package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.MlPredictionRequest;
import com.om.AnomalyDetectionApplication.dto.MlPredictionResponse;
import com.om.AnomalyDetectionApplication.dto.TrainModelResponse;
import com.om.AnomalyDetectionApplication.exception.MlServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public MlServiceClient(RestTemplate restTemplate,
                            @Value("${ml.service.base-url:http://127.0.0.1:8010}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public MlPredictionResponse getPredictions(MlPredictionRequest request) {
        log.info("Calling ML service /predict with {} records", request.getRecords().size());
        try {
            return restTemplate.postForObject(baseUrl + "/predict", request, MlPredictionResponse.class);
        } catch (HttpClientErrorException.BadRequest e) {
            throw new MlServiceException("Model not trained yet. Call /train first.", e);
        } catch (ResourceAccessException e) {
            throw new MlServiceException("ML service unreachable", e);
        } catch (RestClientException e) {
            throw new MlServiceException("ML service error: " + e.getMessage(), e);
        }
    }

    public TrainModelResponse trainModel(MlPredictionRequest request) {
        log.info("Calling ML service /train with {} records", request.getRecords().size());
        try {
            return restTemplate.postForObject(baseUrl + "/train", request, TrainModelResponse.class);
        } catch (ResourceAccessException e) {
            throw new MlServiceException("ML service unreachable", e);
        } catch (RestClientException e) {
            throw new MlServiceException("ML service error: " + e.getMessage(), e);
        }
    }

    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
