package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.CsvImportResult;
import com.om.AnomalyDetectionApplication.dto.CsvImportResult.SkippedRow;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.exception.CsvImportException;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import com.om.AnomalyDetectionApplication.util.IpAddresses;
import com.om.AnomalyDetectionApplication.util.TrafficRowHash;
import jakarta.persistence.EntityManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    static final List<String> REQUIRED_COLUMNS = List.of(
            "timestamp", "source_ip", "destination_ip", "protocol", "destination_port", "bytes_transferred");
    private static final int MAX_PORT = 65_535;

    private final TrafficRecordRepository trafficRecordRepository;
    private final EntityManager entityManager;
    private final int maxRows;
    private final int batchSize;
    private final Set<String> allowedProtocols;

    public CsvImportService(TrafficRecordRepository trafficRecordRepository,
                            EntityManager entityManager,
                            @Value("${csv.import.max-rows:100000}") int maxRows,
                            @Value("${csv.import.batch-size:500}") int batchSize,
                            @Value("${csv.import.allowed-protocols:TCP,UDP,ICMP}") List<String> allowedProtocols) {
        this.trafficRecordRepository = trafficRecordRepository;
        this.entityManager = entityManager;
        this.maxRows = maxRows;
        this.batchSize = batchSize;
        this.allowedProtocols = allowedProtocols.stream()
                .map(p -> p.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Imports traffic records from a CSV file with the columns {@link #REQUIRED_COLUMNS}.
     * Invalid rows and exact duplicates (within the file or of an existing record) are skipped and
     * reported; all valid rows are saved in batches within one transaction, so either all of them
     * are stored or none.
     *
     * @param file the uploaded CSV file
     * @return how many rows were imported and which rows were skipped and why
     * @throws CsvImportException when the file is empty, unreadable, lacks a required column, or
     *                            has more than {@code csv.import.max-rows} data rows
     */
    @Transactional
    public CsvImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CsvImportException("Uploaded CSV file is empty", null);
        }

        List<SkippedRow> invalidRows = new ArrayList<>();
        List<SkippedRow> duplicateRows = new ArrayList<>();
        Map<String, Long> firstRowByHash = new HashMap<>();
        Map<String, PendingRow> batch = new LinkedHashMap<>();
        int imported = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            requireColumns(csvParser.getHeaderNames());

            for (CSVRecord csvRecord : csvParser) {
                long row = csvRecord.getRecordNumber();
                if (row > maxRows) {
                    throw new CsvImportException("CSV file has more than the maximum of " + maxRows + " data rows", null);
                }

                List<String> problems = new ArrayList<>();
                TrafficRecord record = parse(csvRecord, problems);
                if (!problems.isEmpty()) {
                    invalidRows.add(new SkippedRow(row, String.join("; ", problems)));
                    continue;
                }

                String hash = TrafficRowHash.of(record);
                Long firstRow = firstRowByHash.putIfAbsent(hash, row);
                if (firstRow != null) {
                    duplicateRows.add(new SkippedRow(row, "duplicate of row " + firstRow));
                    continue;
                }
                record.setRowHash(hash);
                batch.put(hash, new PendingRow(row, record));

                if (batch.size() >= batchSize) {
                    imported += saveBatch(batch, duplicateRows);
                }
            }
            imported += saveBatch(batch, duplicateRows);
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            throw new CsvImportException("Failed to read CSV file: " + e.getMessage(), e);
        }

        log.info("CSV import complete: {} rows imported, {} invalid, {} duplicates",
                imported, invalidRows.size(), duplicateRows.size());
        return new CsvImportResult(imported, invalidRows, duplicateRows);
    }

    private void requireColumns(List<String> headerNames) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !headerNames.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new CsvImportException("CSV file is missing required columns: " + String.join(", ", missing), null);
        }
    }

    /** Saves the pending rows that are not already stored and clears the batch. */
    private int saveBatch(Map<String, PendingRow> batch, List<SkippedRow> duplicateRows) {
        if (batch.isEmpty()) {
            return 0;
        }
        Set<String> existing = trafficRecordRepository.findExistingRowHashes(batch.keySet());
        List<TrafficRecord> toSave = new ArrayList<>(batch.size());
        batch.forEach((hash, pending) -> {
            if (existing.contains(hash)) {
                duplicateRows.add(new SkippedRow(pending.row(), "duplicate of an existing record"));
            } else {
                toSave.add(pending.record());
            }
        });

        try {
            trafficRecordRepository.saveAll(toSave);
            trafficRecordRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CsvImportException(
                    "Some rows were stored by another upload at the same time. Nothing was imported; retry the upload.", e);
        }
        entityManager.clear();
        batch.clear();
        return toSave.size();
    }

    private TrafficRecord parse(CSVRecord csvRecord, List<String> problems) {
        TrafficRecord record = new TrafficRecord();

        String timestamp = value(csvRecord, "timestamp");
        try {
            record.setTimestamp(LocalDateTime.parse(timestamp).truncatedTo(ChronoUnit.MICROS));
        } catch (DateTimeParseException e) {
            problems.add("timestamp '" + timestamp + "' is not an ISO-8601 date-time such as 2026-01-31T13:45:00");
        }

        String sourceIp = value(csvRecord, "source_ip");
        if (IpAddresses.isValid(sourceIp)) {
            record.setSourceIp(sourceIp);
        } else {
            problems.add("source_ip '" + sourceIp + "' is not a valid IPv4 or IPv6 address");
        }

        String destinationIp = value(csvRecord, "destination_ip");
        if (destinationIp.isEmpty()) {
            record.setDestinationIp(null);
        } else if (IpAddresses.isValid(destinationIp)) {
            record.setDestinationIp(destinationIp);
        } else {
            problems.add("destination_ip '" + destinationIp + "' is not a valid IPv4 or IPv6 address");
        }

        String protocol = value(csvRecord, "protocol");
        if (allowedProtocols.contains(protocol.toUpperCase(Locale.ROOT))) {
            record.setProtocol(protocol);
        } else {
            problems.add("protocol '" + protocol + "' is not one of " + String.join(", ", allowedProtocols.stream().sorted().toList()));
        }

        String port = value(csvRecord, "destination_port");
        try {
            int parsedPort = Integer.parseInt(port);
            if (parsedPort < 0 || parsedPort > MAX_PORT) {
                throw new NumberFormatException();
            }
            record.setDestinationPort(parsedPort);
        } catch (NumberFormatException e) {
            problems.add("destination_port '" + port + "' is not an integer between 0 and " + MAX_PORT);
        }

        String bytes = value(csvRecord, "bytes_transferred");
        try {
            long parsedBytes = Long.parseLong(bytes);
            if (parsedBytes < 0) {
                throw new NumberFormatException();
            }
            record.setBytesTransferred(parsedBytes);
        } catch (NumberFormatException e) {
            problems.add("bytes_transferred '" + bytes + "' is not a non-negative integer");
        }

        return record;
    }

    private static String value(CSVRecord csvRecord, String column) {
        return csvRecord.isSet(column) ? csvRecord.get(column).trim() : "";
    }

    private record PendingRow(long row, TrafficRecord record) {
    }
}
