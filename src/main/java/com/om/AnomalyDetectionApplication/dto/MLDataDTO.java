package com.om.AnomalyDetectionApplication.dto;

public class MLDataDTO {

    private int hourOfDay;
    private int dayOfWeek;
    private int severity;
    private int anomalyType;
    private int sourceIpHash;

    public MLDataDTO(int hourOfDay, int dayOfWeek, int severity, int anomalyType, int sourceIpHash) {
        this.hourOfDay = hourOfDay;
        this.dayOfWeek = dayOfWeek;
        this.severity = severity;
        this.anomalyType = anomalyType;
        this.sourceIpHash = sourceIpHash;
    }

    public int getHourOfDay() {
        return hourOfDay;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getSeverity() {
        return severity;
    }

    public int getAnomalyType() {
        return anomalyType;
    }

    public int getSourceIpHash() {
        return sourceIpHash;
    }
}