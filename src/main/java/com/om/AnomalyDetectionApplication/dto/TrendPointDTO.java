package com.om.AnomalyDetectionApplication.dto;

public class TrendPointDTO {

    private String time;
    private Long count;

    public TrendPointDTO(String time, Long count) {
        this.time = time;
        this.count = count;
    }

    public String getTime() {
        return time;
    }

    public Long getCount() {
        return count;
    }
}