from fastapi.testclient import TestClient
from app.main import app
import app.core.config as config

client = TestClient(app)
FACTS = {"goodsName": "测试商品", "skuCode": "A", "skuName": "蓝色", "language": "中文", "audience": ""}

def test_draft_secret_and_disabled(monkeypatch, tmp_path):
    monkeypatch.setattr(config, "CONFIG_FILE", tmp_path / "missing")
    monkeypatch.setenv("ERP_AGENT_SECRET", "x" * 32)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    assert client.post("/internal/product-draft", json=FACTS).status_code == 403
    response = client.post("/internal/product-draft", json=FACTS, headers={"X-Agent-Secret": "x" * 32})
    assert response.json() == {"modelStatus": "disabled", "draft": None}
    assert client.post("/internal/product-draft", json={**FACTS, "extra": "x"}, headers={"X-Agent-Secret": "x" * 32}).status_code == 422

def test_draft_uses_model_stub(monkeypatch):
    import langchain_openai
    from app.workflows.product import draft_product, ProductFacts, ProductDraft
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    class FakeModel:
        def __init__(self, **kwargs): pass
        def with_structured_output(self, schema, method):
            assert schema is ProductDraft and method == "json_mode"
            return self
        def invoke(self, messages):
            assert "不得虚构" in messages[0].content
            assert "不是商品事实依据" in messages[0].content
            assert "测试商品" in messages[1].content
            assert "美国站" in messages[1].content and "旅行用品" in messages[1].content
            part = {"text":"测试商品", "evidence":[{"field":"goodsName", "quote":"测试商品"}]}
            return ProductDraft(title=part, bullets=[part], description=part, keywords=[part], missing=["材质待核实"])
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    result = draft_product(ProductFacts(**FACTS, site="美国站", confirmedKeywords="旅行用品"))
    assert result["modelStatus"] == "ok" and "材质待核实" in result["draft"]
    assert result["evidence"][0]["evidence"][0]["quote"] == "测试商品"
    assert result["ruleCheck"]["status"] == "not_configured"


def test_draft_rejects_fabricated_evidence(monkeypatch):
    import langchain_openai
    from app.workflows.product import draft_product, ProductFacts, ProductDraft
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    class FakeModel:
        def __init__(self, **kwargs): pass
        def with_structured_output(self, schema, method): return self
        def invoke(self, messages):
            part = {"text":"真皮商品", "evidence":[{"field":"skuName", "quote":"真皮"}]}
            return ProductDraft(title=part, bullets=[part], description=part, keywords=[part], missing=[])
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    assert draft_product(ProductFacts(**FACTS)) == {"modelStatus": "invalid", "draft": None}


def test_configured_rules_report_violations_and_invalid_config_does_not_call_model(monkeypatch):
    import langchain_openai
    import app.workflows.product as workflow
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    calls = []
    class FakeModel:
        def __init__(self, **kwargs): calls.append("model")
        def with_structured_output(self, schema, method): return self
        def invoke(self, messages):
            part = {"text":"测试商品", "evidence":[{"field":"goodsName", "quote":"测试商品"}]}
            return workflow.ProductDraft(title=part, bullets=[part], description=part, keywords=[part], missing=[])
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    rules = {"version":"v1", "source":"内部审核规则", "maxTitleLength":2, "forbiddenTerms":["商品"]}
    monkeypatch.setattr(workflow, "config", lambda: {"listing_rules":{"测试站":rules}})
    result = workflow.draft_product(workflow.ProductFacts(**FACTS, site="测试站"))
    assert result["ruleCheck"]["status"] == "issues"
    assert len(result["ruleCheck"]["issues"]) == 2 and result["ruleCheck"]["version"] == "v1"
    rules["maxTitleLength"] = 20
    rules["forbiddenTerms"] = ["无关词"]
    assert workflow.draft_product(workflow.ProductFacts(**FACTS, site="测试站"))["ruleCheck"]["status"] == "checked"
    rules["maxTitleLength"] = "invalid"
    assert workflow.draft_product(workflow.ProductFacts(**FACTS, site="测试站"))["modelStatus"] == "rules_invalid"
    assert calls == ["model", "model"]


def test_measurements_warn_when_value_or_unit_is_not_in_product_facts(monkeypatch):
    import langchain_openai
    from app.workflows.product import draft_product, ProductFacts, ProductDraft
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    class FakeModel:
        def __init__(self, **kwargs): pass
        def with_structured_output(self, schema, method): return self
        def invoke(self, messages):
            def part(text): return {"text": text, "evidence": [{"field": "goodsName", "quote": "水壶"}]}
            return ProductDraft(title=part("水壶容量1.5 L"), bullets=[part("宽20cm"), part("重量2kg")],
                                description=part("宽20厘米"), keywords=[part("容量2升")], missing=[])
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    facts = ProductFacts(**{**FACTS, "goodsName": "水壶容量1.5L", "skuName": "宽20厘米"})
    result = draft_product(facts)
    assert result["measurementWarnings"] == ["2kg", "2升"]
    facts.skuName = "宽20厘米，高30厘米"
    result = draft_product(facts)
    assert result["measurementWarnings"] == ["2kg", "2升", "原始参数 30 cm 未在草稿保留"]
