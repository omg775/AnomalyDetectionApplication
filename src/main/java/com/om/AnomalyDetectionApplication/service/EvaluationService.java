package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.AgreementResponse;
import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import com.om.AnomalyDetectionApplication.util.MLFeatureMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reports how often the heuristic layer's HIGH-severity verdict agrees with
 * the ML layer's anomaly flag, giving a concrete measure of what the ML
 * stage adds on top of the heuristic rules alone.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final MlFeatureService mlFeatureService;
    private final MlServiceClient mlServiceClient;

    public EvaluationService(MlFeatureService mlFeatureService, MlServiceClient mlServiceClient) {
        this.mlFeatureService = mlFeatureService;
        this.mlServiceClient = mlServiceClient;
    }

    /**
     * Compares, alert by alert, the heuristic HIGH-severity verdict with the default model's
     * anomaly flag. Alerts are scored batch by batch and predictions are matched by alert id.
     *
     * @return the agreement counts and rate
     * @throws MlServiceException when the ML service does not return a prediction for every alert
     */
    public AgreementResponse computeAgreement() {
        Tally tally = new Tally();
        mlFeatureService.forEachAlertBatch(batch -> {
            Map<Long, Boolean> mlFlags = predictBatch(batch);
            for (AnomalyAlert alert : batch) {
                tally.add("HIGH".equalsIgnoreCase(alert.getSeverity()), mlFlags.get(alert.getId()));
            }
        });

        int total = tally.total();
        if (total == 0) {
            return new AgreementResponse(0, 0, 0, 0, 0, 0.0);
        }
        double agreementRate = (double) (tally.both + tally.neither) / total;

        log.info("Agreement evaluation: {} alerts compared, {} both, {} heuristic-only, {} ML-only, {} neither, {}% agreement",
                total, tally.both, tally.heuristicOnly, tally.mlOnly, tally.neither,
                Math.round(agreementRate * 10000.0) / 100.0);

        return new AgreementResponse(total, tally.both, tally.heuristicOnly, tally.mlOnly, tally.neither, agreementRate);
    }

    /** Returns the ML anomaly flag per alert id for one batch. */
    private Map<Long, Boolean> predictBatch(List<AnomalyAlert> batch) {
        List<MLDataDTO> records = batch.stream().map(MLFeatureMapper::toFeatures).toList();
        List<MlPredictionResultDTO> results = mlServiceClient
                .getPredictions(new MlPredictionRequest(records, null, null))
                .getResults();

        Map<Long, Boolean> flags = results.stream()
                .filter(result -> result.getId() != null)
                .collect(Collectors.toMap(MlPredictionResultDTO::getId, result -> result.getPrediction() == -1,
                        (first, second) -> first));
        List<Long> missing = batch.stream().map(AnomalyAlert::getId).filter(id -> !flags.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new MlServiceException("ML service returned no prediction for " + missing.size()
                    + " of " + batch.size() + " alerts", null);
        }
        return flags;
    }

    private static final class Tally {
        private int both;
        private int heuristicOnly;
        private int mlOnly;
        private int neither;

        void add(boolean heuristic, boolean ml) {
            if (heuristic && ml) {
                both++;
            } else if (heuristic) {
                heuristicOnly++;
            } else if (ml) {
                mlOnly++;
            } else {
                neither++;
            }
        }

        int total() {
            return both + heuristicOnly + mlOnly + neither;
        }
    }
}
