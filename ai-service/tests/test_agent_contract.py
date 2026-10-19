"""跨语言契约测试：用 Java 侧真实序列化出的 facts 校验 Python 入口。

payload 由 `MarketingAgentController.Facts` / `RiskAgentController.Facts`
经 `JsonUtils.write` 实际序列化得到（Jackson，NON_NULL 策略），逐字段原样保留，
因此这里同时验证字段名大小写、必需字段齐全、`extra="forbid"` 下不会 422。
"""

import json

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
SECRET = "c" * 32
HEADERS = {"X-Agent-Secret": SECRET}

# 由 Java 序列化得到的原始报文，勿手工改写字段名。
MARKETING_FACTS_JSON = (
    '{"scope":"绑定店铺","total":12,"active":9,"pendingReview":3,'
    '"findings":[{"code":"EXPIRED_ENABLED","level":"HIGH","title":"已过期但仍启用","count":3,'
    '"advice":"核对结束时间，停用或续期后再对外使用。"},'
    '{"code":"EXPIRING_SOON","level":"MEDIUM","title":"7天内到期","count":2,'
    '"advice":"确认是否续期，避免活动中断。"}],"question":"","useModel":false,'
    '"limitation":"仅统计当前授权范围内本系统手动订单折扣规则；广告投放、竞品、广告效果及平台活动数据未接入，规则异常不等于实际资损，处置需人工在营销模块完成；营销建议和文案须人工审核，不自动发布或修改平台设置。"}'
)

RISK_FACTS_JSON = (
    '{"scope":"绑定商户","total":21,"open":13,'
    '"findings":[{"code":"STALE_PENDING","level":"HIGH","title":"待处理超3天","count":4,'
    '"advice":"核对原单与售后进度，及时处理或转人工跟进。"},'
    '{"code":"AMOUNT_INVALID","level":"MEDIUM","title":"退款金额缺失或非正","count":1,'
    '"advice":"核对申请金额，零元或负值不能作为退款依据。"}],"question":"","useModel":false,'
    '"limitation":"仅统计当前授权范围内内部售后记录；记录当前状态与申请金额为原始数据，不等于已确认风险、退款完成或财务结算；跟卖、合规及平台异常订单未接入，处置需人工完成；不执行售后操作，不返回买家个人信息。"}'
)


@pytest.fixture
def model_must_not_be_used(monkeypatch):
    """模型开关打开但未勾选时，任何 ChatOpenAI 构造都视为违约。"""
    import langchain_openai

    class ExplodingModel:
        def __init__(self, **kwargs):
            raise AssertionError("未勾选模型时不应创建模型客户端")

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", ExplodingModel)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)


def test_java_marketing_facts_are_accepted_and_rendered(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    response = client.post("/internal/marketing-analysis", json=json.loads(MARKETING_FACTS_JSON), headers=HEADERS)
    assert response.status_code == 200
    result = response.json()
    assert set(result) == {"scope", "sections", "modelStatus", "explanation"}
    assert result["scope"] == "绑定店铺"
    assert [section["source"] for section in result["sections"]] == [
        "marketing", "marketing-expired_enabled", "marketing-expiring_soon", "notice",
    ]
    assert all(set(section) == {"source", "name", "summary", "limitation"} for section in result["sections"])
    assert [section["name"] for section in result["sections"]] == [
        "折扣规则资料", "已过期但仍启用", "7天内到期", "口径说明",
    ]
    base = result["sections"][0]
    assert "共 12 条" in base["summary"]
    assert base["limitation"] == json.loads(MARKETING_FACTS_JSON)["limitation"], "基础节口径必须原样透传 Java 报文里的 limitation"
    assert "启用 9 条" in base["summary"]
    assert "待审核 3 条" in base["summary"]
    assert result["sections"][1]["summary"] == "已过期但仍启用：3 条。"
    assert result["sections"][2]["summary"] == "7天内到期：2 条。"


def test_java_risk_facts_are_accepted_and_rendered(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    response = client.post("/internal/risk-analysis", json=json.loads(RISK_FACTS_JSON), headers=HEADERS)
    assert response.status_code == 200
    result = response.json()
    assert result["scope"] == "绑定商户"
    assert [section["source"] for section in result["sections"]] == [
        "risk", "risk-stale_pending", "risk-amount_invalid", "notice",
    ]
    base = result["sections"][0]
    assert "共 21 条" in base["summary"]
    assert base["limitation"] == json.loads(RISK_FACTS_JSON)["limitation"], "基础节口径必须原样透传 Java 报文里的 limitation"
    assert "未关闭 13 条" in base["summary"]


def test_java_shape_with_empty_findings_keeps_prompt_section(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    marketing = json.loads(MARKETING_FACTS_JSON)
    marketing["findings"] = []
    sections = client.post("/internal/marketing-analysis", json=marketing, headers=HEADERS).json()["sections"]
    assert [section["source"] for section in sections] == ["marketing", "marketing-risk", "notice"]

    risk = json.loads(RISK_FACTS_JSON)
    risk["findings"] = []
    sections = client.post("/internal/risk-analysis", json=risk, headers=HEADERS).json()["sections"]
    assert [section["source"] for section in sections] == ["risk", "risk-check", "notice"]


def test_java_shape_never_calls_model_when_use_model_false(model_must_not_be_used):
    marketing = client.post("/internal/marketing-analysis", json=json.loads(MARKETING_FACTS_JSON), headers=HEADERS)
    risk = client.post("/internal/risk-analysis", json=json.loads(RISK_FACTS_JSON), headers=HEADERS)
    assert marketing.status_code == 200 and risk.status_code == 200
    assert marketing.json()["modelStatus"] == "not_requested"
    assert marketing.json()["explanation"] is None
    assert risk.json()["modelStatus"] == "not_requested"
    assert risk.json()["explanation"] is None


def test_java_shape_rejects_unknown_field(monkeypatch):
    """Java 侧一旦多传字段就必须 422，说明契约是严格的。"""
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    payload = json.loads(MARKETING_FACTS_JSON)
    payload["scopeType"] = "SHOP"
    assert client.post("/internal/marketing-analysis", json=payload, headers=HEADERS).status_code == 422
    payload = json.loads(RISK_FACTS_JSON)
    payload["findings"][0]["severity"] = "HIGH"
    assert client.post("/internal/risk-analysis", json=payload, headers=HEADERS).status_code == 422
