import logging
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Optional

logger = logging.getLogger("ml-service")

DEFAULT_MODEL_DIR = Path(__file__).resolve().parent / "models"
DEFAULT_MAX_REQUEST_BYTES = 50 * 1024 * 1024


@dataclass(frozen=True)
class Settings:
    api_key: Optional[str]
    allow_insecure_dev: bool
    model_dir: Path = DEFAULT_MODEL_DIR
    max_request_bytes: int = DEFAULT_MAX_REQUEST_BYTES


def load_settings(env: Mapping[str, str] = os.environ) -> Settings:
    """Reads the configuration from the environment and refuses unsafe or ambiguous values."""
    api_key = env.get("ML_API_KEY", "").strip() or None
    allow_insecure_dev = env.get("ALLOW_INSECURE_DEV", "false").strip().lower() == "true"
    if api_key is None and not allow_insecure_dev:
        raise RuntimeError(
            "ML_API_KEY is not set. Set it to the same value as the backend's ML_API_KEY, "
            "or set ALLOW_INSECURE_DEV=true for local development only."
        )
    if api_key is None:
        logger.warning("ML_API_KEY is not set and ALLOW_INSECURE_DEV=true: /train and /predict are unauthenticated")

    model_dir_value = env.get("MODEL_DIR", "").strip()
    model_dir = Path(model_dir_value) if model_dir_value else DEFAULT_MODEL_DIR
    if not model_dir.is_absolute():
        raise RuntimeError(f"MODEL_DIR must be an absolute path, got '{model_dir_value}'")

    max_request_bytes = int(env.get("MAX_REQUEST_BYTES", DEFAULT_MAX_REQUEST_BYTES))
    if max_request_bytes <= 0:
        raise RuntimeError("MAX_REQUEST_BYTES must be a positive number of bytes")

    return Settings(api_key=api_key, allow_insecure_dev=allow_insecure_dev,
                    model_dir=model_dir, max_request_bytes=max_request_bytes)
