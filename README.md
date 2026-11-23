# Hybrid Anomaly Detection for Network Traffic

Source code of the bachelor thesis "Hybrid Rule-Based and Machine Learning Anomaly Detection for
Network Traffic Monitoring". Traffic records are imported from CSV files, a heuristic rule layer
creates alerts, and a machine learning service scores those alerts with Isolation Forest, Local
Outlier Factor or One-Class SVM. Everything is offered through a JWT protected REST API.

## Architecture

```
 client (curl, Postman)
        |  HTTPS/JSON, Bearer JWT
        v
 backend/  Spring Boot 4, Java 21, port 8080 ----- JDBC, Flyway -----> PostgreSQL
        |  rules, REST API, security                                  traffic_records, anomaly_alerts
        |  HTTP/JSON, header X-API-Key
        v
 ml-service/  FastAPI, scikit-learn, port 8010  ----> MODEL_DIR (trained models, joblib)
```

| Folder | Content |
|---|---|
| `backend/` | Spring Boot application: REST API, heuristic rules, security, Flyway migrations, tests |
| `ml-service/` | FastAPI service: training and prediction, tests |
| `evaluation/` | `run_evaluation.py` and the results of Chapter 5 (see `evaluation/README.md`) |
| `sample_data/` | `sample_traffic.csv`, 300 records for a first run |
| `postman/` | Postman collection of the endpoints (no credentials inside) |

In the git repository the content of `backend/` is at the repository root.

## Requirements

| Tool | Version | Needed for |
|---|---|---|
| Java (JDK) | 21 | backend |
| Maven | none to install, the wrapper `./mvnw` downloads it | backend |
| Python | 3.12 for `ml-service/`, 3.11 for `evaluation/` | ML service and evaluation (the pinned packages need these versions) |
| PostgreSQL | 14 or newer (local or Supabase) | running the backend |
| Docker | any recent version | only the two PostgreSQL migration tests, and optionally a local database |

The backend tests use an in-memory H2 database and need neither PostgreSQL nor the ML service. The
running application always needs PostgreSQL.

## Environment variables

Copy `.env.example` to `.env`, fill it in, and load it into every terminal you start a service from
with `set -a; . ./.env; set +a`. No secret is stored in the source code.

| Name | Used by | Meaning | Example placeholder | Required |
|---|---|---|---|---|
| `DB_URL` | backend | JDBC URL of PostgreSQL | `jdbc:postgresql://localhost:5432/anomaly` | yes |
| `DB_USERNAME` | backend | database user | `<db-user>` | yes |
| `DB_PASSWORD` | backend | database password | `<db-password>` | yes |
| `JWT_SECRET` | backend | HS256 signing secret, at least 32 bytes | `<output of openssl rand -hex 32>` | yes |
| `ADMIN_USERNAME` | backend | login name of the administrator | `admin` | yes |
| `ADMIN_PASSWORD` | backend | BCrypt hash of the administrator password | `'<bcrypt hash, $2a$...>'` | yes |
| `ML_API_KEY` | both | shared secret sent as `X-API-Key`, same value on both sides | `<output of openssl rand -hex 32>` | yes |
| `ML_SERVICE_BASE_URL` | backend | address of the ML service | `http://127.0.0.1:8010` | no (this default) |
| `CORS_ALLOWED_ORIGINS` | backend | comma-separated browser origins | `http://localhost:5173` | no (empty: no cross-origin access) |
| `CSV_ALLOWED_PROTOCOLS` | backend | protocols accepted by the CSV import | `TCP,UDP,ICMP` | no (this default) |
| `CSV_MAX_ROWS` | backend | maximum data rows per CSV file | `100000` | no (this default) |
| `DB_POOL_MAX_SIZE` | backend | connection pool size | `1` | no (this default) |
| `MODEL_DIR` | ML service | absolute folder for trained models | `/srv/anomaly/models` | no (default `ml-service/models`) |
| `MAX_REQUEST_BYTES` | ML service | largest accepted request body | `52428800` | no (50 MiB) |
| `ALLOW_INSECURE_DEV` | ML service | `true` lets the service start without `ML_API_KEY`; local development only | `false` | no (`false`) |

The backend refuses to start when a required variable is missing, when `JWT_SECRET` is shorter than
32 bytes, or when `ADMIN_PASSWORD` is not a BCrypt hash. The ML service refuses to start without
`ML_API_KEY` unless `ALLOW_INSECURE_DEV=true`.

### Admin password hash

```bash
cd backend
./mvnw -q compile
java -cp "target/classes:$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
  com.om.AnomalyDetectionApplication.util.PasswordHashGenerator '<your-admin-password>'
```

