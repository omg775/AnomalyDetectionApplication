import warnings
from urllib3.exceptions import NotOpenSSLWarning

warnings.filterwarnings("ignore", category=NotOpenSSLWarning)

from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
import pandas as pd
from sklearn.ensemble import IsolationForest

app = FastAPI()


class MLDataDTO(BaseModel):
    hourOfDay: int
    dayOfWeek: int
    severity: int
    anomalyType: int
    sourceIpHash: int


class MlPredictionRequest(BaseModel):
    records: List[MLDataDTO]


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


def train_model(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()

    df["isNight"] = df["hourOfDay"].apply(lambda x: 1 if x < 6 else 0)
    df["isWeekend"] = df["dayOfWeek"].apply(lambda x: 1 if x >= 6 else 0)

    features = [
        "hourOfDay",
        "dayOfWeek",
        "severity",
        "anomalyType",
        "sourceIpHash",
        "isNight",
        "isWeekend"
    ]

    X = df[features]

    model = IsolationForest(
        n_estimators=100,
        contamination=0.01,
        random_state=42
    )

    model.fit(X)

    df["prediction"] = model.predict(X)
    df["score"] = model.decision_function(X)

    return df


@app.get("/")
def home():
    return {"message": "FastAPI ML service is running"}


@app.post("/predict", response_model=MlPredictionResponse)
def predict(request: MlPredictionRequest):
    df = pd.DataFrame([record.model_dump() for record in request.records])

    if df.empty:
        return {"results": []}

    df = train_model(df)

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