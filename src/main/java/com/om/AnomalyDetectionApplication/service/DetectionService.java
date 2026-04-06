package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DetectionService {

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final TrafficRecordRepository trafficRecordRepository;

    public DetectionService(AnomalyAlertRepository anomalyAlertRepository,
                            TrafficRecordRepository trafficRecordRepository) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.trafficRecordRepository = trafficRecordRepository;
    }

    public void detectAnomalies() {
        List<TrafficRecord> records = trafficRecordRepository.findAll();

        for (TrafficRecord record : records) {
            String anomalyType = null;
            String severity = null;

            if (record.getBytesTransferred() != null && record.getBytesTransferred() > 200000) {
                anomalyType = "HIGH_TRAFFIC_VOLUME";
                severity = record.getBytesTransferred() > 500000 ? "HIGH" : "MEDIUM";
            } else if (record.getDestinationPort() != null &&
                    (record.getDestinationPort() == 21 ||
                            record.getDestinationPort() == 23 ||
                            record.getDestinationPort() == 3389)) {
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
    }

    public List<MLDataDTO> getMLData() {
        List<AnomalyAlert> alerts = anomalyAlertRepository.findAll();

        return alerts.stream()
                .map(alert -> {
                    int hourOfDay = alert.getTimestamp().getHour();
                    int dayOfWeek = alert.getTimestamp().getDayOfWeek().getValue();

                    int severity = mapSeverity(alert.getSeverity());
                    int anomalyType = mapAnomalyType(alert.getAnomalyType());


                    int sourceIpHash = Math.abs(alert.getSourceIp().hashCode());

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