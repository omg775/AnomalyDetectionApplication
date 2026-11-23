-- Detection now scans traffic records that have not been processed yet instead of records newer
-- than the latest alert, so out-of-order and backfilled uploads are checked too.

ALTER TABLE traffic_records ADD COLUMN processed BOOLEAN NOT NULL DEFAULT FALSE;

-- Rows the previous timestamp watermark already covered count as processed, so the first
-- detection run after this migration does not create duplicate alerts for them.
UPDATE traffic_records
SET processed = TRUE
WHERE timestamp <= (SELECT MAX(timestamp) FROM anomaly_alerts);

CREATE INDEX idx_traffic_records_processed_id ON traffic_records (processed, id);
CREATE INDEX idx_anomaly_alerts_timestamp ON anomaly_alerts (timestamp);
