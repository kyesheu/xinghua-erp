from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
SECRET = "x" * 32
HEADERS = {"X-Agent-Secret": SECRET}

# 与 Java 端 AiRiskReadService.LIMITATION 逐字一致（Java 是唯一来源，Python 只透传）。
LIMITATION = (
    "仅统计当前授权范围内内部售后记录；"
    "记录当前状态与申请金额为原始数据，不等于已确认风险、退款完成或财务结算；"
    "跟卖、合规及平台异常订单未接入，处置需人工完成；"
    "不执行售后操作，不返回买家个人信息。"
)


def facts(**overrides):
    payload = {
        "scope": "绑定店铺",
        "total": 20,
        "open": 6,
        "findings": [
            {
                "code": "OPEN_OVERDUE",
                "level": "HIGH",
                "title": "未关闭超过 7 天",
                "count": 4,
                "advice": "请人工联系买家核实并更新售后状态。",
            },
            {
                "code": "AMOUNT_MISMATCH",
                "level": "MEDIUM",
                "title": "申请金额高于订单金额",
                "count": 1,
                "advice": "请人工核对原单金额与申请金额。",
            },
        ],
        "limitation": LIMITATION,
    }
    payload.update(overrides)
    return payload


def test_risk_requires_valid_secret(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    assert client.post("/internal/risk-analysis", json=facts()).status_code == 403
    assert client.post("/internal/risk-analysis", json=facts(), headers={"X-Agent-Secret": "z" * 32}).status_code == 403


def test_risk_sections_follow_fixed_order(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    response = client.post("/internal/risk-analysis", json=facts(), headers=HEADERS)
    assert response.status_code == 200
    result = response.json()
    assert result["scope"] == "绑定店铺"
    assert [section["source"] for section in result["sections"]] == [
        "risk", "risk-open_overdue", "risk-amount_mismatch", "notice",
    ]
    base, first, second, notice = result["sections"]
    # name 由后端给出中文标题，前端不再依赖规则码映射表。
    assert [section["name"] for section in result["sections"]] == [
        "售后记录资料", "未关闭超过 7 天", "申请金额高于订单金额", "口径说明",
    ]
    assert "共 20 条" in base["summary"] and "未关闭 6 条" in base["summary"]
    assert "不等于已确认风险" in base["limitation"]
    assert base["limitation"] == LIMITATION, "基础节口径必须原样透传 Java 传来的 limitation"
    assert first["summary"] == "未关闭超过 7 天：4 条。"
    assert first["limitation"] == "请人工联系买家核实并更新售后状态。"
    assert second["summary"] == "申请金额高于订单金额：1 条。"
    assert "不是模型判断" in notice["summary"] and notice["limitation"] == "结果仅供人工核对。"


def test_risk_without_findings_reports_no_rule_risk(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    sections = client.post("/internal/risk-analysis", json=facts(findings=[]), headers=HEADERS).json()["sections"]
    assert [section["source"] for section in sections] == ["risk", "risk-check", "notice"]
    assert [section["name"] for section in sections] == ["售后记录资料", "未发现异常项", "口径说明"]
    assert "未发现规则化异常项" in sections[1]["summary"]
    assert "未发现不等于不存在风险" in sections[1]["limitation"]


def test_risk_model_is_opt_in_and_limited_to_curated_sections(monkeypatch):
    import langchain_openai
    seen = []

    class FakeModel:
        def __init__(self, **kwargs):
            pass

        def invoke(self, messages):
            seen.extend(messages)
            return SimpleNamespace(content="请优先核对超期未关闭记录。")

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    default_result = client.post("/internal/risk-analysis", json=facts(question="哪些售后最紧急？"), headers=HEADERS).json()
    assert default_result["modelStatus"] == "not_requested"
    assert default_result["explanation"] is None
    assert seen == []
    result = client.post("/internal/risk-analysis", json=facts(question="哪些售后最紧急？", useModel=True), headers=HEADERS).json()
    assert result["modelStatus"] == "ok"
    assert result["explanation"] == "请优先核对超期未关闭记录。"
    assert "哪些售后最紧急？" in seen[1].content
    assert "请人工联系买家核实并更新售后状态。" in seen[1].content
    import json
    payload = json.loads(seen[1].content)
    assert set(payload) == {"question", "scope", "sections"}
    assert payload["scope"]
    assert all(set(section) == {"source", "name", "summary", "limitation"} for section in payload["sections"])


def test_risk_model_disabled_and_failure_keep_rule_sections(monkeypatch):
    import langchain_openai

    class FailedModel:
        def __init__(self, **kwargs):
            pass

        def invoke(self, messages):
            raise RuntimeError("unavailable")

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FailedModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    disabled = client.post("/internal/risk-analysis", json=facts(useModel=True), headers=HEADERS).json()
    assert disabled["modelStatus"] == "disabled" and disabled["explanation"] is None
    assert disabled["sections"][0]["source"] == "risk"
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    failed = client.post("/internal/risk-analysis", json=facts(useModel=True), headers=HEADERS).json()
    assert failed["modelStatus"] == "unavailable" and failed["explanation"] is None
    assert [section["source"] for section in failed["sections"]] == [
        "risk", "risk-open_overdue", "risk-amount_mismatch", "notice",
    ]


def test_risk_rejects_invalid_payload(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    assert client.post("/internal/risk-analysis", json=facts(extra="x"), headers=HEADERS).status_code == 422
    assert client.post("/internal/risk-analysis", json=facts(question="问" * 501), headers=HEADERS).status_code == 422
    bad_count = facts()
    bad_count["findings"][0]["count"] = "many"
    assert client.post("/internal/risk-analysis", json=bad_count, headers=HEADERS).status_code == 422
    assert client.post("/internal/risk-analysis", json=facts(findings=None), headers=HEADERS).status_code == 422
