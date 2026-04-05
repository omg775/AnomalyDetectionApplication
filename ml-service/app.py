import requests
import pandas as pd
import matplotlib.pyplot as plt
from sklearn.ensemble import IsolationForest

API_URL = "http://127.0.0.1:8080/api/anomalies/ml-data"


def fetch_data():
    print("Fetching data from API...")
    response = requests.get(API_URL, timeout=10)
    print("Response received:", response.status_code)
    response.raise_for_status()

    data = response.json()
    print("Rows fetched:", len(data))

    return pd.DataFrame(data)


def train_model(df):
    features = [
        "hourOfDay",
        "dayOfWeek",
        "severity",
        "anomalyType",
        "sourceIpHash"
    ]

    X = df[features]

    model = IsolationForest(
        n_estimators=100,
        contamination=0.05,
        random_state=42
    )

    model.fit(X)

    df["prediction"] = model.predict(X)
    df["score"] = model.decision_function(X)

    return df


def visualize(df):
    normal = df[df["prediction"] == 1]
    anomaly = df[df["prediction"] == -1]

    plt.figure(figsize=(10, 6))
    plt.scatter(normal["hourOfDay"], normal["severity"], label="Normal", alpha=0.6)
    plt.scatter(anomaly["hourOfDay"], anomaly["severity"], label="Anomaly", alpha=0.9)
    plt.xlabel("Hour of Day")
    plt.ylabel("Severity")
    plt.title("Isolation Forest Anomaly Detection")
    plt.legend()
    plt.tight_layout()
    plt.show()


def main():
    print("Starting ML script...")

    df = fetch_data()
    print("Data fetched successfully")

    df = train_model(df)
    print("Model trained successfully")

    print("\nTop anomalies:")
    print(df.sort_values("score").head(10))

    df.to_csv("results.csv", index=False)
    print("\nSaved to results.csv")

    visualize(df)


if __name__ == "__main__":
    main()