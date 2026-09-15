package com.om.AnomalyDetectionApplication.dto;

import java.util.List;

public class MlPredictionRequest {

    private List<MLDataDTO> records;
    private String algorithm;
    private Double contamination;

    public MlPredictionRequest() {
    }

    public MlPredictionRequest(List<MLDataDTO> records) {
        this.records = records;
    }

    public MlPredictionRequest(List<MLDataDTO> records, String algorithm, Double contamination) {
        this.records = records;
        this.algorithm = algorithm;
        this.contamination = contamination;
    }

    public List<MLDataDTO> getRecords() {
        return records;
    }

    public void setRecords(List<MLDataDTO> records) {
        this.records = records;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Double getContamination() {
        return contamination;
    }

    public void setContamination(Double contamination) {
        this.contamination = contamination;
    }
}
