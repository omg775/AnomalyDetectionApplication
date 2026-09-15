# Anomaly Detection Application

A network traffic anomaly detection backend combining a **Spring Boot 4** (Java 21) API with a **FastAPI** ML microservice, backed by PostgreSQL (Supabase).

## Architecture

Detection is a two-layer hybrid pipeline:

```
CSV upload ──▶ traffic_records (Postgres)
                     │
                     ▼
        DetectionService (heuristic rules)
        - bytes transferred over threshold  → HIGH_TRAFFIC_VOLUME
        - connection to a suspicious port   → SUSPICIOUS_PORT_ACCESS
                     │
                     ▼
             anomaly_alerts (Postgres)
                     │
                     ├──▶ REST API (alerts, summary, trend, top sources)
                     │
                     └──▶ MLFeatureMapper ──▶ FastAPI ML service
                                               (IsolationForest /
                                                LocalOutlierFactor /
                                                OneClassSVM)
                                                      │
                                                      ▼
                                          predictions + anomaly scores
```

1. **Heuristic layer** (`DetectionService`) scans `traffic_records` and writes rule-based `anomaly_alerts` with a severity (`HIGH`/`MEDIUM`) and type.
2. **ML layer** (FastAPI, `ml-service/app.py`) trains an unsupervised outlier-detection model over features derived from those alerts (hour of day, day of week, severity, type, hashed source IP, is-night, is-weekend) and scores new data against it.
3. **`GET /api/evaluation/agreement`** reports how often the two layers agree, as a concrete measure of what the ML stage adds beyond the heuristic rules alone.

`MLFeatureMapper` (`src/main/java/.../util/MLFeatureMapper.java`) is the single source of truth for how alert fields are encoded into ML features — both the training-data path and any prediction-input path use it, so the two layers can never drift out of sync on label encoding.

## Tech Stack
* **Java 21**, **Spring Boot 4**, **Spring Security** (stateless JWT), **Spring Data JPA**, **PostgreSQL**
* **FastAPI**, **scikit-learn**, **pandas**, **joblib**
* **Apache Commons CSV**

## Setup

### 1. Environment variables

Copy the guidance in [`src/main/resources/application.properties.example`](src/main/resources/application.properties.example) into real environment variables — `application.properties` itself only contains `${VAR}` placeholders and is safe to commit.

Required:
* `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — your Postgres connection.
* `JWT_SECRET` — a random 256-bit+ secret (`openssl rand -hex 32`).
* `ADMIN_USERNAME`, `ADMIN_PASSWORD` — **`ADMIN_PASSWORD` must be a BCrypt hash**, not plaintext. Generate one with:
  ```
  mvn compile
  java -cp target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
    com.om.AnomalyDetectionApplication.util.PasswordHashGenerator "your-strong-password"
  ```

Optional (sane defaults if unset):
* `CORS_ALLOWED_ORIGINS` (default `*`)
* `ML_SERVICE_BASE_URL` (default `http://127.0.0.1:8010`)

### 2. Run the Spring Boot API
```
mvn spring-boot:run
```
Runs on `http://localhost:8080`.

### 3. Run the FastAPI ML service
```
cd ml-service
pip install -r requirements.txt
uvicorn app:app --port 8010
```

### 4. Run tests
```
mvn test
```
Tests run against an in-memory H2 database (`src/test/resources/application.properties`) — no external DB or ML service needed.

## API

All endpoints except `/api/auth/login` require a `Authorization: Bearer <token>` header, obtained from `/api/auth/login`.

### Auth
* `POST /api/auth/login` — `{"username": "...", "password": "..."}` → `{"token": "..."}`.

### Traffic ingestion & detection
* `POST /api/traffic/upload` — multipart CSV upload (`file` field; columns: `timestamp, source_ip, destination_ip, protocol, destination_port, bytes_transferred`). Malformed rows are skipped and reported, not fatal. Response: `{importedCount, skippedCount, errors[]}`.
* `POST /api/traffic/detect` — runs the heuristic detection pass over unprocessed traffic records.

### Alerts
* `GET /api/anomalies` — paginated alerts. Query params: `page`, `size` (max 100), `severity`, `anomalyType`, `start`, `end` (ISO-8601).
* `GET /api/anomalies/summary` — total/high/medium/low counts and counts by type.
* `GET /api/anomalies/top-sources` — most frequent source IPs, optionally time-bounded.
* `GET /api/anomalies/trend` — hourly alert counts, optionally time-bounded.
* `GET /api/anomalies/ml-data` — alerts encoded as ML feature vectors.
* `GET /api/anomalies/ml-health` — checks whether the FastAPI ML service is reachable.

### ML predictions
* `GET /api/anomalies/predict?algorithm=` — runs prediction using a trained model. `algorithm` is optional: `isolation_forest` (default), `local_outlier_factor`, or `one_class_svm`.
* `POST /api/anomalies/train?algorithm=&contamination=` — trains and saves a model for the given algorithm. `contamination` is optional (default `0.01`, must be in `(0, 0.5]`).
* `GET /anomalies-only` — predicted anomalies only (`prediction == -1`).
* `GET /stats` — anomaly count/percentage summary.

### Evaluation
* `GET /api/evaluation/agreement` — compares heuristic `HIGH` verdicts against ML anomaly flags alert-by-alert; returns `totalCompared`, `bothFlagged`, `heuristicOnly`, `mlOnly`, `neitherFlagged`, `agreementRate`.

### FastAPI ML service (default port 8010)
* `GET /health` — liveness check.
* `GET /models` — lists trained algorithms with contamination, record count, and trained-at timestamp.
* `POST /train` — see above (called internally by the Java service).
* `POST /predict` — see above (called internally by the Java service).

## Algorithm selection

The ML service supports three interchangeable scikit-learn outlier detectors over the identical feature set, each persisted independently as `model_<algorithm>.joblib` with a `.meta.json` sidecar:

| `algorithm` value | Estimator |
|---|---|
| `isolation_forest` (default) | `IsolationForest` |
| `local_outlier_factor` | `LocalOutlierFactor(novelty=True)` |
| `one_class_svm` | `OneClassSVM` |

This is the foundation for a proper algorithm-comparison evaluation: train each algorithm on the same data, then compare via `GET /api/evaluation/agreement` or by inspecting `GET /models`.

---
*Created for network security monitoring.*
