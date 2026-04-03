//package com.om.AnomalyDetectionApplication.service;
//
//import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
//import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
//import org.apache.commons.csv.CSVFormat;
//import org.apache.commons.csv.CSVParser;
//import org.apache.commons.csv.CSVRecord;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//public class CsvImportService {
//
//    private final TrafficRecordRepository trafficRecordRepository;
//
//    public CsvImportService(TrafficRecordRepository trafficRecordRepository) {
//        this.trafficRecordRepository = trafficRecordRepository;
//    }
//
//    public List<TrafficRecord> importCsv(MultipartFile file) {
//        List<TrafficRecord> records = new ArrayList<>();
//
//        try (
//                BufferedReader reader = new BufferedReader(
//                        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
//                CSVParser csvParser = new CSVParser(reader,
//                        CSVFormat.DEFAULT.builder()
//                                .setHeader()
//                                .setSkipHeaderRecord(true)
//                                .build())
//        ) {
//            for (CSVRecord csvRecord : csvParser) {
//                TrafficRecord record = new TrafficRecord(
//                        LocalDateTime.parse(csvRecord.get("timestamp")),
//                        csvRecord.get("source_ip"),
//                        csvRecord.get("destination_ip"),
//                        csvRecord.get("protocol"),
//                        Integer.parseInt(csvRecord.get("destination_port")),
//                        Long.parseLong(csvRecord.get("bytes_transferred"))
//                );
//
//                records.add(record);
//            }
//
//            return trafficRecordRepository.saveAll(records);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to import CSV file: " + e.getMessage(), e);
//        }
//    }
//}