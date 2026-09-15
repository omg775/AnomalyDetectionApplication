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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock
    private AnomalyAlertRepository anomalyAlertRepository;

    @Mock
    private TrafficRecordRepository trafficRecordRepository;

    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        detectionService = new DetectionService(anomalyAlertRepository, trafficRecordRepository);
        ReflectionTestUtils.setField(detectionService, "highTrafficVolumeBytes", 200000L);
        ReflectionTestUtils.setField(detectionService, "criticalTrafficVolumeBytes", 500000L);
        ReflectionTestUtils.setField(detectionService, "suspiciousPorts", List.of(21, 23, 3389));
    }

    private TrafficRecord record(String sourceIp, Long bytesTransferred, Integer port) {
        TrafficRecord record = new TrafficRecord();
        record.setSourceIp(sourceIp);
        record.setDestinationIp("10.0.0.1");
        record.setProtocol("TCP");
        record.setBytesTransferred(bytesTransferred);
        record.setDestinationPort(port);
        record.setTimestamp(LocalDateTime.of(2026, 1, 1, 12, 0));
        return record;
    }

    private void stubTrafficPage(TrafficRecord... records) {
        Page<TrafficRecord> page = new PageImpl<>(List.of(records));
        when(trafficRecordRepository.findAll(any(Pageable.class))).thenReturn(page);
    }

    @Test
    void bytesAboveCriticalThreshold_createsHighSeverityAlert() {
        stubTrafficPage(record("1.2.3.4", 600000L, 80));

        detectionService.detectAnomalies();

        ArgumentCaptor<AnomalyAlert> captor = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(anomalyAlertRepository).save(captor.capture());
        AnomalyAlert saved = captor.getValue();
        assertThat(saved.getAnomalyType()).isEqualTo("HIGH_TRAFFIC_VOLUME");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
    }

    @Test
    void bytesAboveHighButBelowCriticalThreshold_createsMediumSeverityAlert() {
        stubTrafficPage(record("1.2.3.4", 300000L, 80));

        detectionService.detectAnomalies();

        ArgumentCaptor<AnomalyAlert> captor = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(anomalyAlertRepository).save(captor.capture());
        AnomalyAlert saved = captor.getValue();
        assertThat(saved.getAnomalyType()).isEqualTo("HIGH_TRAFFIC_VOLUME");
        assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void bytesExactlyAtHighThreshold_isNotAnAnomaly() {
        stubTrafficPage(record("1.2.3.4", 200000L, 80));

        detectionService.detectAnomalies();

        verify(anomalyAlertRepository, never()).save(any());
    }

    @Test
    void suspiciousPort_createsMediumSeverityAlert() {
        stubTrafficPage(record("1.2.3.4", 1000L, 23));

        detectionService.detectAnomalies();

        ArgumentCaptor<AnomalyAlert> captor = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(anomalyAlertRepository).save(captor.capture());
        AnomalyAlert saved = captor.getValue();
        assertThat(saved.getAnomalyType()).isEqualTo("SUSPICIOUS_PORT_ACCESS");
        assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void normalTraffic_noAlertCreated() {
        stubTrafficPage(record("1.2.3.4", 1000L, 443));

        detectionService.detectAnomalies();

        verify(anomalyAlertRepository, never()).save(any());
    }

    @Test
    void highTrafficVolumeTakesPrecedenceOverSuspiciousPort() {
        stubTrafficPage(record("1.2.3.4", 600000L, 23));

        detectionService.detectAnomalies();

        ArgumentCaptor<AnomalyAlert> captor = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(anomalyAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getAnomalyType()).isEqualTo("HIGH_TRAFFIC_VOLUME");
    }

    @Test
    void incrementalRun_usesMaxTimestampWhenAlertsAlreadyExist() {
        LocalDateTime maxTimestamp = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(anomalyAlertRepository.findMaxTimestamp()).thenReturn(maxTimestamp);
        Page<TrafficRecord> page = new PageImpl<>(List.of());
        when(trafficRecordRepository.findByTimestampGreaterThan(eq(maxTimestamp), any(Pageable.class)))
                .thenReturn(page);

        detectionService.detectAnomalies();

        verify(trafficRecordRepository).findByTimestampGreaterThan(eq(maxTimestamp), any(Pageable.class));
        verify(trafficRecordRepository, never()).findAll(any(Pageable.class));
    }
}
