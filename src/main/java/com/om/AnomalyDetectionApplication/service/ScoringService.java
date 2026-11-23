package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.dto.ScoreResponse;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.util.MLFeatureMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persists the ML layer's verdict on each alert. Only alerts without a stored score are sent, one
 * batch at a time. The ML call happens outside any database transaction so the (single) pooled
 * connection is not held during HTTP; each batch's results are then saved in a short transaction.
 * A run interrupted by an ML failure keeps the batches already saved, and the next run resumes.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final MlServiceClient mlServiceClient;
    private final TransactionOperations transactionOperations;
    private final Clock clock;
    private final int batchSize;
    private final String defaultAlgorithm;

    public ScoringService(AnomalyAlertRepository anomalyAlertRepository,
                          MlServiceClient mlServiceClient,
                          TransactionOperations transactionOperations,
                          Clock clock,
                          @Value("${ml.feature.batch-size:5000}") int batchSize,
                          @Value("${ml.service.default-algorithm:isolation_forest}") String defaultAlgorithm) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.mlServiceClient = mlServiceClient;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
        this.batchSize = batchSize;
        this.defaultAlgorithm = defaultAlgorithm;
    }

    /**
     * Scores every alert that has no stored ML score and saves score, flag, algorithm and time.
     *
     * @param algorithm the model to use, or null for {@code ml.service.default-algorithm}
     * @return how many alerts were scored and how many of them the model flagged
     * @throws MlServiceException when the ML service fails or omits a prediction; batches saved
     *                            before the failure stay saved
     */
    public ScoreResponse scoreUnscoredAlerts(String algorithm) {
        String usedAlgorithm = algorithm != null ? algorithm : defaultAlgorithm;
        int scored = 0;
        int flagged = 0;
        long lastId = 0;
        List<AnomalyAlert> batch;

        do {
            batch = anomalyAlertRepository.findByMlScoredAtIsNullAndIdGreaterThanOrderByIdAsc(lastId, Limit.of(batchSize));
            if (batch.isEmpty()) {
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();

            Map<Long, MlPredictionResultDTO> results = predict(batch, usedAlgorithm);
            LocalDateTime scoredAt = LocalDateTime.now(clock);
            transactionOperations.executeWithoutResult(status -> save(results, usedAlgorithm, scoredAt));

            scored += results.size();
            flagged += (int) results.values().stream().filter(r -> r.getPrediction() == -1).count();
        } while (batch.size() == batchSize);

        log.info("ML scoring finished: {} alerts scored with {}, {} flagged as anomalies", scored, usedAlgorithm, flagged);
        return new ScoreResponse(scored, flagged, usedAlgorithm);
    }

    private Map<Long, MlPredictionResultDTO> predict(List<AnomalyAlert> batch, String algorithm) {
        List<MLDataDTO> records = batch.stream().map(MLFeatureMapper::toFeatures).toList();
        Map<Long, MlPredictionResultDTO> results = mlServiceClient
                .getPredictions(new MlPredictionRequest(records, algorithm, null))
                .getResults().stream()
                .filter(result -> result.getId() != null)
                .collect(Collectors.toMap(MlPredictionResultDTO::getId, Function.identity(), (first, second) -> first));
        long missing = batch.stream().filter(alert -> !results.containsKey(alert.getId())).count();
        if (missing > 0) {
            throw new MlServiceException("ML service returned no prediction for " + missing
                    + " of " + batch.size() + " alerts", null);
        }
        return results;
    }

    private void save(Map<Long, MlPredictionResultDTO> results, String algorithm, LocalDateTime scoredAt) {
        List<AnomalyAlert> managed = anomalyAlertRepository.findAllById(results.keySet());
        for (AnomalyAlert alert : managed) {
            MlPredictionResultDTO result = results.get(alert.getId());
            alert.recordMlScore(result.getScore(), result.getPrediction() == -1, algorithm, scoredAt);
        }
        anomalyAlertRepository.saveAll(managed);
    }
}
