package com.om.AnomalyDetectionApplication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_alerts")
public class AnomalyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String severity;
    private String sourceIp;

    @Column(length = 1000)
    private String description;

    private LocalDateTime detectedAt;

    public AnomalyAlert() {
    }

    public AnomalyAlert(String type, String severity, String sourceIp, String description, LocalDateTime detectedAt) {
        this.type = type;
        this.severity = severity;
        this.sourceIp = sourceIp;
        this.description = description;
        this.detectedAt = detectedAt;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }
}