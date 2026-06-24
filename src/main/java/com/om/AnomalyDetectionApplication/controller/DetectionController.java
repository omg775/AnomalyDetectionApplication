package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.service.DetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
@CrossOrigin(origins = "*")
public class DetectionController {

    private final DetectionService detectionService;

    @Autowired
    public DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping("/detect")
    public ResponseEntity<Map<String, String>> detectAnomalies() {
        detectionService.detectAnomalies();
        return ResponseEntity.ok(Map.of("message", "Heuristic anomaly detection completed successfully"));
    }
}
