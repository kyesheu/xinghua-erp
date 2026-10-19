from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
SECRET = "x" * 32
HEADERS = {"X-Agent-Secret": SECRET}

# 与 Java 端 AiMarketingReadService.LIMITATION 逐字一致（Java 是唯一来源，Python 只透传）。
LIMITATION = (
    "仅统计当前授权范围内本系统手动订单折扣规则；"
    "广告投放、竞品、广告效果及平台活动数据未接入，规则异常不等于实际资损，处置需人工在营销模块完成；"
    "营销建议和文案须人工审核，不自动发布或修改平台设置。"
)


def facts(**overrides):
    payload = {
        "scope": "绑定店铺",
        "total": 12,
        "active": 7,
        "pendingReview": 2,
        "findings": [
            {
                "code": "EXPIRED_ACTIVE",
                "level": "HIGH",
                "title": "已过期仍启用",
                "count": 3,
                "advice": "请在营销模块核对有效期后人工停用。",
            },
            {
                "code": "QUOTA_EXCEEDED",
                "level": "MEDIUM",
                "title": "已用次数超过总次数",
                "count": 2,
                "advice": "请人工核对总次数与已使用次数。",
            },
        ],
        "limitation": LIMITATION,
    }
    payload.update(overrides)
    return payload


def test_marketing_requires_valid_secret(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    assert client.post("/internal/marketing-analysis", json=facts()).status_code == 403
    assert client.post("/internal/marketing-analysis", json=facts(), headers={"X-Agent-Secret": "y" * 32}).status_code == 403


def test_marketing_sections_follow_fixed_order(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    response = client.post("/internal/marketing-analysis", json=facts(), headers=HEADERS)
    assert response.status_code == 200
    result = response.json()
    assert result["scope"] == "绑定店铺"
    assert [section["source"] for section in result["sections"]] == [
        "marketing", "marketing-expired_active", "marketing-quota_exceeded", "notice",
    ]
    base, first, second, notice = result["sections"]
    # name 由后端给出中文标题，前端不再依赖规则码映射表。
    assert [section["name"] for section in result["sections"]] == [
        "折扣规则资料", "已过期仍启用", "已用次数超过总次数", "口径说明",
    ]
    assert "共 12 条" in base["summary"] and "启用 7 条" in base["summary"] and "待审核 2 条" in base["summary"]
    assert "广告投放" in base["limitation"]
    assert base["limitation"] == LIMITATION, "基础节口径必须原样透传 Java 传来的 limitation"
    assert first["summary"] == "已过期仍启用：3 条。"
    assert first["limitation"] == "请在营销模块核对有效期后人工停用。"
    assert second["summary"] == "已用次数超过总次数：2 条。"
    assert notice["source"] == "notice" and "不是模型判断" in notice["summary"]


def test_marketing_without_findings_reports_no_rule_risk(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    sections = client.post("/internal/marketing-analysis", json=facts(findings=[]), headers=HEADERS).json()["sections"]
    assert [section["source"] for section in sections] == ["marketing", "marketing-risk", "notice"]
    assert [section["name"] for section in sections] == ["折扣规则资料", "未发现异常项", "口径说明"]
    assert "未发现规则化异常项" in sections[1]["summary"]
    assert "未发现不等于不存在风险" in sections[1]["limitation"]


def test_marketing_model_is_opt_in_and_limited_to_curated_sections(monkeypatch):
    import langchain_openai
    seen = []

    class FakeModel:
        def __init__(self, **kwargs):
            pass

        def invoke(self, messages):
            seen.extend(messages)
            return SimpleNamespace(content="请人工核对过期规则。")

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    payload = facts(question="哪些规则风险最高？")
    default_result = client.post("/internal/marketing-analysis", json=payload, headers=HEADERS).json()
    assert default_result["modelStatus"] == "not_requested"
    assert default_result["explanation"] is None
    assert seen == []
    result = client.post("/internal/marketing-analysis", json=facts(question="哪些规则风险最高？", useModel=True), headers=HEADERS).json()
    assert result["modelStatus"] == "ok"
    assert result["explanation"] == "请人工核对过期规则。"
    assert "哪些规则风险最高？" in seen[1].content
    assert "请在营销模块核对有效期后人工停用。" in seen[1].content
    import json
    payload = json.loads(seen[1].content)
    assert set(payload) == {"question", "sections"}
    assert all(set(section) == {"source", "name", "summary", "limitation"} for section in payload["sections"])


def test_marketing_model_disabled_and_failure_keep_rule_sections(monkeypatch):
    import langchain_openai

    class FailedModel:
        def __init__(self, **kwargs):
            pass

        def invoke(self, messages):
            raise RuntimeError("unavailable")

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FailedModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    disabled = client.post("/internal/marketing-analysis", json=facts(useModel=True), headers=HEADERS).json()
    assert disabled["modelStatus"] == "disabled" and disabled["explanation"] is None
    assert disabled["sections"][0]["source"] == "marketing"
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    failed = client.post("/internal/marketing-analysis", json=facts(useModel=True), headers=HEADERS).json()
    assert failed["modelStatus"] == "unavailable" and failed["explanation"] is None
    assert [section["source"] for section in failed["sections"]] == [
        "marketing", "marketing-expired_active", "marketing-quota_exceeded", "notice",
    ]


def test_marketing_rejects_invalid_payload(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    assert client.post("/internal/marketing-analysis", json=facts(extra="x"), headers=HEADERS).status_code == 422
    assert client.post("/internal/marketing-analysis", json=facts(question="问" * 501), headers=HEADERS).status_code == 422
    bad_count = facts()
    bad_count["findings"][0]["count"] = 1.5
    assert client.post("/internal/marketing-analysis", json=bad_count, headers=HEADERS).status_code == 422
    assert client.post("/internal/marketing-analysis", json=facts(findings=None), headers=HEADERS).status_code == 422
