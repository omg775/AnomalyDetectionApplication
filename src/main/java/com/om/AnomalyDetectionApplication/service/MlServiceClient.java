package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class MlServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl = "http://127.0.0.1:8010";

    @Autowired
    public MlServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public MlPredictionResponse getPredictions(MlPredictionRequest request) {
        return restTemplate.postForObject(
                baseUrl + "/predict",
                request,
                MlPredictionResponse.class
        );
    }
    
    public Map trainModel(MlPredictionRequest request) {
        return restTemplate.postForObject(
                baseUrl + "/train",
                request,
                Map.class
        );
    }
}