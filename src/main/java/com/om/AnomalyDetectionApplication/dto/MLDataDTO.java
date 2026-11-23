package com.om.AnomalyDetectionApplication.dto;

/**
 * Feature vector of one anomaly alert as sent to the ML service. {@code id} is the alert id; it is
 * carried through to the prediction result and is not used as a feature.
 */
public class MLDataDTO {

    private final Long id;
    private final int hourOfDay;
    private final int dayOfWeek;
    private final int severity;
    private final int anomalyType;
    private final int sourceIpHash;

    public MLDataDTO(Long id, int hourOfDay, int dayOfWeek, int severity, int anomalyType, int sourceIpHash) {
        this.id = id;
        this.hourOfDay = hourOfDay;
        this.dayOfWeek = dayOfWeek;
        this.severity = severity;
        this.anomalyType = anomalyType;
        this.sourceIpHash = sourceIpHash;
    }

    public Long getId() {
        return id;
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
