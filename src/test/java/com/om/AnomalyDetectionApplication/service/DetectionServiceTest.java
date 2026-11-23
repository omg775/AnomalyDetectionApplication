package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock
    private AnomalyAlertRepository anomalyAlertRepository;

    @Mock
    private TrafficRecordRepository trafficRecordRepository;

    private DetectionService detectionService;

    private long nextId = 1;

    @BeforeEach
    void setUp() {
        detectionService = new DetectionService(anomalyAlertRepository, trafficRecordRepository,
                TransactionOperations.withoutTransaction());
        ReflectionTestUtils.setField(detectionService, "highTrafficVolumeBytes", 200000L);
        ReflectionTestUtils.setField(detectionService, "criticalTrafficVolumeBytes", 500000L);
        ReflectionTestUtils.setField(detectionService, "suspiciousPorts", List.of(21, 23, 3389));
        ReflectionTestUtils.setField(detectionService, "batchSize", 1000);
    }

    private TrafficRecord record(Long bytesTransferred, Integer port) {
        TrafficRecord record = new TrafficRecord();
        ReflectionTestUtils.setField(record, "id", nextId++);
        record.setSourceIp("1.2.3.4");
        record.setDestinationIp("10.0.0.1");
        record.setProtocol("TCP");
        record.setBytesTransferred(bytesTransferred);
        record.setDestinationPort(port);
        record.setTimestamp(LocalDateTime.of(2026, 1, 1, 12, 0));
        return record;
    }

    @SuppressWarnings("unchecked")
    private List<AnomalyAlert> runAndCaptureSavedAlerts(TrafficRecord... records) {
        when(trafficRecordRepository.findByProcessedFalseOrderByIdAsc(any(Limit.class))).thenReturn(List.of(records));

        detectionService.detectAnomalies();

        ArgumentCaptor<List<AnomalyAlert>> captor = ArgumentCaptor.forClass(List.class);
        verify(anomalyAlertRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void bytesAboveCriticalThreshold_createsHighSeverityAlert() {
        List<AnomalyAlert> saved = runAndCaptureSavedAlerts(record(600000L, 80));

        assertThat(saved).singleElement().satisfies(alert -> {
            assertThat(alert.getAnomalyType()).isEqualTo("HIGH_TRAFFIC_VOLUME");
            assertThat(alert.getSeverity()).isEqualTo("HIGH");
        });
    }

    @Test
    void bytesAboveHighButBelowCriticalThreshold_createsMediumSeverityAlert() {
        List<AnomalyAlert> saved = runAndCaptureSavedAlerts(record(300000L, 80));

        assertThat(saved).singleElement().satisfies(alert -> {
            assertThat(alert.getAnomalyType()).isEqualTo("HIGH_TRAFFIC_VOLUME");
            assertThat(alert.getSeverity()).isEqualTo("MEDIUM");
        });
    }

    @Test
    void bytesExactlyAtHighThreshold_isNotAnAnomaly() {
        assertThat(runAndCaptureSavedAlerts(record(200000L, 80))).isEmpty();
    }

    @Test
    void suspiciousPort_createsMediumSeverityAlert() {
        List<AnomalyAlert> saved = runAndCaptureSavedAlerts(record(1000L, 23));

        assertThat(saved).singleElement().satisfies(alert -> {
            assertThat(alert.getAnomalyType()).isEqualTo("SUSPICIOUS_PORT_ACCESS");
            assertThat(alert.getSeverity()).isEqualTo("MEDIUM");
        });
    }

    @Test
    void normalTraffic_noAlertCreated() {
        assertThat(runAndCaptureSavedAlerts(record(1000L, 443))).isEmpty();
    }

    @Test
    void highTrafficVolumeTakesPrecedenceOverSuspiciousPort() {
        List<AnomalyAlert> saved = runAndCaptureSavedAlerts(record(600000L, 23));

        assertThat(saved).singleElement()
                .extracting(AnomalyAlert::getAnomalyType).isEqualTo("HIGH_TRAFFIC_VOLUME");
    }

    @Test
    void everyScannedRecordIsMarkedProcessed_includingNormalOnes() {
        TrafficRecord anomalous = record(600000L, 80);
        TrafficRecord normal = record(1000L, 443);

        runAndCaptureSavedAlerts(anomalous, normal);

        verify(trafficRecordRepository).markProcessed(List.of(anomalous.getId(), normal.getId()));
    }

    @Test
    void fullBatch_triggersAnotherBatchUntilNothingIsLeft() {
        ReflectionTestUtils.setField(detectionService, "batchSize", 2);
        when(trafficRecordRepository.findByProcessedFalseOrderByIdAsc(any(Limit.class)))
                .thenReturn(List.of(record(600000L, 80), record(1000L, 443)))
                .thenReturn(List.of(record(1000L, 23)));

        DetectionService.DetectionRunSummary summary = detectionService.detectAnomalies();

        assertThat(summary.recordsScanned()).isEqualTo(3);
        assertThat(summary.alertsCreated()).isEqualTo(2);
        verify(anomalyAlertRepository, times(2)).saveAll(anyList());
        verify(trafficRecordRepository, times(2)).markProcessed(anyList());
    }

    @Test
    void nothingUnprocessed_writesNothing() {
        when(trafficRecordRepository.findByProcessedFalseOrderByIdAsc(any(Limit.class))).thenReturn(List.of());

        DetectionService.DetectionRunSummary summary = detectionService.detectAnomalies();

        assertThat(summary.recordsScanned()).isZero();
        verify(anomalyAlertRepository, never()).saveAll(anyList());
        verify(trafficRecordRepository, never()).markProcessed(anyList());
    }
}
