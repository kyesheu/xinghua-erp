import json
import os
from pathlib import Path

CONFIG_FILE = Path(os.getenv("ERP_AGENT_CONFIG", str(Path(__file__).resolve().parent.parent.parent / "config.local.json")))


def config() -> dict:
    try:
        with CONFIG_FILE.open(encoding="utf-8-sig") as source:
            value = json.load(source)
        return value if isinstance(value, dict) else {}
    except (OSError, ValueError):
        return {}


def shared_secret() -> str:
    return os.getenv("ERP_AGENT_SECRET") or str(config().get("agent_secret") or "")


def model_settings() -> dict:
    stored = config().get("model") or {}
    if not isinstance(stored, dict):
        stored = {}
    enabled = os.getenv("AI_MODEL_ENABLED")
    return {
        "enabled": enabled.lower() == "true" if enabled is not None else stored.get("enabled") is True,
        "name": os.getenv("AI_MODEL_NAME") or stored.get("name") or "",
        "api_key": os.getenv("AI_MODEL_API_KEY") or stored.get("api_key") or "",
        "base_url": os.getenv("AI_MODEL_BASE_URL") or stored.get("base_url") or "",
    }
