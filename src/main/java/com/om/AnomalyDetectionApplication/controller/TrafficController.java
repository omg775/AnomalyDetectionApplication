package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.service.AnomalyAlertService;
import com.om.AnomalyDetectionApplication.service.DetectionService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traffic")
@CrossOrigin
public class TrafficController {

    private final DetectionService detectionService;
    private final AnomalyAlertService anomalyAlertService;

    public TrafficController(DetectionService detectionService,
                             AnomalyAlertService anomalyAlertService) {
        this.detectionService = detectionService;
        this.anomalyAlertService = anomalyAlertService;
    }

    @PostMapping("/detect")
    public ResponseEntity<String> detectAnomalies() {
        detectionService.detectAnomalies();
        return ResponseEntity.ok("Anomaly detection completed successfully.");
    }

    @GetMapping("/alerts")
    public ResponseEntity<Page<AnomalyAlert>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String anomalyType
    ) {
        return ResponseEntity.ok(
                anomalyAlertService.getAlerts(page, size, severity, anomalyType)
        );
    }
}