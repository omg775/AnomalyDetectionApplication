package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {

    @Query("SELECT a.severity, COUNT(a) FROM AnomalyAlert a GROUP BY a.severity")
    List<Object[]> countBySeverity();

    @Query("SELECT a.anomalyType, COUNT(a) FROM AnomalyAlert a GROUP BY a.anomalyType")
    List<Object[]> countByType();

    Page<AnomalyAlert> findBySeverity(String severity, Pageable pageable);

    Page<AnomalyAlert> findByAnomalyType(String anomalyType, Pageable pageable);

    Page<AnomalyAlert> findBySeverityAndAnomalyType(String severity, String anomalyType, Pageable pageable);
}