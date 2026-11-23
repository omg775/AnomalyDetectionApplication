-- H2 counterpart of postgresql/V2_1 for the test database. H2 has no setval(), and the test
-- database is always empty at this point, so the sequences simply start at 50.

CREATE SEQUENCE anomaly_alerts_seq START WITH 50 INCREMENT BY 50;
ALTER TABLE anomaly_alerts ALTER COLUMN id DROP IDENTITY;
ALTER TABLE anomaly_alerts ALTER COLUMN id SET DEFAULT nextval('anomaly_alerts_seq');

CREATE SEQUENCE traffic_records_seq START WITH 50 INCREMENT BY 50;
ALTER TABLE traffic_records ALTER COLUMN id DROP IDENTITY;
ALTER TABLE traffic_records ALTER COLUMN id SET DEFAULT nextval('traffic_records_seq');
