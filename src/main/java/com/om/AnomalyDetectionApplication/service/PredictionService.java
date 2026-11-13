package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResultDTO;
import com.om.AnomalyDetectionApplication.dto.TrainModelResponse;
import com.om.AnomalyDetectionApplication.util.MLFeatureMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PredictionService {

    private final MlFeatureService mlFeatureService;
    private final MlServiceClient mlServiceClient;

    public PredictionService(MlFeatureService mlFeatureService, MlServiceClient mlServiceClient) {
        this.mlFeatureService = mlFeatureService;
        this.mlServiceClient = mlServiceClient;
    }

    /**
     * Scores every alert with the trained model. Alerts are sent to the ML service one batch at a
     * time; each prediction depends only on its own record, so batching does not change results.
     *
     * @param algorithm the model to use, or null for the ML service default
     * @return one result per alert, in id order, each carrying the alert id
     */
    public MlPredictionResponse predict(String algorithm) {
        List<MlPredictionResultDTO> results = new ArrayList<>();
        mlFeatureService.forEachAlertBatch(batch -> {
            List<MLDataDTO> records = batch.stream().map(MLFeatureMapper::toFeatures).toList();
            results.addAll(mlServiceClient.getPredictions(new MlPredictionRequest(records, algorithm, null)).getResults());
        });
        return new MlPredictionResponse(results);
    }

    /**
     * Trains a model on all alerts. Training needs the whole dataset, so it is sent in one request.
     *
     * @param algorithm     the model to train, or null for the ML service default
     * @param contamination expected share of anomalies, or null for the ML service default
     * @return the ML service's training summary
     */
    public TrainModelResponse trainModel(String algorithm, Double contamination) {
        List<MLDataDTO> records = mlFeatureService.loadAllFeatures();
        return mlServiceClient.trainModel(new MlPredictionRequest(records, algorithm, contamination));
    }
}
