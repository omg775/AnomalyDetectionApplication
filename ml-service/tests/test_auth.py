import os
import subprocess
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import Settings, create_app, load_settings
from tests.conftest import TEST_API_KEY

SERVICE_DIR = Path(__file__).resolve().parent.parent
EMPTY_REQUEST = {"records": []}


@pytest.fixture
def client():
    return TestClient(create_app(Settings(api_key=TEST_API_KEY, allow_insecure_dev=False)))


@pytest.fixture
def insecure_client():
    return TestClient(create_app(Settings(api_key=None, allow_insecure_dev=True)))


def test_missing_key_without_dev_flag_is_refused():
    with pytest.raises(RuntimeError, match="ML_API_KEY is not set"):
        load_settings({})


def test_blank_key_counts_as_missing():
    with pytest.raises(RuntimeError):
        load_settings({"ML_API_KEY": "   "})


def test_missing_key_is_allowed_only_with_the_dev_flag():
    settings = load_settings({"ALLOW_INSECURE_DEV": "true"})

    assert settings.api_key is None
    assert settings.allow_insecure_dev is True


def test_key_is_read_from_the_environment():
    assert load_settings({"ML_API_KEY": "abc"}).api_key == "abc"


def test_service_refuses_to_start_without_a_key():
    env = {k: v for k, v in os.environ.items() if k not in ("ML_API_KEY", "ALLOW_INSECURE_DEV")}

    result = subprocess.run([sys.executable, "-c", "import app"], cwd=SERVICE_DIR, env=env,
                            capture_output=True, text=True)

    assert result.returncode != 0
    assert "ML_API_KEY is not set" in result.stderr


def test_health_is_open(client):
    assert client.get("/health").status_code == 200


@pytest.mark.parametrize("method,path", [("post", "/train"), ("post", "/predict"), ("get", "/models")])
def test_protected_endpoints_reject_a_missing_key(client, method, path):
    response = client.request(method, path, json=EMPTY_REQUEST)

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or missing API key"


@pytest.mark.parametrize("method,path", [("post", "/train"), ("post", "/predict"), ("get", "/models")])
def test_protected_endpoints_reject_a_wrong_key(client, method, path):
    response = client.request(method, path, json=EMPTY_REQUEST, headers={"X-API-Key": "wrong"})

    assert response.status_code == 401


@pytest.mark.parametrize("method,path", [("post", "/train"), ("post", "/predict"), ("get", "/models")])
def test_protected_endpoints_accept_the_right_key(client, method, path):
    response = client.request(method, path, json=EMPTY_REQUEST, headers={"X-API-Key": TEST_API_KEY})

    assert response.status_code == 200


def test_insecure_dev_mode_needs_no_key(insecure_client):
    assert insecure_client.post("/predict", json=EMPTY_REQUEST).status_code == 200
