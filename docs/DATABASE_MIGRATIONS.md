# Database migrations

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Hibernate no longer
creates or changes tables. It only checks at startup that the entities match the database
(`spring.jpa.hibernate.ddl-auto=validate`). If they do not match, the application refuses to start.

## Where the migrations live

| Folder | Used for |
|---|---|
| `src/main/resources/db/migration/common` | SQL that runs on every database (PostgreSQL and the H2 test database) |

| Version | File | What it does |
|---|---|---|
| V1 | `common/V1__baseline_schema.sql` | The schema as Hibernate created it before Flyway: `traffic_records` and `anomaly_alerts` |

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
