package com.om.AnomalyDetectionApplication.dto;

public class TopSourceDTO {

    private String sourceIp;
    private Long count;
    private String highestSeverity;

    public TopSourceDTO(String sourceIp, Long count, String highestSeverity) {
        this.sourceIp = sourceIp;
        this.count = count;
        this.highestSeverity = highestSeverity;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public Long getCount() {
        return count;
    }

    public String getHighestSeverity() {
        return highestSeverity;
    }
}