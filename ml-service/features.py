from typing import List

import pandas as pd

# Feature order the alert models are trained on. Stored in every model's metadata; a model
# trained with a different list is refused at prediction time.
ALERT_FEATURES: List[str] = [
    "hourOfDay",
    "dayOfWeek",
    "severity",
    "anomalyType",
    "sourceIpHash",
    "isNight",
    "isWeekend",
]


def alert_features(records: pd.DataFrame) -> pd.DataFrame:
    """Adds the derived features (vectorised) and returns the columns in ALERT_FEATURES order."""
    features = records.assign(
        isNight=(records["hourOfDay"] < 6).astype(int),
        isWeekend=(records["dayOfWeek"] >= 6).astype(int),
    )
    return features[ALERT_FEATURES]
