package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {

    @Query("SELECT a.severity, COUNT(a) FROM AnomalyAlert a GROUP BY a.severity")
    List<Object[]> countBySeverity();

    @Query("SELECT a.anomalyType, COUNT(a) FROM AnomalyAlert a GROUP BY a.anomalyType")
    List<Object[]> countByType();

    Page<AnomalyAlert> findBySeverity(String severity, Pageable pageable);

    Page<AnomalyAlert> findByAnomalyType(String anomalyType, Pageable pageable);

    Page<AnomalyAlert> findBySeverityAndAnomalyType(String severity, String anomalyType, Pageable pageable);

    @Query("""
        SELECT a.sourceIp, COUNT(a), MAX(a.severity)
        FROM AnomalyAlert a
        GROUP BY a.sourceIp
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> findTopSuspiciousIps();

    List<AnomalyAlert> findAllByOrderByTimestampAsc();

    // NEW: time-filtered alert queries
    Page<AnomalyAlert> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<AnomalyAlert> findBySeverityAndTimestampBetween(String severity, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<AnomalyAlert> findByAnomalyTypeAndTimestampBetween(String anomalyType, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<AnomalyAlert> findBySeverityAndAnomalyTypeAndTimestampBetween(
            String severity,
            String anomalyType,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // NEW: time-filtered top sources
    @Query("""
        SELECT a.sourceIp, COUNT(a), MAX(a.severity)
        FROM AnomalyAlert a
        WHERE a.timestamp BETWEEN :start AND :end
        GROUP BY a.sourceIp
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> findTopSuspiciousIpsBetween(LocalDateTime start, LocalDateTime end);

    // NEW: time-filtered trend base data
    List<AnomalyAlert> findByTimestampBetweenOrderByTimestampAsc(LocalDateTime start, LocalDateTime end);

    /** Keyset paging: the next {@code limit} alerts after {@code id}, in id order. */
    List<AnomalyAlert> findByIdGreaterThanOrderByIdAsc(Long id, Limit limit);

    /** Keyset paging over alerts the ML layer has not scored yet. */
    List<AnomalyAlert> findByMlScoredAtIsNullAndIdGreaterThanOrderByIdAsc(Long id, Limit limit);
}