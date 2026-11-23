# Upgrade report

Branch `upgrade`, created from `origin/master` at `9b31bd7`. Nothing has been pushed.

Local `master` was one commit behind `origin/master`. That commit (`9b31bd7`, "harden security, fix
core bugs, and add multi-algorithm ML + evaluation") is the version the upgrade brief describes, so
local `master` was fast-forwarded to it before the branch was created.

## Test counts

| Point | `mvn clean test` summary line |
|---|---|
| Baseline (before any change) | `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0` |
| After Phase 1 | `Tests run: 173, Failures: 0, Errors: 0, Skipped: 0` |
| After Phase 2 | `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0` |

ML service (pytest, Python 3.12, pinned requirements): none before, 16 after Phase 1, 47 after
Phase 2 (`47 passed`, warnings treated as errors).

The PostgreSQL migration tests (`FreshDatabaseMigrationTest`, `ExistingDatabaseMigrationTest`) need
Docker. Without Docker they are skipped, not failed, and the summary shows them as skipped.

## Phase 1: correctness and security

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Detection watermark bug | Done | Commit `bf1e80e`. Committed after task 2 so every commit has a working schema. |
| 2 | Flyway | Done | Commit `ec7ee24`. Flyway gets its own unpooled connection (see below). |
| 3 | JSON 401 and 403 | Done | Commit `1053251`. |
| 4 | Fail fast on unsafe secrets | Done | Commit `a090dcb`. |
| 5 | Login rate limit | Done | Commit `894589d`. |
| 6 | ML service API key | Done | Commit `5d0342a`. `/models` is protected too, `/` and `/health` stay open. |
| 7 | CSV import hardening | Done | Commit `4df215d`. Default protocol allow list is `TCP,UDP,ICMP`; check it against your dataset. |
| 8 | Config hygiene | Done | Commit `988f374`. |
| extra | 4xx instead of 500 | Done | Commit `7f2d703`. Found by a task 8 test. |
| extra | No generated password in logs | Done | Commit `a522ea0`. Found in a production-config smoke run. |

### Findings worth knowing

* **Secrets in git history.** Commits up to and including `6508b15` contain the real Supabase
  database password, the JWT secret and the plaintext admin password in
  `src/main/resources/application.properties`. They were removed from the working tree in `9b31bd7`
  but are still in the history on `origin`. Rotate all three (Supabase: Project Settings, Database,
  Reset database password; choose a new `JWT_SECRET`; choose a new admin password and hash it).
  Rewriting history would need a force push and was not done.
* **Flyway and a pool of 1.** Flyway needs two connections at once. With
  `spring.datasource.hikari.maximum-pool-size=1` it waits forever and startup fails after 30 s. This
  was found by the PostgreSQL test and fixed by giving Flyway its own short-lived connection
  (`spring.flyway.url`). The H2 tests would never have shown it.
* **JDBC batching needs sequences.** Hibernate silently ignores `hibernate.jdbc.batch_size` for
  `IDENTITY` ids. V2.1 switches both tables to sequences that hand out blocks of 50. Ids stay unique
  but can have gaps.
* **Client errors returned 500.** Unknown paths, wrong methods, malformed JSON, missing upload parts
  and wrongly typed parameters all produced 500. They now return the correct 4xx.
* **Homebrew Python is broken on this machine.** `python@3.11` and `python@3.14` cannot create a
  venv (`pyexpat` was built against a newer libexpat than macOS has). `brew reinstall python@3.11`
  should fix it. The ML tests were run in a `python:3.12-slim` container instead. The existing
  `ml-service/venv` has no pip and was left untouched.

## Phase 2: ML integration quality

| # | Task | Status | Notes |
|---|---|---|---|
| 9 | Alert id in ML DTOs, paging | Done | Commit `c881f5b`. Keyset paging (`ml.feature.batch-size`, default 5000). Predict sends one ML request per batch; training still sends everything in one request because the model must see the whole dataset. Three duplicate copies of the feature code were merged into `MlFeatureService`. |
| 10 | Persist model output | Done | Commit `74fddc3`. Migration is **V4**, not V3 (V3 holds the CSV row hash). `ml_scored_at` is stored in UTC. |
| 11 | ML service performance and robustness | Done | Commit `979c942`. Model and metadata live in one bundle file so they can never disagree. A stored model with another feature schema gets 409. |
| 12 | ML service hygiene | Done | Commit `e8edfab`. Test client uses `httpx2` (Starlette deprecates `httpx`). |

An end-to-end run (backend jar on PostgreSQL, ML service in a container with the pinned
requirements) covered upload, detect, train, predict, score, the alerts list, agreement and `/stats`.

**Retrain once after deploying.** Models trained before task 11 used a different file format and
location, so the ML service does not pick them up and answers "not trained yet" until
`POST /api/anomalies/train` is called.

## Behaviour changes that affect the thesis

| Change | Before | After | Thesis section |
|---|---|---|---|
| Unauthenticated request | 403, empty body | 401, JSON `ErrorResponse`, `WWW-Authenticate: Bearer` | REST API and Security; Testing |
| Invalid or expired token | 403, empty body | 401, message "Invalid or expired token" | REST API and Security |
| Repeated failed logins | Unlimited | 429 with `Retry-After` after 5 failures in 15 min per IP or per username | REST API and Security; Requirements |
| Detection run logic | Scans records newer than the latest alert | Scans records with `processed = false` in id order, marks them processed in the same transaction; idempotent | Heuristic Detection Layer; Data Model |
| New column `traffic_records.processed` | | `BOOLEAN NOT NULL DEFAULT FALSE`, index on `(processed, id)` | Data Model |
| New column `traffic_records.row_hash` | | SHA-256 of the row, unique index | Data Model |
| Id generation | `IDENTITY` | Sequences `anomaly_alerts_seq`, `traffic_records_seq` (increment 50) | Data Model |
| Schema management | Hibernate `ddl-auto=none`, tables created earlier by Hibernate | Flyway migrations, `ddl-auto=validate` | Architecture; Data Model |
| CSV upload validation | Only parse errors | IP (v4 and v6), port 0..65535, bytes >= 0, protocol allow list, max rows | REST API and Security; Requirements |
| CSV upload duplicates | Imported again | Skipped and reported; `skippedCount` now includes duplicates | REST API and Security |
| CSV upload response | `importedCount`, `skippedCount`, `errors` | Same fields plus `duplicateCount` and `skippedRows` | REST API and Security |
| CSV missing column or too many rows | 200 with every row skipped | 400 `ErrorResponse`, nothing imported | REST API and Security |
| CSV blank `destination_ip` | Stored as empty string | Stored as `NULL` | Data Model |
| Client errors (404, 405, malformed JSON, bad parameter) | 500 | Correct 4xx in `ErrorResponse` format | REST API and Security |
| Backend to ML service | No authentication | `X-API-Key` header; ML service answers 401 without it | ML Service; Architecture |
| ML service startup | Always starts | Refuses to start without `ML_API_KEY` unless `ALLOW_INSECURE_DEV=true` | ML Service |
| CORS | Any origin by default | No cross-origin access by default; `*` only with profile `dev` | REST API and Security |
| Health endpoint | None | `/actuator/health`, `/liveness`, `/readiness` public; other actuator endpoints need a token | Architecture |
| Startup checks | None | Refuses to start with `JWT_SECRET` < 32 bytes or a non-BCrypt `ADMIN_PASSWORD` | REST API and Security |
| ML request and result DTOs | No id | Each record and each prediction carries the alert `id` (not a feature); `GET /api/anomalies/ml-data` includes it | ML Service; REST API and Security |
| Prediction over all alerts | One request with every alert | One request per batch of 5000 alerts, results concatenated in id order; values unchanged | ML Service; Architecture |
| Agreement evaluation | Predictions matched by list position | Matched by alert id | ML Service; Testing |
| New endpoint `POST /api/anomalies/score` | | Scores alerts without a stored score and saves the result | REST API and Security; ML Service |
| New columns `anomaly_alerts.ml_score`, `ml_flag`, `ml_algorithm`, `ml_scored_at` | | `NULL` until scored | Data Model |
| Alerts response | | Contains `mlScore`, `mlFlag`, `mlAlgorithm`, `mlScoredAt` only for scored alerts | REST API and Security |
| ML model storage | `model_<algorithm>.joblib` and `.meta.json` in the working directory, loaded on every request | One bundle per algorithm in `MODEL_DIR` (absolute), loaded once and cached; metadata has feature schema and library versions | ML Service |
| ML request size | Unlimited | 413 above `MAX_REQUEST_BYTES` (50 MiB) | ML Service |
| ML stale model | Would predict anyway | 409 when the stored model's feature schema differs | ML Service |
| ML dependencies | Unpinned, included matplotlib and requests | Pinned exact versions, Python 3.12 | ML Service; Testing |

Unchanged: every existing endpoint path and request shape, the login response bodies (`{"token"}` and
`{"message": "Invalid credentials"}`), the detection rules and thresholds, and the detect endpoint's
response message.

## Commands for the Supabase database

None are required. Flyway applies everything on the first start (see
`docs/DATABASE_MIGRATIONS.md`): take a backup, connect through the Session pooler, start the
application once, then check `flyway_schema_history`.

Optional: to re-evaluate every historic traffic record, including backfilled ones the old watermark
never checked, run this once and then call `POST /api/traffic/detect`. It deletes all alerts.

```sql
BEGIN;
DELETE FROM anomaly_alerts;
UPDATE traffic_records SET processed = FALSE;
COMMIT;
```
