-- Content fingerprint used by the CSV import to skip exact duplicate rows. Rows imported before
-- this column existed have no hash (NULL), which the unique index allows any number of times.

ALTER TABLE traffic_records ADD COLUMN row_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_traffic_records_row_hash ON traffic_records (row_hash);
