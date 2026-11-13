import json
import logging
import os
import secrets
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import List, Mapping, Optional

from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request
from fastapi.security import APIKeyHeader
from pydantic import BaseModel
import pandas as pd
import joblib
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor
from sklearn.svm import OneClassSVM

logger = logging.getLogger("ml-service")

API_KEY_HEADER_NAME = "X-API-Key"
DEFAULT_ALGORITHM = "isolation_forest"
DEFAULT_CONTAMINATION = 0.01
SUPPORTED_ALGORITHMS = ("isolation_forest", "local_outlier_factor", "one_class_svm")


@dataclass(frozen=True)
class Settings:
    api_key: Optional[str]
    allow_insecure_dev: bool


def load_settings(env: Mapping[str, str] = os.environ) -> Settings:
    """Reads the security settings and refuses an unauthenticated service unless explicitly allowed."""
    api_key = env.get("ML_API_KEY", "").strip() or None
    allow_insecure_dev = env.get("ALLOW_INSECURE_DEV", "false").strip().lower() == "true"
    if api_key is None and not allow_insecure_dev:
        raise RuntimeError(
            "ML_API_KEY is not set. Set it to the same value as the backend's ML_API_KEY, "
            "or set ALLOW_INSECURE_DEV=true for local development only."
        )
    if api_key is None:
        logger.warning("ML_API_KEY is not set and ALLOW_INSECURE_DEV=true: /train and /predict are unauthenticated")
    return Settings(api_key=api_key, allow_insecure_dev=allow_insecure_dev)


api_key_header = APIKeyHeader(name=API_KEY_HEADER_NAME, auto_error=False)


def require_api_key(request: Request, provided: Optional[str] = Depends(api_key_header)) -> None:
    expected = request.app.state.settings.api_key
    if expected is None:
        return
    if provided is None or not secrets.compare_digest(provided.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="Invalid or missing API key")


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


public_router = APIRouter()
protected_router = APIRouter(dependencies=[Depends(require_api_key)])


@public_router.get("/")
def home():
    return {"message": "FastAPI ML service is running"}


@public_router.get("/health")
def health():
    return {"status": "UP"}


@protected_router.get("/models")
def list_models():
    models = []
    for algorithm in SUPPORTED_ALGORITHMS:
        path = metadata_path(algorithm)
        if os.path.exists(path):
            with open(path) as f:
                models.append(json.load(f))
    return {"models": models}


@protected_router.post("/train")
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


@protected_router.post("/predict", response_model=MlPredictionResponse)
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


def create_app(settings: Optional[Settings] = None) -> FastAPI:
    """Builds the FastAPI application. Raises RuntimeError when the security settings are unsafe."""
    application = FastAPI(title="Anomaly Detection ML Service")
    application.state.settings = settings if settings is not None else load_settings()
    application.include_router(public_router)
    application.include_router(protected_router)
    return application


app = create_app()
