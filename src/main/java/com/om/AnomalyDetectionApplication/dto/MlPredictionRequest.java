package com.om.AnomalyDetectionApplication.dto;

import java.util.List;

public class MlPredictionRequest {

    private List<MLDataDTO> records;

    public MlPredictionRequest() {
    }

    public MlPredictionRequest(List<MLDataDTO> records) {
        this.records = records;
    }

    public List<MLDataDTO> getRecords() {
        return records;
    }

    public void setRecords(List<MLDataDTO> records) {
        this.records = records;
    }
}