package com.om.AnomalyDetectionApplication.dto;

import java.util.List;

public class MlPredictionResponse {

    private List<MlPredictionResultDTO> results;

    public MlPredictionResponse() {
    }

    public List<MlPredictionResultDTO> getResults() {
        return results;
    }

    public void setResults(List<MlPredictionResultDTO> results) {
        this.results = results;
    }
}