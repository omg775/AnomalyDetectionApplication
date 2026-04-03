package com.om.AnomalyDetectionApplication.dto;

import java.util.Map;

public class AnomalySummaryResponse {

    private long total;
    private long high;
    private long medium;
    private long low;
    private Map<String, Long> byType;

    public AnomalySummaryResponse() {
    }

    public AnomalySummaryResponse(long total, long high, long medium, long low, Map<String, Long> byType) {
        this.total = total;
        this.high = high;
        this.medium = medium;
        this.low = low;
        this.byType = byType;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getHigh() {
        return high;
    }

    public void setHigh(long high) {
        this.high = high;
    }

    public long getMedium() {
        return medium;
    }

    public void setMedium(long medium) {
        this.medium = medium;
    }

    public long getLow() {
        return low;
    }

    public void setLow(long low) {
        this.low = low;
    }

    public Map<String, Long> getByType() {
        return byType;
    }

    public void setByType(Map<String, Long> byType) {
        this.byType = byType;
    }
}