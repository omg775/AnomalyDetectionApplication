package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.AnomalySummaryResponse;
import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.TopSourceDTO;
import com.om.AnomalyDetectionApplication.dto.TrendPointDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.service.AnomalyAlertService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/anomalies")
@CrossOrigin(origins = "*")
public class AnomalyAlertController {

    private final AnomalyAlertService anomalyAlertService;

    public AnomalyAlertController(AnomalyAlertService anomalyAlertService) {
        this.anomalyAlertService = anomalyAlertService;
    }

    @GetMapping
    public ResponseEntity<Page<AnomalyAlert>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String anomalyType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end
    ) {
        return ResponseEntity.ok(
                anomalyAlertService.getAlerts(page, size, severity, anomalyType, start, end)
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<AnomalySummaryResponse> getSummary() {
        return ResponseEntity.ok(anomalyAlertService.getSummary());
    }

    @GetMapping("/top-sources")
    public ResponseEntity<List<TopSourceDTO>> getTopSources(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end
    ) {
        return ResponseEntity.ok(anomalyAlertService.getTopSources(start, end));
    }

    @GetMapping("/trend")
    public ResponseEntity<List<TrendPointDTO>> getTrend(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end
    ) {
        return ResponseEntity.ok(anomalyAlertService.getTrend(start, end));
    }
    @GetMapping("/ml-data")
    public ResponseEntity<List<MLDataDTO>> getMLData() {
        return ResponseEntity.ok(anomalyAlertService.getMLData());
    }
}