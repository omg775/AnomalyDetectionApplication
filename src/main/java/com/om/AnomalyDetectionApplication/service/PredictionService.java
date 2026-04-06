package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MLDataDTO;
import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
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

    public MlPredictionResponse predict() {
        List<MLDataDTO> records = detectionService.getMLData();
        MlPredictionRequest request = new MlPredictionRequest(records);
        return mlServiceClient.getPredictions(request);
    }
}