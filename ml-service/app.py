import json
import os
import warnings
from datetime import datetime, timezone
from typing import List, Optional

from urllib3.exceptions import NotOpenSSLWarning

warnings.filterwarnings("ignore", category=NotOpenSSLWarning)

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import pandas as pd
import joblib
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor
from sklearn.svm import OneClassSVM

app = FastAPI()

DEFAULT_ALGORITHM = "isolation_forest"
DEFAULT_CONTAMINATION = 0.01
SUPPORTED_ALGORITHMS = ("isolation_forest", "local_outlier_factor", "one_class_svm")


def model_path(algorithm: str) -> str:
    return f"model_{algorithm}.joblib"


def metadata_path(algorithm: str) -> str:
    return f"model_{algorithm}.meta.json"


def build_model(algorithm: str, contamination: float):
    if algorithm == "isolation_forest":
        return IsolationForest(n_estimators=100, contamination=contamination, random_state=42)
    if algorithm == "local_outlier_factor":
        return LocalOutlierFactor(n_neighbors=20, contamination=contamination, novelty=True)
    if algorithm == "one_class_svm":
        return OneClassSVM(nu=contamination, kernel="rbf", gamma="scale")
    raise ValueError(f"Unsupported algorithm: {algorithm}")


def validate_algorithm(algorithm: str) -> str:
    algorithm = (algorithm or DEFAULT_ALGORITHM).lower()
    if algorithm not in SUPPORTED_ALGORITHMS:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported algorithm '{algorithm}'. Supported: {', '.join(SUPPORTED_ALGORITHMS)}",
        )
    return algorithm


def validate_contamination(contamination: Optional[float]) -> float:
    value = contamination if contamination is not None else DEFAULT_CONTAMINATION
    if not (0.0 < value <= 0.5):
        raise HTTPException(status_code=400, detail="contamination must be in (0, 0.5]")
    return value


class MLDataDTO(BaseModel):
    hourOfDay: int
    dayOfWeek: int
    severity: int
    anomalyType: int
    sourceIpHash: int


class MlPredictionRequest(BaseModel):
    records: List[MLDataDTO]
    algorithm: Optional[str] = None
    contamination: Optional[float] = None


class MlPredictionResult(BaseModel):
    hourOfDay: int
    dayOfWeek: int
    severity: int
    anomalyType: int
    sourceIpHash: int
    isNight: int
    isWeekend: int
    prediction: int
    score: float


class MlPredictionResponse(BaseModel):
    results: List[MlPredictionResult]


def preprocess_data(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["isNight"] = df["hourOfDay"].apply(lambda x: 1 if x < 6 else 0)
    df["isWeekend"] = df["dayOfWeek"].apply(lambda x: 1 if x >= 6 else 0)
    return df


def get_features(df: pd.DataFrame) -> pd.DataFrame:
    features = [
        "hourOfDay",
        "dayOfWeek",
        "severity",
        "anomalyType",
        "sourceIpHash",
        "isNight",
        "isWeekend"
    ]
    return df[features]


@app.get("/")
def home():
    return {"message": "FastAPI ML service is running"}


@app.get("/health")
def health():
    return {"status": "UP"}


@app.get("/models")
def list_models():
    models = []
    for algorithm in SUPPORTED_ALGORITHMS:
        path = metadata_path(algorithm)
        if os.path.exists(path):
            with open(path) as f:
                models.append(json.load(f))
    return {"models": models}


@app.post("/train")
def train(request: MlPredictionRequest):
    df = pd.DataFrame([record.model_dump() for record in request.records])

    if df.empty:
        return {"message": "No data provided for training"}

    algorithm = validate_algorithm(request.algorithm)
    contamination = validate_contamination(request.contamination)

    df = preprocess_data(df)
    X = get_features(df)

    model = build_model(algorithm, contamination)
    model.fit(X)

    joblib.dump(model, model_path(algorithm))

    metadata = {
        "algorithm": algorithm,
        "contamination": contamination,
        "recordsProcessed": len(df),
        "trainedAt": datetime.now(timezone.utc).isoformat(),
    }
    with open(metadata_path(algorithm), "w") as f:
        json.dump(metadata, f)

    return {"message": "Model trained and saved successfully", "records_processed": len(df)}


@app.post("/predict", response_model=MlPredictionResponse)
def predict(request: MlPredictionRequest):
    df = pd.DataFrame([record.model_dump() for record in request.records])

    if df.empty:
        return {"results": []}

    algorithm = validate_algorithm(request.algorithm)

    df = preprocess_data(df)
    X = get_features(df)

    path = model_path(algorithm)
    if not os.path.exists(path):
        raise HTTPException(
            status_code=400,
            detail=f"Model for algorithm '{algorithm}' not trained yet. Call /train first.",
        )

    model = joblib.load(path)

    df["prediction"] = model.predict(X)
    df["score"] = model.decision_function(X)

    results = []
    for _, row in df.iterrows():
        results.append({
            "hourOfDay": int(row["hourOfDay"]),
            "dayOfWeek": int(row["dayOfWeek"]),
            "severity": int(row["severity"]),
            "anomalyType": int(row["anomalyType"]),
            "sourceIpHash": int(row["sourceIpHash"]),
            "isNight": int(row["isNight"]),
            "isWeekend": int(row["isWeekend"]),
            "prediction": int(row["prediction"]),
            "score": float(row["score"])
        })

    return {"results": results}
