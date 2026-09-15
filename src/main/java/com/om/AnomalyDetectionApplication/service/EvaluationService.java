package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.AgreementResponse;
import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.entity.AnomalyAlert;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import com.om.AnomalyDetectionApplication.repository.AnomalyAlertRepository;
import com.om.AnomalyDetectionApplication.util.MLFeatureMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reports how often the heuristic layer's HIGH-severity verdict agrees with
 * the ML layer's anomaly flag, giving a concrete measure of what the ML
 * stage adds on top of the heuristic rules alone.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final AnomalyAlertRepository anomalyAlertRepository;
    private final MlServiceClient mlServiceClient;

    public EvaluationService(AnomalyAlertRepository anomalyAlertRepository, MlServiceClient mlServiceClient) {
        this.anomalyAlertRepository = anomalyAlertRepository;
        this.mlServiceClient = mlServiceClient;
    }

    public AgreementResponse computeAgreement() {
        List<AnomalyAlert> alerts = anomalyAlertRepository.findAll().stream()
                .filter(a -> a.getTimestamp() != null)
                .toList();

        if (alerts.isEmpty()) {
            return new AgreementResponse(0, 0, 0, 0, 0, 0.0);
        }

        List<MLDataDTO> records = alerts.stream()
                .map(a -> new MLDataDTO(
                        a.getTimestamp().getHour(),
                        a.getTimestamp().getDayOfWeek().getValue(),
                        MLFeatureMapper.mapSeverity(a.getSeverity()),
                        MLFeatureMapper.mapAnomalyType(a.getAnomalyType()),
                        MLFeatureMapper.hashIpAddress(a.getSourceIp())
                ))
                .toList();

        MlPredictionRequest request = new MlPredictionRequest(records, null, null);
        MlPredictionResponse response = mlServiceClient.getPredictions(request);
        List<MlPredictionResultDTO> results = response.getResults();

        if (results.size() != alerts.size()) {
            throw new MlServiceException(
                    "ML service returned a mismatched number of predictions (expected "
                            + alerts.size() + ", got " + results.size() + ")", null);
        }

        int bothFlagged = 0;
        int heuristicOnly = 0;
        int mlOnly = 0;
        int neitherFlagged = 0;

        for (int i = 0; i < alerts.size(); i++) {
            boolean heuristicHigh = "HIGH".equalsIgnoreCase(alerts.get(i).getSeverity());
            boolean mlAnomaly = results.get(i).getPrediction() == -1;

            if (heuristicHigh && mlAnomaly) {
                bothFlagged++;
            } else if (heuristicHigh) {
                heuristicOnly++;
            } else if (mlAnomaly) {
                mlOnly++;
            } else {
                neitherFlagged++;
            }
        }

        int total = alerts.size();
        double agreementRate = (double) (bothFlagged + neitherFlagged) / total;

        log.info("Agreement evaluation: {} alerts compared, {} both, {} heuristic-only, {} ML-only, {} neither, {}% agreement",
                total, bothFlagged, heuristicOnly, mlOnly, neitherFlagged, Math.round(agreementRate * 10000.0) / 100.0);

        return new AgreementResponse(total, bothFlagged, heuristicOnly, mlOnly, neitherFlagged, agreementRate);
    }
}
