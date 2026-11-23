package com.om.AnomalyDetectionApplication.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_alerts")
public class AnomalyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "anomaly_alerts_seq")
    @SequenceGenerator(name = "anomaly_alerts_seq", sequenceName = "anomaly_alerts_seq", allocationSize = 50)
    private Long id;

    @Column(name = "source_ip", nullable = false)
    private String sourceIp;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType;

    @Column(nullable = false)
    private String severity;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** Model decision score; lower means more anomalous. Omitted from JSON until scored. */
    @Column(name = "ml_score")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double mlScore;

    /** True when the model classified the alert as an anomaly. */
    @Column(name = "ml_flag")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean mlFlag;

    @Column(name = "ml_algorithm", length = 64)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mlAlgorithm;

    @Column(name = "ml_scored_at")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime mlScoredAt;

    public AnomalyAlert() {
    }

    public Long getId() {
        return id;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    public void setAnomalyType(String anomalyType) {
        this.anomalyType = anomalyType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getMlScore() {
        return mlScore;
    }

    public Boolean getMlFlag() {
        return mlFlag;
    }

    public String getMlAlgorithm() {
        return mlAlgorithm;
    }

    public LocalDateTime getMlScoredAt() {
        return mlScoredAt;
    }

    /** Stores the ML verdict for this alert. */
    public void recordMlScore(double score, boolean flagged, String algorithm, LocalDateTime scoredAt) {
        this.mlScore = score;
        this.mlFlag = flagged;
        this.mlAlgorithm = algorithm;
        this.mlScoredAt = scoredAt;
    }
}
