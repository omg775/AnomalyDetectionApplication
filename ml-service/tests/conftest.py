import os

# app.py builds the application at import time and refuses to start without an API key,
# so the key must exist before any test module imports it.
os.environ.setdefault("ML_API_KEY", "test-api-key")

TEST_API_KEY = os.environ["ML_API_KEY"]
