package com.om.AnomalyDetectionApplication.entity;

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
}