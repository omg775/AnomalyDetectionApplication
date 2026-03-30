package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {
}