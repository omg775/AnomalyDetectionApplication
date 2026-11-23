# Database migrations

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Hibernate no longer
creates or changes tables. It only checks at startup that the entities match the database
(`spring.jpa.hibernate.ddl-auto=validate`). If they do not match, the application refuses to start.

## Where the migrations live

| Folder | Used for |
|---|---|
| `src/main/resources/db/migration/common` | SQL that runs on every database (PostgreSQL and the H2 test database) |
| `src/main/resources/db/migration/postgresql` | PostgreSQL-only SQL (production) |
| `src/main/resources/db/migration/h2` | H2 equivalent of the PostgreSQL-only SQL (tests) |

| Version | File | What it does |
|---|---|---|
| V1 | `common/V1__baseline_schema.sql` | The schema as Hibernate created it before Flyway: `traffic_records` and `anomaly_alerts` |
| V2 | `common/V2__traffic_processed_marker.sql` | Adds `traffic_records.processed` (default `false`), marks rows the old timestamp watermark already covered as processed, adds indexes on `(processed, id)` and `anomaly_alerts(timestamp)` |
| V2.1 | `postgresql/V2_1__id_sequences_for_batch_inserts.sql` | Replaces the identity columns with the sequences `anomaly_alerts_seq` and `traffic_records_seq` (increment 50) so Hibernate can batch inserts. Each sequence starts above the current maximum id |
| V3 | `common/V3__traffic_row_hash.sql` | Adds `traffic_records.row_hash` (SHA-256 of the row content) with a unique index, used by the CSV import to skip exact duplicates |
| V3.1 | `postgresql/V3_1__backfill_traffic_row_hash.sql` | Computes `row_hash` for rows imported before V3. If existing rows are already exact duplicates of each other, only the lowest id gets the hash, the others keep `NULL` |
| V4 | `common/V4__alert_ml_scores.sql` | Adds `anomaly_alerts.ml_score`, `ml_flag`, `ml_algorithm`, `ml_scored_at` (all `NULL` until `POST /api/anomalies/score` runs) and an index on `ml_scored_at` |

### What V2 means for existing data

Before V2, detection only scanned traffic records newer than the latest alert. V2 marks every
record with `timestamp <= MAX(anomaly_alerts.timestamp)` as processed, which is exactly the set the
old logic would have skipped. The first run after the upgrade therefore behaves like the old one
and creates no duplicate alerts.

Records that were backfilled before the upgrade (older than the latest alert, never evaluated)
stay marked as processed. To evaluate the whole history again from scratch, run this once and then
call `POST /api/traffic/detect`:

```sql
BEGIN;
DELETE FROM anomaly_alerts;
UPDATE traffic_records SET processed = FALSE;
COMMIT;
```

This deletes all alerts, including their stored ML scores, so only do it on purpose.

To score all alerts again with a different algorithm, clear the stored scores and call
`POST /api/anomalies/score?algorithm=...`:

```sql
UPDATE anomaly_alerts SET ml_score = NULL, ml_flag = NULL, ml_algorithm = NULL, ml_scored_at = NULL;
```

### What V2.1 means for ids

Ids are still assigned automatically, but Hibernate now reserves them in blocks of 50. Ids stay
unique and increasing, but there can be gaps (for example after a restart). Rows inserted by hand
without an id keep working because the column default uses the same sequence.

## How it behaves

* **Empty database:** Flyway runs every migration, starting with V1.
* **Existing database without Flyway history (your current Supabase database):** because of
  `spring.flyway.baseline-on-migrate=true` and `baseline-version=1`, Flyway records V1 as the
  baseline *without executing it*, then runs every later migration. Existing data is not touched
  by the baseline.
* Flyway connects with its own short-lived connection (`spring.flyway.url`), because it needs two
  connections at once and the Hikari pool is capped at 1 for the Supabase pooler.

## Applying it to the existing Supabase database

1. **Back up first.** In the Supabase dashboard use Database, Backups, or run
   `pg_dump "$DB_URL_IN_LIBPQ_FORMAT" > backup.sql` from your machine.
2. **Use the Session pooler** (port 5432) or a direct connection for the first start. The
   Transaction pooler (port 6543) is not suitable for schema changes.
3. **Check that your tables match V1.** Run this in the Supabase SQL editor:

   ```sql
   SELECT table_name, column_name, data_type, is_nullable, is_identity
   FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name IN ('traffic_records', 'anomaly_alerts')
   ORDER BY table_name, ordinal_position;
   ```

   Expected: `id` is `bigint` and an identity column, `timestamp` is
   `timestamp without time zone`, `destination_port` is `integer`, `bytes_transferred` is
   `bigint`, text columns are `character varying`, and only `destination_ip` is nullable.
   If a type differs, stop and adjust before starting the application, because
   `ddl-auto=validate` will reject the mismatch.
4. **Start the application once** with the usual environment variables. Flyway logs lines such as
   `Successfully baselined schema with version: 1` and `Successfully applied N migrations`.
5. **Verify:**

   ```sql
   SELECT installed_rank, version, description, type, success
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```

   Every row must have `success = true`.

No manual SQL is required beyond the backup. If a migration fails, Flyway marks it as failed and
the application does not start. Restore the backup, fix the cause, and start again.

## Testing

* All tests run against an in-memory H2 database built from the same migrations.
* `FreshDatabaseMigrationTest` and `ExistingDatabaseMigrationTest` run the migrations against a
  real PostgreSQL 17 container with a pool size of 1. The second one simulates the existing
  Supabase database: tables created by Hibernate and already holding data. Both tests are skipped
  automatically when Docker is not available.
