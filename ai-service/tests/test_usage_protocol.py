import json
from types import SimpleNamespace
from fastapi.testclient import TestClient
from app.main import app
from app.core.model_policy import current_policy, UsageCallback


def record_usage():
    policy=current_policy();policy.begin(10)
    UsageCallback(policy).on_llm_end(SimpleNamespace(llm_output={"token_usage":{"prompt_tokens":11,"completion_tokens":5}},generations=[]))


def test_usage_header_does_not_change_business_json(monkeypatch):
    import app.main as main
    monkeypatch.setenv("ERP_AGENT_SECRET","x"*32)
    monkeypatch.setattr(main,"run_text",lambda *args: record_usage() or "ok")
    response=TestClient(app).post("/internal/brief",json={"prompt":"hi"},headers={"X-Agent-Secret":"x"*32,"X-AI-Usage":"1"})
    assert response.json()=={"text":"ok"}
    assert json.loads(response.headers["x-ai-usage"])["inputTokens"]==11


def test_stream_opt_in_reports_usage_after_done(monkeypatch):
    import app.main as main
    monkeypatch.setenv("ERP_AGENT_SECRET","x"*32)
    def fake(*args):
        record_usage()
        yield {"type":"message","content":"ok"}
        yield {"type":"done","content":""}
    monkeypatch.setattr(main,"stream_agent",fake)
    response=TestClient(app).post("/internal/chat",json={"messages":[{"role":"user","content":"hi"}]},headers={"X-Agent-Secret":"x"*32,"X-AI-Usage":"1"})
    events=[json.loads(line) for line in response.text.splitlines()]
    assert events[-1]["type"]=="usage"
    assert json.loads(events[-1]["content"])=={"calls":1,"inputTokens":11,"outputTokens":5,"known":True}
