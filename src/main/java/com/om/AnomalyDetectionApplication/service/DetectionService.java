package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
}