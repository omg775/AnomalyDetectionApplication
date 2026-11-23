package com.om.AnomalyDetectionApplication.dto;

public class MlPredictionResultDTO {

    /** Id of the alert this prediction belongs to. */
    private Long id;
    private int hourOfDay;
    private int dayOfWeek;
    private int severity;
    private int anomalyType;
    private int isNight;
    private int isWeekend;
    private int prediction;
    private double score;

    public MlPredictionResultDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(int hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public int getAnomalyType() {
        return anomalyType;
    }

    public void setAnomalyType(int anomalyType) {
        this.anomalyType = anomalyType;
    }

    public int getIsNight() {
        return isNight;
    }

    public void setIsNight(int isNight) {
        this.isNight = isNight;
    }

    public int getIsWeekend() {
        return isWeekend;
    }

    public void setIsWeekend(int isWeekend) {
        this.isWeekend = isWeekend;
    }

    public int getPrediction() {
        return prediction;
    }

    public void setPrediction(int prediction) {
        this.prediction = prediction;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}