Put the printed hash in single quotes into `ADMIN_PASSWORD` in `.env`. You log in with the plain
password, never with the hash.

## Start-up

Run the commands from the folder that contains this README.

### 1. Database

Any PostgreSQL 14+ database works. A local one with Docker:

```bash
docker run -d --name anomaly-db -p 5432:5432 \
  -e POSTGRES_DB=anomaly -e POSTGRES_USER=anomaly -e POSTGRES_PASSWORD='<db-password>' \
  postgres:17-alpine
```

The backend creates and updates the tables itself with Flyway on start. For an existing database
(for example Supabase, connected through the Session pooler) see
`backend/docs/DATABASE_MIGRATIONS.md`.

### 2. ML service (port 8010)

```bash
cp .env.example .env            # once, then fill in the values
set -a; . ./.env; set +a
cd ml-service
python3.12 -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 127.0.0.1 --port 8010
```

### 3. Backend (port 8080)

In a second terminal:

```bash
set -a; . ./.env; set +a
cd backend
./mvnw spring-boot:run
```

Ready when `curl http://localhost:8080/actuator/health` answers `{"status":"UP",...}`.

### 4. Train once after every deployment

A new ML service has no trained model, and models from older versions of the service are not
loaded. Until a model is trained, prediction and scoring answer with status 503 ("Model not
trained yet"). After the alerts exist (step 2 of the example below), train once:

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/anomalies/train?algorithm=isolation_forest"
```

## Example flow

```bash
B=http://localhost:8080

# 1. log in (plain password, not the hash) and keep the token
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<your-admin-password>"}' $B/api/auth/login \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["token"])')

# 2. import the sample data and run the heuristic detection
curl -H "Authorization: Bearer $TOKEN" -F file=@sample_data/sample_traffic.csv $B/api/traffic/upload
curl -X POST -H "Authorization: Bearer $TOKEN" $B/api/traffic/detect
curl -H "Authorization: Bearer $TOKEN" $B/api/anomalies/summary

# 3. train a model, predict, and store the results with the alerts
curl -X POST -H "Authorization: Bearer $TOKEN" "$B/api/anomalies/train?algorithm=isolation_forest&contamination=0.05"
curl -H "Authorization: Bearer $TOKEN" "$B/api/anomalies/predict?algorithm=isolation_forest"
curl -X POST -H "Authorization: Bearer $TOKEN" "$B/api/anomalies/score?algorithm=isolation_forest"
curl -H "Authorization: Bearer $TOKEN" "$B/api/anomalies?size=5"
```

The same requests are in `postman/AnomalyDetection.postman_collection.json`. Import it into Postman,
set the collection variables `adminUsername` and `adminPassword`, and run "Login" first.

## Tests

```bash
# backend: 191 tests. The 7 PostgreSQL migration tests start a container and are skipped without Docker.
cd backend && ./mvnw clean test

# ML service: 47 tests
cd ml-service
python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
pytest
```

## Main endpoints

All endpoints except login and `/actuator/health` need the header `Authorization: Bearer <token>`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/login` | Obtain a JWT |
| POST | `/api/traffic/upload` | Import a CSV file |
| POST | `/api/traffic/detect` | Run the heuristic detection |
| GET | `/api/anomalies` | Alerts with paging and filters |
| GET | `/api/anomalies/summary` | Alert counts |
| POST | `/api/anomalies/train` | Train a model |
| GET | `/api/anomalies/predict` | Score alerts with the model |
| POST | `/api/anomalies/score` | Store the model results with the alerts |
| GET | `/api/anomalies/ml-health` | State of the ML service |
| GET | `/api/evaluation/agreement` | Agreement of rules and ML |

Further endpoints: `GET /api/anomalies/top-sources`, `GET /api/anomalies/trend`,
`GET /api/anomalies/ml-data`, `GET /anomalies-only`, `GET /stats` and the public
`GET /actuator/health` (with `/liveness` and `/readiness`). The ML service itself offers
`/train`, `/predict` and `/models` (all need `X-API-Key`) and the public `/health`.

The CSV file needs the header
`timestamp,source_ip,destination_ip,protocol,destination_port,bytes_transferred`, with timestamps
like `2026-01-31T13:45:00`.

## Evaluation

`evaluation/run_evaluation.py` produces the tables and figures of Chapter 5, and `evaluation/results/`
holds the results reported in the thesis. The CICIDS2017 dataset is not included; download the CSV
version from https://www.unb.ca/cic/datasets/ids-2017.html. How to rerun it is described in
`evaluation/README.md`.
