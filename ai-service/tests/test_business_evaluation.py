import json
from pathlib import Path
from app.evaluate import evaluate, answer_errors


def test_fixed_business_cases_are_evaluated_without_model(monkeypatch):
    import langchain_openai
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", lambda **kwargs: (_ for _ in ()).throw(AssertionError("must stay offline")))
    data = json.loads((Path(__file__).resolve().parents[1] / "evals/business_cases.json").read_text(encoding="utf-8"))
    report = evaluate(data)
    assert report["total"] >= 6 and report["passed"] == report["total"], report


def test_hallucinated_profit_and_missing_caveat_are_detected():
    case = {"answerRequired": ["不等于利润"], "forbidden": ["利润为0"]}
    assert len(answer_errors(case, "利润为0")) == 2
    assert answer_errors(case, "支付退款差额不等于利润，需要结算成本。") == []


def test_partial_sample_cannot_be_treated_as_full_inventory_answer():
    data = json.loads((Path(__file__).resolve().parents[1] / "evals/business_cases.json").read_text(encoding="utf-8"))
    case = next(case for case in data["cases"] if case["id"] == "partial-inventory")
    assert answer_errors(case, "全部150个SKU均已扫描，全仓只有3个缺货。")
    assert answer_errors(case, "仅第一页扫描100个SKU，不能推断全仓只有3个缺货，需要完整范围统计。") == []


def test_missing_or_structured_candidate_does_not_crash_quality_report():
    for answer in (None, {}, [], 5, ""):
        assert answer_errors({}, answer) == ["candidate_answer_invalid"]
