import json
import os
from contextvars import ContextVar
from pathlib import Path

CONFIG_FILE = Path(os.getenv("ERP_AGENT_CONFIG", str(Path(__file__).resolve().parent.parent.parent / "config.local.json")))

# 本次请求携带的模型参数：由 Java 侧从「AI 智能 > 模型配置」页面读取后经请求头传入。
# 用 ContextVar 而不是全局变量，保证并发请求之间互不串号。
_MODEL_OVERRIDE: ContextVar[dict | None] = ContextVar("erp_model_override", default=None)


def config() -> dict:
    try:
        with CONFIG_FILE.open(encoding="utf-8-sig") as source:
            value = json.load(source)
        return value if isinstance(value, dict) else {}
    except (OSError, ValueError):
        return {}


def shared_secret() -> str:
    return os.getenv("ERP_AGENT_SECRET") or str(config().get("agent_secret") or "")


def apply_model_override(api_key: str = "", base_url: str = "", name: str = "") -> object:
    """写入本次请求的模型参数；三者皆空时置空，回退到环境变量与本地配置文件。"""
    values = {"api_key": (api_key or "").strip(), "base_url": (base_url or "").strip(), "name": (name or "").strip()}
    return _MODEL_OVERRIDE.set(values if any(values.values()) else None)


def clear_model_override(token: object) -> None:
    """请求结束后还原上下文，避免参数泄漏到后续请求。"""
    _MODEL_OVERRIDE.reset(token)


def model_override() -> dict:
    return _MODEL_OVERRIDE.get() or {}


def model_settings() -> dict:
    stored = config().get("model") or {}
    if not isinstance(stored, dict):
        stored = {}
    override = model_override()
    enabled = os.getenv("AI_MODEL_ENABLED")
    return {
        # 页面配置了密钥即视为启用：用户不必再为 AI 服务单独设置开关。
        "enabled": True if override.get("api_key") else (enabled.lower() == "true" if enabled is not None else stored.get("enabled") is True),
        "name": override.get("name") or os.getenv("AI_MODEL_NAME") or stored.get("name") or "",
        "api_key": override.get("api_key") or os.getenv("AI_MODEL_API_KEY") or stored.get("api_key") or "",
        "base_url": override.get("base_url") or os.getenv("AI_MODEL_BASE_URL") or stored.get("base_url") or "",
    }
