package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DetectionService {

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final TrafficRecordRepository trafficRecordRepository;

    @Value("${anomaly.rule.bytes-transferred.high:200000}")
    private Long highTrafficVolumeBytes;

    @Value("${anomaly.rule.bytes-transferred.critical:500000}")
    private Long criticalTrafficVolumeBytes;

    @Value("${anomaly.rule.suspicious-ports:21,23,3389}")
    private List<Integer> suspiciousPorts;

    public DetectionService(AnomalyAlertRepository anomalyAlertRepository,
                            TrafficRecordRepository trafficRecordRepository) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.trafficRecordRepository = trafficRecordRepository;
    }

    public void detectAnomalies() {
        LocalDateTime maxTimestamp = anomalyAlertRepository.findMaxTimestamp();
        
        int pageSize = 1000;
        int pageNumber = 0;
        Page<TrafficRecord> page;
        
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            
            if (maxTimestamp != null) {
                page = trafficRecordRepository.findByTimestampGreaterThan(maxTimestamp, pageable);
            } else {
                page = trafficRecordRepository.findAll(pageable);
            }

            for (TrafficRecord record : page.getContent()) {
                String anomalyType = null;
                String severity = null;

                if (record.getBytesTransferred() != null && record.getBytesTransferred() > highTrafficVolumeBytes) {
                    anomalyType = "HIGH_TRAFFIC_VOLUME";
                    severity = record.getBytesTransferred() > criticalTrafficVolumeBytes ? "HIGH" : "MEDIUM";
                } else if (record.getDestinationPort() != null &&
                        suspiciousPorts.contains(record.getDestinationPort())) {
                    anomalyType = "SUSPICIOUS_PORT_ACCESS";
                    severity = "MEDIUM";
                }

                if (anomalyType != null) {
                    AnomalyAlert alert = new AnomalyAlert();
                    alert.setSourceIp(record.getSourceIp());
                    alert.setAnomalyType(anomalyType);
                    alert.setSeverity(severity);
                    alert.setTimestamp(record.getTimestamp());

                    anomalyAlertRepository.save(alert);
                }
            }
            pageNumber++;
        } while (page.hasNext());
    }

    public List<MLDataDTO> getMLData() {
        List<AnomalyAlert> alerts = anomalyAlertRepository.findAll();

        return alerts.stream()
                .map(alert -> {
                    int hourOfDay = alert.getTimestamp().getHour();
                    int dayOfWeek = alert.getTimestamp().getDayOfWeek().getValue();

                    int severity = mapSeverity(alert.getSeverity());
                    int anomalyType = mapAnomalyType(alert.getAnomalyType());

                    int sourceIpHash = hashIpAddress(alert.getSourceIp());

                    return new MLDataDTO(
                            hourOfDay,
                            dayOfWeek,
                            severity,
                            anomalyType,
                            sourceIpHash
                    );
                })
                .collect(Collectors.toList());
    }
    
    private int hashIpAddress(String ipAddress) {
        if (ipAddress == null || !ipAddress.contains(".")) {
            return 0;
        }
        try {
            String[] parts = ipAddress.split("\\.");
            long result = 0;
            for (int i = 0; i < parts.length; i++) {
                result += Long.parseLong(parts[i]) * Math.pow(256, 3 - i);
            }
            return (int) (result % Integer.MAX_VALUE);
        } catch (Exception e) {
            return Math.abs(ipAddress.hashCode());
        }
    }

    private int mapSeverity(String severity) {
        if (severity == null) return 1;

        return switch (severity.toUpperCase()) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 1;
        };
    }

    private int mapAnomalyType(String anomalyType) {
        if (anomalyType == null) return 0;

        return switch (anomalyType.toUpperCase()) {
            case "HIGH_TRAFFIC_VOLUME" -> 1;
            case "SUSPICIOUS_PORT_ACCESS" -> 2;
            default -> 0;
        };
    }
}