package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, Long> {
}