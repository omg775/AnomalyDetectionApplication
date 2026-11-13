-- Gives existing rows the same fingerprint the application computes (TrafficRowHash), so new
-- uploads are also checked against data imported before V3. When existing rows are already
-- exact duplicates of each other, only the lowest id gets the hash; the others keep NULL.

WITH hashed AS (
    SELECT id,
           encode(sha256(convert_to(
                   to_char(timestamp, 'YYYY-MM-DD"T"HH24:MI:SS.US')
                   || '|' || source_ip
                   || '|' || COALESCE(destination_ip, '')
                   || '|' || protocol
                   || '|' || destination_port
                   || '|' || bytes_transferred,
                   'UTF8')), 'hex') AS hash
    FROM traffic_records
    WHERE row_hash IS NULL
),
first_of_each AS (
    SELECT id, hash, ROW_NUMBER() OVER (PARTITION BY hash ORDER BY id) AS position
    FROM hashed
)
UPDATE traffic_records t
SET row_hash = f.hash
FROM first_of_each f
WHERE t.id = f.id
  AND f.position = 1;
