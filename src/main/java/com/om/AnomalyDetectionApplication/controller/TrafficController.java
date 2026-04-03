package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.service.AnomalyAlertService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anomalies")
@CrossOrigin(origins = "*")
public class TrafficController {

    private final AnomalyAlertService anomalyAlertService;

    public TrafficController(AnomalyAlertService anomalyAlertService) {
        this.anomalyAlertService = anomalyAlertService;
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