package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.TrainModelResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PredictionService {

    private final DetectionService detectionService;
    private final MlServiceClient mlServiceClient;

    @Autowired
    public PredictionService(DetectionService detectionService, MlServiceClient mlServiceClient) {
        this.detectionService = detectionService;
        this.mlServiceClient = mlServiceClient;
    }

    public MlPredictionResponse predict(String algorithm) {
        List<MLDataDTO> records = detectionService.getMLData();
        MlPredictionRequest request = new MlPredictionRequest(records, algorithm, null);
        return mlServiceClient.getPredictions(request);
    }

    public TrainModelResponse trainModel(String algorithm, Double contamination) {
        List<MLDataDTO> records = detectionService.getMLData();
        MlPredictionRequest request = new MlPredictionRequest(records, algorithm, contamination);
        return mlServiceClient.trainModel(request);
    }
}