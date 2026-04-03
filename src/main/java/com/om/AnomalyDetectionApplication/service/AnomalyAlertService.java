package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.AnomalySummaryResponse;
import com.om.AnomalyDetectionApplication.dto.TopSourceDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnomalyAlertService {

    private final AnomalyAlertRepository anomalyAlertRepository;

    public AnomalyAlertService(AnomalyAlertRepository anomalyAlertRepository) {
        this.anomalyAlertRepository = anomalyAlertRepository;
    }

    public AnomalySummaryResponse getSummary() {
        long total = anomalyAlertRepository.count();

        long high = 0;
        long medium = 0;
        long low = 0;

        List<Object[]> severityResults = anomalyAlertRepository.countBySeverity();
        for (Object[] row : severityResults) {
            String severity = row[0] != null ? row[0].toString() : "";
            long count = ((Number) row[1]).longValue();

            if ("HIGH".equalsIgnoreCase(severity)) {
                high = count;
            } else if ("MEDIUM".equalsIgnoreCase(severity)) {
                medium = count;
            } else if ("LOW".equalsIgnoreCase(severity)) {
                low = count;
            }
        }

        Map<String, Long> byType = new HashMap<>();
        List<Object[]> typeResults = anomalyAlertRepository.countByType();
        for (Object[] row : typeResults) {
            String anomalyType = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = ((Number) row[1]).longValue();
            byType.put(anomalyType, count);
        }

        return new AnomalySummaryResponse(total, high, medium, low, byType);
    }

    public Page<AnomalyAlert> getAlerts(int page, int size, String severity, String anomalyType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        boolean hasSeverity = severity != null && !severity.isBlank();
        boolean hasAnomalyType = anomalyType != null && !anomalyType.isBlank();

        if (hasSeverity && hasAnomalyType) {
            return anomalyAlertRepository.findBySeverityAndAnomalyType(severity, anomalyType, pageable);
        } else if (hasSeverity) {
            return anomalyAlertRepository.findBySeverity(severity, pageable);
        } else if (hasAnomalyType) {
            return anomalyAlertRepository.findByAnomalyType(anomalyType, pageable);
        } else {
            return anomalyAlertRepository.findAll(pageable);
        }
    }

    public List<TopSourceDTO> getTopSources() {
        List<Object[]> results = anomalyAlertRepository.findTopSuspiciousIps();

        return results.stream()
                .map(r -> new TopSourceDTO(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        (String) r[2]
                ))
                .toList();
    }
}