package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.CsvImportResult;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.exception.CsvImportException;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final TrafficRecordRepository trafficRecordRepository;

    public CsvImportService(TrafficRecordRepository trafficRecordRepository) {
        this.trafficRecordRepository = trafficRecordRepository;
    }

    public CsvImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CsvImportException("Uploaded CSV file is empty", null);
        }

        List<TrafficRecord> records = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader,
                     CSVFormat.DEFAULT.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .build())
        ) {
            for (CSVRecord csvRecord : csvParser) {
                try {
                    records.add(parseRecord(csvRecord));
                } catch (Exception e) {
                    String error = "Row " + csvRecord.getRecordNumber() + ": " + e.getMessage();
                    errors.add(error);
                    log.warn("Skipping malformed CSV row {}: {}", csvRecord.getRecordNumber(), e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new CsvImportException("Failed to read CSV file: " + e.getMessage(), e);
        }

        List<TrafficRecord> saved = trafficRecordRepository.saveAll(records);
        log.info("CSV import complete: {} rows imported, {} rows skipped", saved.size(), errors.size());

        return new CsvImportResult(saved.size(), errors.size(), errors);
    }

    private TrafficRecord parseRecord(CSVRecord csvRecord) {
        TrafficRecord record = new TrafficRecord();
        record.setTimestamp(LocalDateTime.parse(csvRecord.get("timestamp")));
        record.setSourceIp(csvRecord.get("source_ip"));
        record.setDestinationIp(csvRecord.get("destination_ip"));
        record.setProtocol(csvRecord.get("protocol"));
        record.setDestinationPort(Integer.parseInt(csvRecord.get("destination_port")));
        record.setBytesTransferred(Long.parseLong(csvRecord.get("bytes_transferred")));
        return record;
    }
}
