import warnings
from urllib3.exceptions import NotOpenSSLWarning

warnings.filterwarnings("ignore", category=NotOpenSSLWarning)

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
import pandas as pd
from sklearn.ensemble import IsolationForest
import joblib
import os

app = FastAPI()

MODEL_FILE = "model.joblib"


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


@app.post("/train")
def train(request: MlPredictionRequest):
    df = pd.DataFrame([record.model_dump() for record in request.records])
    
    if df.empty:
        return {"message": "No data provided for training"}

    df = preprocess_data(df)
    X = get_features(df)

    model = IsolationForest(
        n_estimators=100,
        contamination=0.01,
        random_state=42
    )

    model.fit(X)
    
    # Save the trained model
    joblib.dump(model, MODEL_FILE)
    
    return {"message": "Model trained and saved successfully", "records_processed": len(df)}


@app.post("/predict", response_model=MlPredictionResponse)
def predict(request: MlPredictionRequest):
    df = pd.DataFrame([record.model_dump() for record in request.records])

    if df.empty:
        return {"results": []}

    df = preprocess_data(df)
    X = get_features(df)
    
    # Load the trained model
    if not os.path.exists(MODEL_FILE):
        raise HTTPException(status_code=400, detail="Model not trained yet. Call /train first.")
        
    model = joblib.load(MODEL_FILE)

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