package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {

    Page<AnomalyAlert> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AnomalyAlert> findBySeverityOrderByTimestampDesc(String severity, Pageable pageable);

    Page<AnomalyAlert> findByAnomalyTypeOrderByTimestampDesc(String anomalyType, Pageable pageable);

    Page<AnomalyAlert> findBySeverityAndAnomalyTypeOrderByTimestampDesc(
            String severity,
            String anomalyType,
            Pageable pageable
    );
}