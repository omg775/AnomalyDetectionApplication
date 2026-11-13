from tests.conftest import AUTH, alert_record, training_records


def test_predict_returns_the_alert_id_of_every_record(client):
    client.post("/train", json={"records": training_records()}, headers=AUTH)

    response = client.post("/predict", json={"records": [alert_record(501), alert_record(77)]}, headers=AUTH)

    assert response.status_code == 200
    assert [r["id"] for r in response.json()["results"]] == [501, 77]


def test_id_is_optional(client):
    client.post("/train", json={"records": training_records()}, headers=AUTH)
    record = {k: v for k, v in alert_record(1).items() if k != "id"}

    response = client.post("/predict", json={"records": [record]}, headers=AUTH)

    assert response.json()["results"][0]["id"] is None
