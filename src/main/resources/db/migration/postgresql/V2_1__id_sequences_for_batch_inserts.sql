-- Hibernate cannot batch INSERTs for IDENTITY columns, so ids now come from sequences that hand
-- out blocks of 50 (matching allocationSize and hibernate.jdbc.batch_size).
--
-- Each sequence starts 50 above the current maximum id: Hibernate's pooled optimizer treats a
-- value v as the top of the block (v - 49 .. v), so this guarantees no collision with existing
-- rows. The column default also uses the sequence, so rows inserted by hand keep working.

CREATE SEQUENCE anomaly_alerts_seq INCREMENT BY 50;
SELECT setval('anomaly_alerts_seq', COALESCE((SELECT MAX(id) FROM anomaly_alerts), 0) + 50, false);
ALTER TABLE anomaly_alerts ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE anomaly_alerts ALTER COLUMN id SET DEFAULT nextval('anomaly_alerts_seq');
ALTER SEQUENCE anomaly_alerts_seq OWNED BY anomaly_alerts.id;

CREATE SEQUENCE traffic_records_seq INCREMENT BY 50;
SELECT setval('traffic_records_seq', COALESCE((SELECT MAX(id) FROM traffic_records), 0) + 50, false);
ALTER TABLE traffic_records ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE traffic_records ALTER COLUMN id SET DEFAULT nextval('traffic_records_seq');
ALTER SEQUENCE traffic_records_seq OWNED BY traffic_records.id;
