package com.om.AnomalyDetectionApplication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrainModelResponse {

    private String message;

    @JsonProperty("records_processed")
    private Integer recordsProcessed;

    public TrainModelResponse() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(Integer recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }
}
