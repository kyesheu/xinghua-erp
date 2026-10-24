"""AI 编排迁移到 Python 之后的接口契约。

验证四件事：内部端点必须鉴权；流式端点按行返回事件；工具回调带上发起人令牌；
模型不可用时返回可读事件而不是抛栈。
"""

import json

import pytest
from fastapi.testclient import TestClient

import app.core.config as config
import app.tools.erp as erp
import app.workflows.agent as agent
from app.main import app

client = TestClient(app)
SECRET = "z" * 32


def isolated(monkeypatch, tmp_path):
    monkeypatch.setattr(config, "CONFIG_FILE", tmp_path / "missing.json")
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)


def test_internal_model_endpoints_require_secret(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    for path, body in [
        ("/internal/chat", {"messages": [{"role": "user", "content": "你好"}]}),
        ("/internal/analysis", {"prompt": "分析一下"}),
        ("/internal/brief", {"prompt": "生成简报"}),
        ("/internal/model-ping", {}),
    ]:
        assert client.post(path, json=body).status_code == 403, path
        assert client.post(path, json=body, headers={"X-Agent-Secret": "bad"}).status_code == 403, path


def test_chat_forwards_messages_and_streams_events(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    seen = {}

    def fake_stream(system, messages):
        seen["system"] = system
        seen["messages"] = messages
        yield {"type": "message", "content": "先查订单"}
        yield {"type": "message", "content": "，共 3 单"}
        yield {"type": "done"}

    monkeypatch.setattr("app.main.stream_agent", fake_stream)
    response = client.post(
        "/internal/chat",
        json={"system": "系统提示", "messages": [{"role": "user", "content": "今天卖得怎么样"}]},
        headers={"X-Agent-Secret": SECRET},
    )
    assert response.status_code == 200
    events = [json.loads(line) for line in response.text.strip().splitlines()]
    assert [event["type"] for event in events] == ["message", "message", "done"]
    assert events[0]["content"] == "先查订单"
    assert seen["system"] == "系统提示"
    assert seen["messages"] == [{"role": "user", "content": "今天卖得怎么样"}]


def test_chat_rejects_oversized_or_unknown_role(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    headers = {"X-Agent-Secret": SECRET}
    assert client.post("/internal/chat", json={"messages": []}, headers=headers).status_code == 422
    assert client.post("/internal/chat", json={"messages": [{"role": "system", "content": "x"}]}, headers=headers).status_code == 422


def test_analysis_turns_prompt_into_single_user_message(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    seen = {}

    def fake_stream(system, messages):
        seen["messages"] = messages
        yield {"type": "done"}

    monkeypatch.setattr("app.main.stream_agent", fake_stream)
    response = client.post("/internal/analysis", json={"system": "分析专家", "prompt": "近7天销售"},
                           headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    assert seen["messages"] == [{"role": "user", "content": "近7天销售"}]


def test_brief_returns_text_and_maps_model_errors(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    headers = {"X-Agent-Secret": SECRET}
    monkeypatch.setattr("app.main.run_text", lambda system, prompt: '{"summary":"今日正常"}')
    response = client.post("/internal/brief", json={"system": "只输出JSON", "prompt": "生成简报"}, headers=headers)
    assert response.status_code == 200
    assert response.json() == {"text": '{"summary":"今日正常"}'}

    def missing(system, prompt):
        raise agent.ModelNotConfigured("未配置已启用的模型")

    monkeypatch.setattr("app.main.run_text", missing)
    assert client.post("/internal/brief", json={"prompt": "生成简报"}, headers=headers).status_code == 409

    def broken(system, prompt):
        raise agent.ModelUnavailable("AI 服务调用失败：401")

    monkeypatch.setattr("app.main.run_text", broken)
    assert client.post("/internal/brief", json={"prompt": "生成简报"}, headers=headers).status_code == 502


def test_model_ping_reports_reply_or_failure(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    headers = {"X-Agent-Secret": SECRET}
    monkeypatch.setattr("app.main.ping", lambda: "pong")
    assert client.post("/internal/model-ping", json={}, headers=headers).json() == {"ok": True, "reply": "pong"}

    def broken():
        raise agent.ModelUnavailable("401 Unauthorized")

    monkeypatch.setattr("app.main.ping", broken)
    response = client.post("/internal/model-ping", json={}, headers=headers)
    assert response.status_code == 502
    assert "401" in response.json()["detail"]


def test_query_erp_sends_resource_and_identity(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)
    calls = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps({"text": "| 订单号 | 金额 |"}, ensure_ascii=False).encode()

    def fake_urlopen(request, timeout):
        calls["url"] = request.full_url
        calls["headers"] = {key.lower(): value for key, value in request.header_items()}
        calls["body"] = json.loads(request.data.decode())
        return FakeResponse()

    monkeypatch.setattr(erp.request, "urlopen", fake_urlopen)
    handle = erp.bind_tool_token("42.1700000000000.signature")
    try:
        result = erp.query_erp.invoke({"resource": "order.list", "params": {"days": 7, "limit": 20}})
    finally:
        erp.reset_tool_token(handle)

    assert result == "| 订单号 | 金额 |"
    assert calls["url"].endswith("/internal/ai-tools/query")
    assert calls["body"] == {"resource": "order.list", "params": {"days": 7, "limit": 20}}
    assert calls["headers"]["x-agent-secret"] == SECRET
    assert calls["headers"]["x-tool-token"] == "42.1700000000000.signature"


def test_query_erp_degrades_to_readable_text(monkeypatch, tmp_path):
    isolated(monkeypatch, tmp_path)

    def unreachable(request, timeout):
        raise erp.error.URLError("connection refused")

    monkeypatch.setattr(erp.request, "urlopen", unreachable)
    assert "无法连接 ERP 服务" in erp.query_erp.invoke({"resource": "shop.list", "params": {}})
    assert "resource 不能为空" in erp.query_erp.invoke({"resource": " ", "params": {}})


def test_stream_agent_without_model_emits_error_event(monkeypatch, tmp_path):
    monkeypatch.setattr(config, "CONFIG_FILE", tmp_path / "missing.json")
    monkeypatch.delenv("AI_MODEL_ENABLED", raising=False)
    monkeypatch.delenv("AI_MODEL_API_KEY", raising=False)
    events = list(agent.stream_agent("系统提示", [{"role": "user", "content": "你好"}]))
    assert events[0]["type"] == "error"
    assert "模型" in events[0]["content"]
