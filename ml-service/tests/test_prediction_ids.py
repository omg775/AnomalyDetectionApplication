from fastapi.testclient import TestClient

from app import Settings, create_app
from tests.conftest import TEST_API_KEY

HEADERS = {"X-API-Key": TEST_API_KEY}


def record(alert_id, hour):
    return {"id": alert_id, "hourOfDay": hour, "dayOfWeek": 3, "severity": 2, "anomalyType": 1, "sourceIpHash": 1000 + hour}


def test_predict_returns_the_alert_id_of_every_record(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    client = TestClient(create_app(Settings(api_key=TEST_API_KEY, allow_insecure_dev=False)))
    training = [record(i, i % 24) for i in range(1, 60)]
    assert client.post("/train", json={"records": training}, headers=HEADERS).status_code == 200

    response = client.post("/predict", json={"records": [record(501, 3), record(77, 14)]}, headers=HEADERS)

    assert response.status_code == 200
    assert [r["id"] for r in response.json()["results"]] == [501, 77]


def test_id_is_optional(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    client = TestClient(create_app(Settings(api_key=TEST_API_KEY, allow_insecure_dev=False)))
    without_ids = [{k: v for k, v in record(i, i % 24).items() if k != "id"} for i in range(1, 60)]
    client.post("/train", json={"records": without_ids}, headers=HEADERS)

    response = client.post("/predict", json={"records": without_ids[:1]}, headers=HEADERS)

    assert response.json()["results"][0]["id"] is None
