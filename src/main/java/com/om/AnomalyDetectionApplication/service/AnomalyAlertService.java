package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.AnomalySummaryResponse;
import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.TopSourceDTO;
import com.om.AnomalyDetectionApplication.dto.TrendPointDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    // old version kept so existing controllers don't break
    public Page<AnomalyAlert> getAlerts(int page, int size, String severity, String anomalyType) {
        return getAlerts(page, size, severity, anomalyType, null, null);
    }

    // new version with time filtering
    public Page<AnomalyAlert> getAlerts(
            int page,
            int size,
            String severity,
            String anomalyType,
            LocalDateTime start,
            LocalDateTime end
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        boolean hasSeverity = severity != null && !severity.isBlank();
        boolean hasAnomalyType = anomalyType != null && !anomalyType.isBlank();
        boolean hasTimeRange = start != null && end != null;

        if (hasTimeRange) {
            if (hasSeverity && hasAnomalyType) {
                return anomalyAlertRepository.findBySeverityAndAnomalyTypeAndTimestampBetween(
                        severity, anomalyType, start, end, pageable
                );
            } else if (hasSeverity) {
                return anomalyAlertRepository.findBySeverityAndTimestampBetween(
                        severity, start, end, pageable
                );
            } else if (hasAnomalyType) {
                return anomalyAlertRepository.findByAnomalyTypeAndTimestampBetween(
                        anomalyType, start, end, pageable
                );
            } else {
                return anomalyAlertRepository.findByTimestampBetween(start, end, pageable);
            }
        }

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

    public List<TopSourceDTO> getTopSources(LocalDateTime start, LocalDateTime end) {
        List<Object[]> results;

        if (start != null && end != null) {
            results = anomalyAlertRepository.findTopSuspiciousIpsBetween(start, end);
        } else {
            results = anomalyAlertRepository.findTopSuspiciousIps();
        }

        return results.stream()
                .map(r -> new TopSourceDTO(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        (String) r[2]
                ))
                .toList();
    }

    public List<TrendPointDTO> getTrend() {
        List<AnomalyAlert> alerts = anomalyAlertRepository.findAllByOrderByTimestampAsc();
        return buildTrend(alerts);
    }

    public List<TrendPointDTO> getTrend(LocalDateTime start, LocalDateTime end) {
        List<AnomalyAlert> alerts;

        if (start != null && end != null) {
            alerts = anomalyAlertRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
        } else {
            alerts = anomalyAlertRepository.findAllByOrderByTimestampAsc();
        }

        return buildTrend(alerts);
    }

    private List<TrendPointDTO> buildTrend(List<AnomalyAlert> alerts) {
        Map<LocalDateTime, Long> grouped = new LinkedHashMap<>();

        for (AnomalyAlert alert : alerts) {
            if (alert.getTimestamp() == null) {
                continue;
            }

            LocalDateTime hourBucket = alert.getTimestamp()
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);

            grouped.put(hourBucket, grouped.getOrDefault(hourBucket, 0L) + 1);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        List<TrendPointDTO> trend = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Long> entry : grouped.entrySet()) {
            trend.add(new TrendPointDTO(
                    entry.getKey().format(formatter),
                    entry.getValue()
            ));
        }

        return trend;
    }
    public List<MLDataDTO> getMLData() {
        List<AnomalyAlert> alerts = anomalyAlertRepository.findAll();

        return alerts.stream()
                .filter(a -> a.getTimestamp() != null)
                .map(a -> new MLDataDTO(
                        a.getTimestamp().getHour(),
                        a.getTimestamp().getDayOfWeek().getValue(),
                        mapSeverity(a.getSeverity()),
                        mapAnomalyType(a.getAnomalyType()),
                        mapSourceIp(a.getSourceIp())
                ))
                .toList();
    }

    private int mapSeverity(String severity) {
        if ("HIGH".equalsIgnoreCase(severity)) return 3;
        if ("MEDIUM".equalsIgnoreCase(severity)) return 2;
        return 1;
    }

    private int mapAnomalyType(String anomalyType) {
        if ("HIGH_TRAFFIC_VOLUME".equalsIgnoreCase(anomalyType)) return 1;
        if ("SUSPICIOUS_PROTOCOL".equalsIgnoreCase(anomalyType)) return 2;
        if ("UNUSUAL_PORT_ACCESS".equalsIgnoreCase(anomalyType)) return 3;
        return 0;
    }

    private int mapSourceIp(String sourceIp) {
        if (sourceIp == null) {
            return 0;
        }
        return Math.abs(sourceIp.hashCode());
    }
}