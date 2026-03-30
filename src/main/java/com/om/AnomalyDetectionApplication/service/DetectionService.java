package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DetectionService {

    private final TrafficRecordRepository trafficRecordRepository;
    private final AnomalyAlertRepository anomalyAlertRepository;

    public DetectionService(TrafficRecordRepository trafficRecordRepository,
                            AnomalyAlertRepository anomalyAlertRepository) {
        this.trafficRecordRepository = trafficRecordRepository;
        this.anomalyAlertRepository = anomalyAlertRepository;
    }

    public List<AnomalyAlert> detectAnomalies() {
        List<TrafficRecord> records = trafficRecordRepository.findAll();

        Map<String, List<TrafficRecord>> groupedBySourceIp = records.stream()
                .collect(Collectors.groupingBy(TrafficRecord::getSourceIp));

        List<AnomalyAlert> alerts = new ArrayList<>();

        for (Map.Entry<String, List<TrafficRecord>> entry : groupedBySourceIp.entrySet()) {
            String sourceIp = entry.getKey();
            List<TrafficRecord> trafficList = entry.getValue();

            long highTrafficCount = trafficList.stream()
                    .filter(r -> r.getBytesTransferred() != null && r.getBytesTransferred() > 10000)
                    .count();

            if (highTrafficCount >= 3) {
                alerts.add(new AnomalyAlert(
                        "High Traffic Spike",
                        "HIGH",
                        sourceIp,
                        "Source IP generated multiple unusually large traffic records.",
                        LocalDateTime.now()
                ));
            }

            long distinctPorts = trafficList.stream()
                    .map(TrafficRecord::getDestinationPort)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            if (distinctPorts >= 5) {
                alerts.add(new AnomalyAlert(
                        "Possible Port Scan",
                        "MEDIUM",
                        sourceIp,
                        "Source IP accessed many different destination ports.",
                        LocalDateTime.now()
                ));
            }
        }

        return anomalyAlertRepository.saveAll(alerts);
    }

    public List<AnomalyAlert> getAllAlerts() {
        return anomalyAlertRepository.findAll();
    }
}