package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, Long> {
    Page<TrafficRecord> findByTimestampGreaterThan(LocalDateTime timestamp, Pageable pageable);
}