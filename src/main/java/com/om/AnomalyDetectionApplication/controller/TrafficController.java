package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.service.CsvImportService;
import com.om.AnomalyDetectionApplication.service.DetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final CsvImportService csvImportService;
    private final DetectionService detectionService;

    public TrafficController(CsvImportService csvImportService, DetectionService detectionService) {
        this.csvImportService = csvImportService;
        this.detectionService = detectionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadCsv(@RequestParam("file") MultipartFile file) {
        List<TrafficRecord> savedRecords = csvImportService.importCsv(file);

        return ResponseEntity.ok(Map.of(
                "message", "CSV uploaded successfully",
                "recordsImported", savedRecords.size()
        ));
    }

    @PostMapping("/detect")
    public ResponseEntity<List<AnomalyAlert>> detectAnomalies() {
        return ResponseEntity.ok(detectionService.detectAnomalies());
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AnomalyAlert>> getAlerts() {
        return ResponseEntity.ok(detectionService.getAllAlerts());
    }
}