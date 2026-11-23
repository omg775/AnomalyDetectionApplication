package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.dto.AnomalySummaryResponse;
import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.TopSourceDTO;
import com.om.AnomalyDetectionApplication.dto.TrendPointDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.service.AnomalyAlertService;
import com.om.AnomalyDetectionApplication.service.MlFeatureService;
import com.om.AnomalyDetectionApplication.service.MlServiceClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anomalies")
@Validated
public class AnomalyAlertController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AnomalyAlertService anomalyAlertService;
    private final MlFeatureService mlFeatureService;
    private final MlServiceClient mlServiceClient;

    public AnomalyAlertController(AnomalyAlertService anomalyAlertService, MlFeatureService mlFeatureService,
                                  MlServiceClient mlServiceClient) {
        this.anomalyAlertService = anomalyAlertService;
        this.mlFeatureService = mlFeatureService;
        this.mlServiceClient = mlServiceClient;
    }

    @GetMapping
    public ResponseEntity<Page<AnomalyAlert>> getAlerts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(MAX_PAGE_SIZE) int size,
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
        return ResponseEntity.ok(mlFeatureService.loadAllFeatures());
    }

    @GetMapping("/ml-health")
    public ResponseEntity<Map<String, String>> mlHealth() {
        boolean healthy = mlServiceClient.isHealthy();
        Map<String, String> body = Map.of("status", healthy ? "UP" : "DOWN");
        return healthy
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
