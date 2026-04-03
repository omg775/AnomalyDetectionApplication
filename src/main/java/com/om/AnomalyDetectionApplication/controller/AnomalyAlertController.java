package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.AnomalySummaryResponse;
import com.om.AnomalyDetectionApplication.service.AnomalyAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anomalies")
@CrossOrigin(origins = "*")
public class AnomalyAlertController {

    private final AnomalyAlertService anomalyAlertService;

    public AnomalyAlertController(AnomalyAlertService anomalyAlertService) {
        this.anomalyAlertService = anomalyAlertService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnomalySummaryResponse> getSummary() {
        return ResponseEntity.ok(anomalyAlertService.getSummary());
    }
}