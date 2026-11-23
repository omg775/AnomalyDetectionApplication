import os
import tempfile

import pytest
from fastapi.testclient import TestClient

# app.py builds the application at import time: it refuses to start without an API key and creates
# MODEL_DIR. Both must point at test values before any test module imports it.
os.environ.setdefault("ML_API_KEY", "test-api-key")
os.environ.setdefault("MODEL_DIR", tempfile.mkdtemp(prefix="ml-service-test-models-"))

TEST_API_KEY = os.environ["ML_API_KEY"]
AUTH = {"X-API-Key": TEST_API_KEY}


@pytest.fixture
def make_settings(tmp_path):
    from settings import Settings

    def factory(**overrides):
        values = {"api_key": TEST_API_KEY, "allow_insecure_dev": False, "model_dir": tmp_path / "models"}
        values.update(overrides)
        return Settings(**values)

    return factory


@pytest.fixture
def client(make_settings):
    from app import create_app

    return TestClient(create_app(make_settings()))


def alert_record(alert_id, hour=10, day=3, severity=2, anomaly_type=1, ip_hash=None):
    return {
        "id": alert_id,
        "hourOfDay": hour,
        "dayOfWeek": day,
        "severity": severity,
        "anomalyType": anomaly_type,
        "sourceIpHash": ip_hash if ip_hash is not None else 3_232_235_776 % 2_147_483_647 + alert_id,
    }


def training_records(count=80):
    return [alert_record(i, hour=i % 24, day=1 + i % 7, severity=1 + i % 3, anomaly_type=i % 3) for i in range(1, count + 1)]
