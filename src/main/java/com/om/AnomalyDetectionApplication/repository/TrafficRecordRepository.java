package com.om.AnomalyDetectionApplication.repository;

import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, Long> {

    /**
     * Returns the oldest unprocessed records by id and locks them until the surrounding
     * transaction ends, so two concurrent detection runs never evaluate the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TrafficRecord> findByProcessedFalseOrderByIdAsc(Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TrafficRecord t SET t.processed = true WHERE t.id IN :ids")
    int markProcessed(@Param("ids") Collection<Long> ids);

    long countByProcessedFalse();

    @Query("SELECT t.rowHash FROM TrafficRecord t WHERE t.rowHash IN :hashes")
    Set<String> findExistingRowHashes(@Param("hashes") Collection<String> hashes);
}
