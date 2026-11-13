import logging
import secrets
from datetime import datetime, timezone
from typing import List, Optional

import pandas as pd
from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.security import APIKeyHeader
from pydantic import BaseModel
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor
from sklearn.svm import OneClassSVM

from features import ALERT_FEATURES, alert_features
from model_store import IncompatibleModelError, ModelStore, library_versions
from settings import Settings, load_settings

logger = logging.getLogger("ml-service")

API_KEY_HEADER_NAME = "X-API-Key"
DEFAULT_ALGORITHM = "isolation_forest"
DEFAULT_CONTAMINATION = 0.01
SUPPORTED_ALGORITHMS = ("isolation_forest", "local_outlier_factor", "one_class_svm")


api_key_header = APIKeyHeader(name=API_KEY_HEADER_NAME, auto_error=False)


def require_api_key(request: Request, provided: Optional[str] = Depends(api_key_header)) -> None:
    expected = request.app.state.settings.api_key
    if expected is None:
        return
    if provided is None or not secrets.compare_digest(provided.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="Invalid or missing API key")


def model_store(request: Request) -> ModelStore:
    return request.app.state.model_store


class RequestSizeLimitMiddleware:
    """Rejects request bodies larger than max_bytes with 413, with or without a Content-Length header."""

    def __init__(self, app, max_bytes: int):
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        declared = dict(scope["headers"]).get(b"content-length")
        if declared is not None and declared.isdigit() and int(declared) > self.max_bytes:
            await self._too_large()(scope, receive, send)
            return

        received = 0

        async def limited_receive():
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_bytes:
                    raise HTTPException(status_code=413, detail=self._message())
            return message

        await self.app(scope, limited_receive, send)

    def _message(self) -> str:
        return f"Request body exceeds the limit of {self.max_bytes} bytes (MAX_REQUEST_BYTES)"

    def _too_large(self) -> JSONResponse:
        return JSONResponse(status_code=413, content={"detail": self._message()})


def build_model(algorithm: str, contamination: float):
    if algorithm == "isolation_forest":
        return IsolationForest(n_estimators=100, contamination=contamination, random_state=42)
    if algorithm == "local_outlier_factor":
        return LocalOutlierFactor(n_neighbors=20, contamination=contamination, novelty=True)
    if algorithm == "one_class_svm":
        return OneClassSVM(nu=contamination, kernel="rbf", gamma="scale")
    raise ValueError(f"Unsupported algorithm: {algorithm}")


def validate_algorithm(algorithm: Optional[str]) -> str:
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


def schema_conflict(algorithm: str) -> HTTPException:
    return HTTPException(
        status_code=409,
        detail=f"The stored model for '{algorithm}' was trained with a different feature schema. Retrain it.",
    )


def load_trained_model(store: ModelStore, name: str, algorithm: str, schema: List[str]):
    """Returns the cached model, or raises 400 when it was never trained and 409 when it does not fit the schema."""
    try:
        stored = store.get(name)
    except IncompatibleModelError:
        raise schema_conflict(algorithm) from None
    if stored is None:
        raise HTTPException(
            status_code=400,
            detail=f"Model for algorithm '{algorithm}' not trained yet. Call /train first.",
        )
    if stored.metadata.get("featureSchema") != schema:
        raise schema_conflict(algorithm)
    return stored.model


def train_and_store(store: ModelStore, name: str, algorithm: str, contamination: float,
                    features: pd.DataFrame, schema: List[str]) -> None:
    model = build_model(algorithm, contamination)
    model.fit(features)
    store.save(name, model, {
        "name": name,
        "algorithm": algorithm,
        "contamination": contamination,
        "recordsProcessed": len(features),
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "featureSchema": schema,
        "libraryVersions": library_versions(),
    })


class MLDataDTO(BaseModel):
    id: Optional[int] = None
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
    id: Optional[int] = None
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


def records_frame(records: List[BaseModel]) -> pd.DataFrame:
    return pd.DataFrame([record.model_dump() for record in records])


public_router = APIRouter()
protected_router = APIRouter(dependencies=[Depends(require_api_key)])


@public_router.get("/")
def home():
    return {"message": "FastAPI ML service is running"}


@public_router.get("/health")
def health():
    return {"status": "UP"}


@protected_router.get("/models")
def list_models(store: ModelStore = Depends(model_store)):
    return {"models": store.list_metadata()}


@protected_router.post("/train")
def train(request: MlPredictionRequest, store: ModelStore = Depends(model_store)):
    if not request.records:
        return {"message": "No data provided for training"}

    algorithm = validate_algorithm(request.algorithm)
    contamination = validate_contamination(request.contamination)

    features = alert_features(records_frame(request.records))
    train_and_store(store, algorithm, algorithm, contamination, features, ALERT_FEATURES)

    return {"message": "Model trained and saved successfully", "records_processed": len(features)}


@protected_router.post("/predict", response_model=MlPredictionResponse)
def predict(request: MlPredictionRequest, store: ModelStore = Depends(model_store)):
    if not request.records:
        return {"results": []}

    algorithm = validate_algorithm(request.algorithm)
    model = load_trained_model(store, algorithm, algorithm, ALERT_FEATURES)

    frame = records_frame(request.records)
    features = alert_features(frame)
    results = features.assign(
        id=frame["id"].astype(object).where(frame["id"].notna(), None),
        prediction=model.predict(features).astype(int),
        score=model.decision_function(features).astype(float),
    )
    return {"results": results.to_dict(orient="records")}


def create_app(settings: Optional[Settings] = None) -> FastAPI:
    """Builds the FastAPI application. Raises RuntimeError when the configuration is unsafe."""
    settings = settings if settings is not None else load_settings()
    application = FastAPI(title="Anomaly Detection ML Service")
    application.state.settings = settings
    application.state.model_store = ModelStore(settings.model_dir)
    application.add_middleware(RequestSizeLimitMiddleware, max_bytes=settings.max_request_bytes)
    application.include_router(public_router)
    application.include_router(protected_router)
    return application


app = create_app()
