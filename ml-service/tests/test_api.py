import pytest

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


def test_train_reports_how_many_records_were_used(client):
    response = client.post("/train", json={"records": training_records(60)}, headers=AUTH)

    assert response.status_code == 200
    assert response.json() == {"message": "Model trained and saved successfully", "records_processed": 60}


def test_train_without_records_trains_nothing(client):
    response = client.post("/train", json={"records": []}, headers=AUTH)

    assert response.json() == {"message": "No data provided for training"}
    assert client.get("/models", headers=AUTH).json() == {"models": []}


@pytest.mark.parametrize("algorithm", ["isolation_forest", "local_outlier_factor", "one_class_svm"])
def test_every_algorithm_trains_and_predicts(client, algorithm):
    client.post("/train", json={"records": training_records(), "algorithm": algorithm}, headers=AUTH)

    response = client.post("/predict", json={"records": [alert_record(1), alert_record(2, hour=3)],
                                             "algorithm": algorithm}, headers=AUTH)

    assert response.status_code == 200
    results = response.json()["results"]
    assert len(results) == 2
    assert all(r["prediction"] in (-1, 1) for r in results)
    assert all(isinstance(r["score"], float) for r in results)


def test_predict_returns_the_input_and_the_derived_features(client):
    client.post("/train", json={"records": training_records()}, headers=AUTH)

    result = client.post("/predict", json={"records": [alert_record(9, hour=2, day=7)]},
                         headers=AUTH).json()["results"][0]

    assert result["hourOfDay"] == 2
    assert result["dayOfWeek"] == 7
    assert result["isNight"] == 1
    assert result["isWeekend"] == 1


def test_contamination_controls_how_many_training_records_are_flagged(client):
    records = training_records(200)
    client.post("/train", json={"records": records, "contamination": 0.1}, headers=AUTH)

    results = client.post("/predict", json={"records": records}, headers=AUTH).json()["results"]

    assert sum(r["prediction"] == -1 for r in results) == 20


def test_predict_with_no_records_returns_an_empty_list(client):
    assert client.post("/predict", json={"records": []}, headers=AUTH).json() == {"results": []}


@pytest.mark.parametrize("path", ["/train", "/predict"])
def test_unknown_algorithm_is_rejected(client, path):
    response = client.post(path, json={"records": training_records(5), "algorithm": "random_forest"}, headers=AUTH)

    assert response.status_code == 400
    assert "Unsupported algorithm 'random_forest'" in response.json()["detail"]


@pytest.mark.parametrize("contamination", [0, -0.1, 0.51, 1])
def test_contamination_outside_the_allowed_range_is_rejected(client, contamination):
    response = client.post("/train", json={"records": training_records(5), "contamination": contamination},
                           headers=AUTH)

    assert response.status_code == 400
    assert response.json()["detail"] == "contamination must be in (0, 0.5]"


def test_predicting_with_an_untrained_model_is_rejected(client):
    client.post("/train", json={"records": training_records(), "algorithm": "isolation_forest"}, headers=AUTH)

    response = client.post("/predict", json={"records": [alert_record(1)], "algorithm": "one_class_svm"}, headers=AUTH)

    assert response.status_code == 400
    assert response.json()["detail"] == "Model for algorithm 'one_class_svm' not trained yet. Call /train first."


def test_malformed_records_are_rejected_with_422(client):
    response = client.post("/predict", json={"records": [{"hourOfDay": "noon"}]}, headers=AUTH)

    assert response.status_code == 422
