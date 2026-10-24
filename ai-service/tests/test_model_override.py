"""模型凭据透传：页面配置的模型参数经请求头传入，必须压过环境变量与本地配置文件。

Java 侧 `PythonAgentClient` 从 ai_config 表取默认配置后写入 X-Model-* 请求头；
这里验证两件事：取值优先级正确，且请求头能真正穿过 FastAPI 的线程池抵达工作流。
"""

import langchain_openai
import pytest
from fastapi.testclient import TestClient

from app.core.config import apply_model_override, clear_model_override, model_override, model_settings
from app.main import app

client = TestClient(app)
SECRET = "c" * 32

MARKETING_FACTS = {
    "scope": "绑定店铺",
    "total": 1,
    "active": 1,
    "pendingReview": 0,
    "findings": [],
    "question": "",
    "useModel": True,
    "limitation": "仅统计当前授权范围内本系统手动订单折扣规则。",
}


def test_page_config_beats_env_and_file(monkeypatch):
    monkeypatch.setenv("AI_MODEL_API_KEY", "sk-from-env")
    monkeypatch.setenv("AI_MODEL_NAME", "env-model")
    token = apply_model_override("sk-from-page", "https://page.example/v1", "page-model")
    try:
        settings = model_settings()
        assert settings["api_key"] == "sk-from-page"
        assert settings["name"] == "page-model"
        assert settings["base_url"] == "https://page.example/v1"
        assert settings["enabled"] is True, "页面配好密钥即应视为启用"
    finally:
        clear_model_override(token)
    assert model_override() == {}, "请求结束后必须还原上下文"


def test_blank_override_falls_back_to_env(monkeypatch):
    monkeypatch.setenv("AI_MODEL_API_KEY", "sk-from-env")
    monkeypatch.setenv("AI_MODEL_NAME", "env-model")
    token = apply_model_override("", "", "")
    try:
        assert model_override() == {}
        assert model_settings()["api_key"] == "sk-from-env"
    finally:
        clear_model_override(token)


def test_request_header_reaches_workflow_model(monkeypatch):
    """关键用例：contextvar 能否穿过 FastAPI 线程池，决定透传是否真的生效。"""
    captured = {}

    class FakeReply:
        content = "页面配置已生效"

    class FakeModel:
        def __init__(self, **kwargs):
            captured.update(kwargs)

        def invoke(self, messages):
            return FakeReply()

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.delenv("AI_MODEL_API_KEY", raising=False)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")

    response = client.post(
        "/internal/marketing-analysis",
        json=MARKETING_FACTS,
        headers={
            "X-Agent-Secret": SECRET,
            "X-Model-Api-Key": "sk-from-page",
            "X-Model-Base-Url": "https://page.example/v1",
            "X-Model-Name": "page-model",
        },
    )

    assert response.status_code == 200
    assert captured["api_key"] == "sk-from-page"
    assert captured["model"] == "page-model"
    assert captured["base_url"] == "https://page.example/v1"
    assert response.json()["modelStatus"] == "ok"
    assert response.json()["explanation"] == "页面配置已生效"


def test_request_without_headers_keeps_local_fallback(monkeypatch):
    """未配置页面模型时保持原行为：不启用模型，不报错。"""
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.delenv("AI_MODEL_API_KEY", raising=False)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")

    response = client.post(
        "/internal/marketing-analysis",
        json=MARKETING_FACTS,
        headers={"X-Agent-Secret": SECRET},
    )

    assert response.status_code == 200
    assert response.json()["modelStatus"] == "disabled"
