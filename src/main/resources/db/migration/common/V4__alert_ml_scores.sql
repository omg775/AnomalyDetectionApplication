-- Stored output of the ML layer per alert, written by POST /api/anomalies/score.
-- All columns stay NULL until an alert has been scored.

ALTER TABLE anomaly_alerts ADD COLUMN ml_score DOUBLE PRECISION;
ALTER TABLE anomaly_alerts ADD COLUMN ml_flag BOOLEAN;
ALTER TABLE anomaly_alerts ADD COLUMN ml_algorithm VARCHAR(64);
ALTER TABLE anomaly_alerts ADD COLUMN ml_scored_at TIMESTAMP(6);

CREATE INDEX idx_anomaly_alerts_ml_scored_at ON anomaly_alerts (ml_scored_at);
