package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.CsvImportResult;
import com.om.AnomalyDetectionApplication.service.CsvImportService;
import com.om.AnomalyDetectionApplication.service.DetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
public class DetectionController {

    private final DetectionService detectionService;
    private final CsvImportService csvImportService;

    @Autowired
    public DetectionController(DetectionService detectionService, CsvImportService csvImportService) {
        this.detectionService = detectionService;
        this.csvImportService = csvImportService;
    }

    @PostMapping("/detect")
    public ResponseEntity<Map<String, String>> detectAnomalies() {
        detectionService.detectAnomalies();
        return ResponseEntity.ok(Map.of("message", "Heuristic anomaly detection completed successfully"));
    }

    @PostMapping("/upload")
    public ResponseEntity<CsvImportResult> uploadCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importCsv(file));
    }
}
