package com.om.AnomalyDetectionApplication.dto;

/** Result of POST /api/anomalies/score. */
public record ScoreResponse(int scoredCount, int anomalyCount, String algorithm) {
}
