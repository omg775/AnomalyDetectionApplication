import json
import logging
import os
import platform
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

import joblib
import numpy
import pandas
import sklearn

logger = logging.getLogger("ml-service")


def library_versions() -> Dict[str, str]:
    return {
        "python": platform.python_version(),
        "scikit-learn": sklearn.__version__,
        "numpy": numpy.__version__,
        "pandas": pandas.__version__,
        "joblib": joblib.__version__,
    }


@dataclass(frozen=True)
class StoredModel:
    model: Any
    metadata: Dict[str, Any]


class IncompatibleModelError(Exception):
    """The file on disk is not a model bundle this service can use."""


class ModelStore:
    """
    Keeps trained models on disk under one directory and in memory. Each model is read from disk
    at most once per process; saving replaces both copies. The model and its metadata are written
    together in one bundle file via a temporary file and an atomic rename, so a crash never leaves
    a half-written model behind.
    """

    def __init__(self, directory: Path):
        self._directory = directory
        self._directory.mkdir(parents=True, exist_ok=True)
        self._cache: Dict[str, StoredModel] = {}
        self._lock = threading.Lock()

    def save(self, name: str, model: Any, metadata: Dict[str, Any]) -> None:
        stored = StoredModel(model=model, metadata=metadata)
        self._write_atomically(self._bundle_path(name), lambda f: joblib.dump(
            {"model": model, "metadata": metadata}, f))
        self._write_atomically(self._metadata_path(name), lambda f: f.write(
            json.dumps(metadata, indent=2).encode()))
        with self._lock:
            self._cache[name] = stored

    def get(self, name: str) -> Optional[StoredModel]:
        """Returns the model, loading it from disk the first time. None when it was never trained."""
        with self._lock:
            if name in self._cache:
                return self._cache[name]
            path = self._bundle_path(name)
            if not path.exists():
                return None
            bundle = joblib.load(path)
            if not isinstance(bundle, dict) or "model" not in bundle or "metadata" not in bundle:
                raise IncompatibleModelError(f"{path.name} is not a model bundle")
            stored = StoredModel(model=bundle["model"], metadata=bundle["metadata"])
            self._warn_on_version_drift(name, stored.metadata)
            self._cache[name] = stored
            return stored

    def list_metadata(self) -> List[Dict[str, Any]]:
        result = []
        for path in sorted(self._directory.glob("*.meta.json")):
            with open(path) as f:
                result.append(json.load(f))
        return result

    def _bundle_path(self, name: str) -> Path:
        return self._directory / f"model_{name}.joblib"

    def _metadata_path(self, name: str) -> Path:
        return self._directory / f"model_{name}.meta.json"

    def _write_atomically(self, target: Path, write: Callable[[Any], None]) -> None:
        handle = tempfile.NamedTemporaryFile(dir=self._directory, prefix=f".{target.name}.", suffix=".tmp", delete=False)
        try:
            with handle:
                write(handle)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(handle.name, target)
        except BaseException:
            Path(handle.name).unlink(missing_ok=True)
            raise

    @staticmethod
    def _warn_on_version_drift(name: str, metadata: Dict[str, Any]) -> None:
        trained_with = metadata.get("libraryVersions", {})
        current = library_versions()
        drift = {lib: (trained_with.get(lib), version) for lib, version in current.items()
                 if lib != "python" and trained_with.get(lib) != version}
        if drift:
            logger.warning("Model %s was trained with different library versions %s; retrain it to be safe", name, drift)
