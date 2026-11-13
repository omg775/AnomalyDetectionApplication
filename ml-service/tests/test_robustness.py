import json

import joblib
import pytest
from fastapi.testclient import TestClient

import model_store
from app import create_app
from features import ALERT_FEATURES
from settings import DEFAULT_MODEL_DIR, load_settings
from tests.conftest import AUTH, TEST_API_KEY, alert_record, training_records


def train(client, algorithm="isolation_forest"):
    response = client.post("/train", json={"records": training_records(), "algorithm": algorithm}, headers=AUTH)
    assert response.status_code == 200
    return response


def predict(client, records, algorithm="isolation_forest"):
    return client.post("/predict", json={"records": records, "algorithm": algorithm}, headers=AUTH)


# --- model cache ------------------------------------------------------------------------------

def count_loads(monkeypatch):
    calls = {"n": 0}
    real_load = joblib.load

    def counting_load(*args, **kwargs):
        calls["n"] += 1
        return real_load(*args, **kwargs)

    monkeypatch.setattr(model_store.joblib, "load", counting_load)
    return calls


def test_after_training_predictions_never_read_the_disk(client, monkeypatch):
    train(client)
    loads = count_loads(monkeypatch)

    for _ in range(3):
        assert predict(client, [alert_record(1)]).status_code == 200

    assert loads["n"] == 0


def test_after_a_restart_the_model_is_read_from_disk_once(make_settings, monkeypatch):
    train(TestClient(create_app(make_settings())))
    restarted = TestClient(create_app(make_settings()))
    loads = count_loads(monkeypatch)

    for _ in range(3):
        assert predict(restarted, [alert_record(1)]).status_code == 200

    assert loads["n"] == 1


def test_retraining_replaces_the_cached_model(client):
    train(client)
    first = predict(client, [alert_record(1)]).json()["results"][0]["score"]
    client.post("/train", json={"records": training_records(40), "contamination": 0.3}, headers=AUTH)

    second = predict(client, [alert_record(1)]).json()["results"][0]["score"]

    assert first != second


# --- atomic writes and metadata ---------------------------------------------------------------

def test_metadata_records_the_feature_schema_and_library_versions(client, make_settings):
    train(client)

    metadata = client.get("/models", headers=AUTH).json()["models"][0]

    assert metadata["featureSchema"] == ALERT_FEATURES
    assert set(metadata["libraryVersions"]) == {"python", "scikit-learn", "numpy", "pandas", "joblib"}
    assert metadata["recordsProcessed"] == 80


def test_a_failed_save_leaves_the_previous_model_and_no_temporary_files(make_settings, monkeypatch):
    settings = make_settings()
    client = TestClient(create_app(settings), raise_server_exceptions=False)
    train(client)
    before = predict(client, [alert_record(1)]).json()

    def failing_dump(obj, f, *args, **kwargs):
        f.write(b"partial")
        raise OSError("disk full")

    monkeypatch.setattr(model_store.joblib, "dump", failing_dump)
    assert client.post("/train", json={"records": training_records(30)}, headers=AUTH).status_code == 500
    monkeypatch.undo()

    restarted = TestClient(create_app(settings))
    assert predict(restarted, [alert_record(1)]).json() == before
    assert not list(settings.model_dir.glob("*.tmp"))


def test_a_model_trained_with_another_feature_schema_is_refused(client, make_settings):
    train(client)
    model_dir = make_settings().model_dir
    bundle = joblib.load(model_dir / "model_isolation_forest.joblib")
    bundle["metadata"]["featureSchema"] = ["hourOfDay", "dayOfWeek"]
    joblib.dump(bundle, model_dir / "model_isolation_forest.joblib")

    response = predict(TestClient(create_app(make_settings())), [alert_record(1)])

    assert response.status_code == 409
    assert "different feature schema" in response.json()["detail"]


def test_a_model_file_from_the_old_format_is_refused(make_settings):
    settings = make_settings()
    settings.model_dir.mkdir(parents=True)
    joblib.dump({"not": "a bundle"}, settings.model_dir / "model_isolation_forest.joblib")

    response = predict(TestClient(create_app(settings)), [alert_record(1)])

    assert response.status_code == 409


# --- MODEL_DIR and request size -------------------------------------------------------------

def test_model_dir_defaults_to_an_absolute_path():
    settings = load_settings({"ML_API_KEY": "k"})

    assert settings.model_dir == DEFAULT_MODEL_DIR
    assert settings.model_dir.is_absolute()


def test_relative_model_dir_is_refused():
    with pytest.raises(RuntimeError, match="MODEL_DIR must be an absolute path"):
        load_settings({"ML_API_KEY": "k", "MODEL_DIR": "models"})


def test_model_dir_is_read_from_the_environment(tmp_path):
    assert load_settings({"ML_API_KEY": "k", "MODEL_DIR": str(tmp_path)}).model_dir == tmp_path


def test_body_with_a_too_large_content_length_is_rejected(make_settings):
    client = TestClient(create_app(make_settings(max_request_bytes=1000)))

    response = client.post("/predict", content=b"x" * 1001, headers={**AUTH, "Content-Type": "application/json"})

    assert response.status_code == 413
    assert "MAX_REQUEST_BYTES" in response.json()["detail"]


def test_streamed_body_without_content_length_is_rejected(make_settings):
    client = TestClient(create_app(make_settings(max_request_bytes=1000)))
    body = json.dumps({"records": training_records(50)}).encode()

    def chunks():
        for start in range(0, len(body), 256):
            yield body[start:start + 256]

    response = client.post("/train", content=chunks(), headers={**AUTH, "Content-Type": "application/json"})

    assert response.status_code == 413


def test_body_under_the_limit_is_accepted(make_settings):
    client = TestClient(create_app(make_settings(max_request_bytes=100_000)))

    assert client.post("/train", json={"records": training_records(50)}, headers=AUTH).status_code == 200
