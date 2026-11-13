package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.entity.TrafficRecord;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import com.om.AnomalyDetectionApplication.util.MLFeatureMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Heuristic detection layer. Evaluates every traffic record exactly once: records are scanned in
 * id order while {@code processed = false}, and each batch is marked processed in the same
 * transaction that stores its alerts. Re-running detection is therefore idempotent, and records
 * uploaded out of timestamp order are still evaluated.
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    static final String HIGH_TRAFFIC_VOLUME = "HIGH_TRAFFIC_VOLUME";
    static final String SUSPICIOUS_PORT_ACCESS = "SUSPICIOUS_PORT_ACCESS";

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final TrafficRecordRepository trafficRecordRepository;
    private final TransactionOperations transactionOperations;

    @Value("${anomaly.rule.bytes-transferred.high:200000}")
    private Long highTrafficVolumeBytes;

    @Value("${anomaly.rule.bytes-transferred.critical:500000}")
    private Long criticalTrafficVolumeBytes;

    @Value("${anomaly.rule.suspicious-ports:21,23,3389}")
    private List<Integer> suspiciousPorts;

    @Value("${detection.batch-size:1000}")
    private int batchSize;

    public DetectionService(AnomalyAlertRepository anomalyAlertRepository,
                            TrafficRecordRepository trafficRecordRepository,
                            TransactionOperations transactionOperations) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.trafficRecordRepository = trafficRecordRepository;
        this.transactionOperations = transactionOperations;
    }

    /**
     * Runs the heuristic rules over every traffic record that has not been processed yet.
     * Each batch of {@code detection.batch-size} records is handled in its own transaction:
     * the records are locked, alerts are saved with a batched insert, and the records are marked
     * processed. A failure rolls back only the current batch, and the next run resumes there.
     *
     * @return how many records were scanned and how many alerts were created in this run
     */
    public DetectionRunSummary detectAnomalies() {
        log.info("Starting heuristic anomaly detection run");

        int recordsScanned = 0;
        int alertsCreated = 0;
        DetectionRunSummary batch;

        do {
            batch = transactionOperations.execute(status -> processNextBatch());
            recordsScanned += batch.recordsScanned();
            alertsCreated += batch.alertsCreated();
        } while (batch.recordsScanned() == batchSize);

        log.info("Detection run finished: {} records scanned, {} alerts created", recordsScanned, alertsCreated);
        return new DetectionRunSummary(recordsScanned, alertsCreated);
    }

    private DetectionRunSummary processNextBatch() {
        List<TrafficRecord> records = trafficRecordRepository.findByProcessedFalseOrderByIdAsc(Limit.of(batchSize));
        if (records.isEmpty()) {
            return new DetectionRunSummary(0, 0);
        }

        List<AnomalyAlert> alerts = new ArrayList<>();
        List<Long> ids = new ArrayList<>(records.size());
        for (TrafficRecord record : records) {
            evaluate(record).ifPresent(alerts::add);
            ids.add(record.getId());
        }

        anomalyAlertRepository.saveAll(alerts);
        trafficRecordRepository.markProcessed(ids);
        return new DetectionRunSummary(records.size(), alerts.size());
    }

    /**
     * Applies the heuristic rules to a single record. A high traffic volume takes precedence over
     * a suspicious destination port.
     *
     * @param record the traffic record to evaluate
     * @return the alert the rules raise for this record, or empty when the record looks normal
     */
    public Optional<AnomalyAlert> evaluate(TrafficRecord record) {
        String anomalyType;
        String severity;

        if (record.getBytesTransferred() != null && record.getBytesTransferred() > highTrafficVolumeBytes) {
            anomalyType = HIGH_TRAFFIC_VOLUME;
            severity = record.getBytesTransferred() > criticalTrafficVolumeBytes ? "HIGH" : "MEDIUM";
        } else if (record.getDestinationPort() != null && suspiciousPorts.contains(record.getDestinationPort())) {
            anomalyType = SUSPICIOUS_PORT_ACCESS;
            severity = "MEDIUM";
        } else {
            return Optional.empty();
        }

        AnomalyAlert alert = new AnomalyAlert();
        alert.setSourceIp(record.getSourceIp());
        alert.setAnomalyType(anomalyType);
        alert.setSeverity(severity);
        alert.setTimestamp(record.getTimestamp());
        return Optional.of(alert);
    }

    public List<MLDataDTO> getMLData() {
        List<AnomalyAlert> alerts = anomalyAlertRepository.findAll();

        return alerts.stream()
                .map(alert -> {
                    int hourOfDay = alert.getTimestamp().getHour();
                    int dayOfWeek = alert.getTimestamp().getDayOfWeek().getValue();

                    int severity = MLFeatureMapper.mapSeverity(alert.getSeverity());
                    int anomalyType = MLFeatureMapper.mapAnomalyType(alert.getAnomalyType());

                    int sourceIpHash = MLFeatureMapper.hashIpAddress(alert.getSourceIp());

                    return new MLDataDTO(
                            hourOfDay,
                            dayOfWeek,
                            severity,
                            anomalyType,
                            sourceIpHash
                    );
                })
                .collect(Collectors.toList());
    }

    /** Counts produced by one detection run (or one batch of it). */
    public record DetectionRunSummary(int recordsScanned, int alertsCreated) {
    }
}
