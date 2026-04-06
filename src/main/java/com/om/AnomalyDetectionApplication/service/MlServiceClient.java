package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MlServiceClient {

    private final RestTemplate restTemplate;
    private final String fastApiUrl = "http://127.0.0.1:8010/predict";

    @Autowired
    public MlServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public MlPredictionResponse getPredictions(MlPredictionRequest request) {
        return restTemplate.postForObject(
                fastApiUrl,
                request,
                MlPredictionResponse.class
        );
    }
}