package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AnomalyAlertService {

    private final AnomalyAlertRepository repository;

    public AnomalyAlertService(AnomalyAlertRepository repository) {
        this.repository = repository;
    }

    public Page<AnomalyAlert> getAlerts(int page, int size, String severity, String anomalyType) {
        Pageable pageable = PageRequest.of(page, size);

        boolean hasSeverity = severity != null && !severity.isBlank();
        boolean hasAnomalyType = anomalyType != null && !anomalyType.isBlank();

        if (hasSeverity && hasAnomalyType) {
            return repository.findBySeverityAndAnomalyTypeOrderByTimestampDesc(
                    severity, anomalyType, pageable
            );
        }

        if (hasSeverity) {
            return repository.findBySeverityOrderByTimestampDesc(severity, pageable);
        }

        if (hasAnomalyType) {
            return repository.findByAnomalyTypeOrderByTimestampDesc(anomalyType, pageable);
        }

        return repository.findAllByOrderByTimestampDesc(pageable);
    }
}