package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs detection against the real (H2) schema to prove the processed marker makes detection
 * independent of upload order and idempotent across runs.
 */
@SpringBootTest
class DetectionIntegrationTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 3, 10, 12, 0);

    @Autowired
    private DetectionService detectionService;

    @Autowired
    private TrafficRecordRepository trafficRecordRepository;

    @Autowired
    private AnomalyAlertRepository anomalyAlertRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        anomalyAlertRepository.deleteAllInBatch();
        trafficRecordRepository.deleteAllInBatch();
        ReflectionTestUtils.setField(detectionService, "batchSize", 1000);
    }

    private TrafficRecord save(String sourceIp, LocalDateTime timestamp, long bytes) {
        TrafficRecord record = new TrafficRecord();
        record.setSourceIp(sourceIp);
        record.setDestinationIp("10.0.0.1");
        record.setDestinationPort(443);
        record.setProtocol("TCP");
        record.setBytesTransferred(bytes);
        record.setTimestamp(timestamp);
        return trafficRecordRepository.save(record);
    }

    @Test
    void outOfOrderUpload_olderRecordUploadedLaterIsStillDetected() {
        save("1.1.1.1", NOON, 600_000);
        detectionService.detectAnomalies();

        save("2.2.2.2", NOON.minusDays(3), 600_000);
        DetectionService.DetectionRunSummary secondRun = detectionService.detectAnomalies();

        assertThat(secondRun.alertsCreated()).isEqualTo(1);
        assertThat(anomalyAlertRepository.findAll())
                .extracting(AnomalyAlert::getSourceIp)
                .containsExactlyInAnyOrder("1.1.1.1", "2.2.2.2");
    }

    @Test
    void repeatedRun_createsNoDuplicateAlerts() {
        save("1.1.1.1", NOON, 600_000);
        save("1.1.1.2", NOON.plusMinutes(1), 300_000);
        save("1.1.1.3", NOON.plusMinutes(2), 100);

        DetectionService.DetectionRunSummary first = detectionService.detectAnomalies();
        DetectionService.DetectionRunSummary second = detectionService.detectAnomalies();

        assertThat(first.alertsCreated()).isEqualTo(2);
        assertThat(second.recordsScanned()).isZero();
        assertThat(second.alertsCreated()).isZero();
        assertThat(anomalyAlertRepository.count()).isEqualTo(2);
        assertThat(trafficRecordRepository.countByProcessedFalse()).isZero();
    }

    @Test
    void equalTimestamps_recordUploadedAfterARunWithTheSameTimestampIsDetected() {
        save("1.1.1.1", NOON, 600_000);
        detectionService.detectAnomalies();

        save("3.3.3.3", NOON, 600_000);
        detectionService.detectAnomalies();

        assertThat(anomalyAlertRepository.findAll())
                .extracting(AnomalyAlert::getSourceIp)
                .containsExactlyInAnyOrder("1.1.1.1", "3.3.3.3");
    }

    @Test
    void manyBatches_everyRecordIsEvaluatedExactlyOnce() {
        ReflectionTestUtils.setField(detectionService, "batchSize", 7);
        for (int i = 0; i < 30; i++) {
            save("10.0.0." + i, NOON.minusMinutes(i), i % 2 == 0 ? 600_000 : 100);
        }

        DetectionService.DetectionRunSummary summary = detectionService.detectAnomalies();

        assertThat(summary.recordsScanned()).isEqualTo(30);
        assertThat(summary.alertsCreated()).isEqualTo(15);
        assertThat(anomalyAlertRepository.count()).isEqualTo(15);
        assertThat(trafficRecordRepository.countByProcessedFalse()).isZero();
    }

    @Test
    void alertsKeepTheTimestampOfTheirTrafficRecord() {
        LocalDateTime backfilled = NOON.minusYears(1);
        save("4.4.4.4", backfilled, 600_000);

        detectionService.detectAnomalies();

        List<AnomalyAlert> alerts = anomalyAlertRepository.findAll();
        assertThat(alerts).singleElement().extracting(AnomalyAlert::getTimestamp).isEqualTo(backfilled);
    }
}
