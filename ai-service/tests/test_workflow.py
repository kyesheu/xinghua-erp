import pytest
import app.core.config as app_config
from fastapi.testclient import TestClient
from app.main import app

@pytest.fixture(autouse=True)
def isolated_config(monkeypatch, tmp_path):
    monkeypatch.setattr(app_config, "CONFIG_FILE", tmp_path / "config.local.json")


client = TestClient(app)
SECRET = "x" * 32
facts = {
    "skuId": 5, "warehouseId": 1, "days": 30, "retrievedAt": "2026-11-02T10:00:00",
    "inventory": [{"warehouseId": 1, "merchantId": 7, "shopId": 0, "quantity": 10,
                   "availableQuantity": 8, "lockedQuantity": 2, "stockStatus": 1,
                   "batchCount": 1, "batchQuantity": 10, "invalidBatches": 0}],
    "sales": {"skuId": 5, "quantity": 30, "start": "2026-10-03T00:00:00", "endExclusive": "2026-11-02T00:00:00",
              "scope": "商户", "limitation": "未扣退款"},
    "purchase": None, "inventoryLimitation": "空记录不代表零库存"
}


@pytest.mark.parametrize("changes", [
    {"batchCount": 0, "batchQuantity": 0},
    {"batchCount": None, "batchQuantity": None, "invalidBatches": None},
    {"batchQuantity": 9},
    {"invalidBatches": 1},
])
def test_batch_integrity_blocks_replenishment_estimate(changes):
    from app.schemas.business import BusinessFacts
    from app.workflows.business import run_business
    data = {**facts, "inventory": [{**facts["inventory"][0], **changes}], "useModel": False,
            "assumptions": {"dailyDemand": "1", "targetDays": 20, "confirmedIncoming": 0}}
    result = run_business(BusinessFacts.model_validate(data))
    assert result["suggestedQuantity"] is None
    assert any("批次" in issue for issue in result["inventoryChecks"][0]["issues"])


def test_requires_configured_secret(monkeypatch):
    monkeypatch.delenv("ERP_AGENT_SECRET", raising=False)
    assert client.post("/internal/inventory-analysis", json=facts, headers={"X-Agent-Secret": SECRET}).status_code == 403
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    assert client.post("/internal/inventory-analysis", json=facts).status_code == 403


def test_real_data_workflow_has_no_fabricated_replenishment(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.delenv("AI_MODEL_ENABLED", raising=False)
    response = client.post("/internal/inventory-analysis", json=facts, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    result = response.json()
    assert result["facts"]["inventory"][0]["availableQuantity"] == 8
    assert result["facts"]["sales"]["quantity"] == 30
    assert result["suggestedQuantity"] is None
    assert result["modelStatus"] == "disabled"
    assert any("采购" in w for w in result["warnings"])


def test_missing_inventory_is_not_zero(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    data = {**facts, "inventory": []}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert any("不代表实际库存为零" in w for w in result["warnings"])
    assert result["suggestedQuantity"] is None


def test_missing_sales_and_invalid_period(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    data = {**facts, "sales": None}
    response = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    assert any("销售" in w for w in response.json()["warnings"])
    assert client.post("/internal/inventory-analysis", json={**data, "days": 8}, headers={"X-Agent-Secret": SECRET}).status_code == 422


def test_estimate_uses_one_real_row_and_confirmed_inputs(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    data = {**facts, "assumptions": {"dailyDemand": "1.5", "targetDays": 10, "confirmedIncoming": 2}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] == 5
    assert result["facts"]["assumptions"]["dailyDemand"] == "1.5"
    data["inventory"] = facts["inventory"] * 2
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] is None
    data["inventory"] = [{**facts["inventory"][0], "stockStatus": 2}]
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] is None


@pytest.mark.parametrize("changes", [{"quantity": None}, {"lockedQuantity": -1}, {"quantity": 11}, {"lockedQuantity": None}])
def test_inconsistent_stock_cannot_produce_purchase_quantity(monkeypatch, changes):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "inventory": [{**facts["inventory"][0], **changes}],
            "assumptions": {"dailyDemand": "2", "targetDays": 10, "confirmedIncoming": 0}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] is None
    assert any("不一致" in warning for warning in result["warnings"])


def test_model_receives_question_but_does_not_change_rule_result(monkeypatch):
    from types import SimpleNamespace
    import langchain_openai
    seen = []
    class FakeModel:
        def __init__(self, **kwargs): pass
        def invoke(self, messages):
            seen.extend(messages)
            return SimpleNamespace(content="请核对在途数量。")
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    monkeypatch.setenv("AI_MODEL_NAME", "test")
    monkeypatch.setenv("AI_MODEL_API_KEY", "test")
    monkeypatch.setenv("AI_MODEL_BASE_URL", "http://127.0.0.1")
    data = {**facts, "question": "该商品需要关注什么？", "assumptions": {"dailyDemand": "1.5", "targetDays": 10, "confirmedIncoming": 2}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["modelStatus"] == "ok"
    assert result["suggestedQuantity"] == 5
    assert "该商品需要关注什么" in seen[1].content


def test_explicit_rule_only_analysis_does_not_call_model(monkeypatch):
    import langchain_openai
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", lambda **kwargs: pytest.fail("model must not be called"))
    result = client.post("/internal/inventory-analysis", json={**facts, "useModel": False}, headers={"X-Agent-Secret": SECRET}).json()
    assert result["modelStatus"] == "not_requested"


def test_shared_secret_can_be_loaded_from_local_config(monkeypatch):
    import json
    monkeypatch.delenv("ERP_AGENT_SECRET", raising=False)
    app_config.CONFIG_FILE.write_text(json.dumps({"agent_secret": SECRET, "model": {"enabled": False}}), encoding="utf-8")
    response = client.post("/internal/inventory-analysis", json=facts, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    assert response.json()["modelStatus"] == "disabled"


def test_purchase_without_task_is_preserved_and_not_treated_as_in_transit(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "purchase": {"records": [], "withoutTask": [{"orderNum": "TEST", "quantity": 8}],
                                   "scope": "商户", "limitation": "采购数量不代表在途"}}
    response = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    result = response.json()
    assert result["facts"]["purchase"]["withoutTask"][0]["quantity"] == 8
    assert result["suggestedQuantity"] is None
    assert "采购数量不代表在途" in result["warnings"]


def test_coverage_and_warehouse_checks_are_deterministic(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "assumptions": {"dailyDemand": "3", "targetDays": 10, "confirmedIncoming": 2}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] == 20
    assert result["coverage"]["currentCoverageDays"] == "2.66"
    assert result["coverage"]["targetDemand"] == "30"
    assert result["inventoryChecks"][0]["issues"] == []
    data["inventory"] = facts["inventory"] * 2
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["coverage"] is None
    assert all(row["availableQuantity"] is None for row in result["inventoryChecks"])
    assert all(row["issues"] for row in result["inventoryChecks"])


def test_incomplete_inventory_owner_prevents_estimate(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "inventory": [{**facts["inventory"][0], "merchantId": None}],
            "assumptions": {"dailyDemand": "3", "targetDays": 10, "confirmedIncoming": 2}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] is None
    assert result["coverage"] is None
    assert result["inventoryChecks"][0]["status"] == "待核实"


def test_purchase_reconciliation_flags_duplicates_and_invalid_quantities(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    task = {"sourceItemId": 4, "sourceNo": "P1", "warehouseId": 1,
            "quantity": 10, "inQuantity": 4, "itemStatus": 1}
    data = {**facts, "purchase": {"records": [task, task, {**task, "sourceItemId": 5, "inQuantity": -1}],
                                    "withoutTask": [], "scope": "总部", "limitation": "需核实在途"}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert len(result["purchaseChecks"]) == 3
    assert all(row["status"] == "待核实" for row in result["purchaseChecks"])
    assert result["purchaseChecks"][2]["remainingQuantity"] is None
    assert result["suggestedQuantity"] is None


@pytest.mark.parametrize("arrival,expected,effective", [(5, 2, 20), (11, 22, 0)])
def test_arrivals_outside_horizon_do_not_reduce_replenishment(monkeypatch, arrival, expected, effective):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "assumptions": {"dailyDemand": "3", "targetDays": 10, "confirmedIncoming": 20,
                                     "incomingArrivalDays": arrival, "leadTimeDays": 4}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] == expected
    assert result["coverage"]["effectiveIncoming"] == effective
    assert len(result["coverage"]["timingRisks"]) == 2


def test_exact_arrival_and_lead_time_boundary_has_no_gap(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "assumptions": {"dailyDemand": "2", "targetDays": 10, "confirmedIncoming": 20,
                                     "incomingArrivalDays": 4, "leadTimeDays": 4}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] == 0
    assert result["coverage"]["timingRisks"] == []


@pytest.mark.parametrize("arrival,first_gap", [(3, None), (5, "4.00")])
def test_multiple_arrivals_detect_intermediate_shortage_despite_sufficient_total(monkeypatch, arrival, first_gap):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    data = {**facts, "assumptions": {"dailyDemand": "2", "targetDays": 10, "confirmedIncoming": 25,
            "incomingBatches": [{"quantity": 20, "arrivalDays": arrival}, {"quantity": 5, "arrivalDays": 11}]}}
    result = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).json()
    assert result["suggestedQuantity"] == 0
    assert result["coverage"]["effectiveIncoming"] == 20
    assert result["coverage"]["arrivalProjection"]["firstShortageDay"] == first_gap
    assert result["coverage"]["arrivalProjection"]["endBalance"] == "8"


def test_batch_totals_and_single_batch_mode_cannot_conflict(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    assumptions = {"dailyDemand": "2", "targetDays": 10, "confirmedIncoming": 10,
                   "incomingBatches": [{"quantity": 9, "arrivalDays": 1}]}
    assert client.post("/internal/inventory-analysis", json={**facts,"assumptions":assumptions}, headers={"X-Agent-Secret": SECRET}).status_code == 422
    assumptions["confirmedIncoming"] = 9
    assumptions["incomingArrivalDays"] = 1
    assert client.post("/internal/inventory-analysis", json={**facts,"assumptions":assumptions}, headers={"X-Agent-Secret": SECRET}).status_code == 422


def test_extremely_small_daily_demand_is_rejected_instead_of_overflow(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    data = {**facts, "assumptions": {"dailyDemand": "1e-100", "targetDays": 10, "confirmedIncoming": 0}}
    assert client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET}).status_code == 422


def test_order_level_shipments_are_preserved_but_do_not_reduce_purchase(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    shipment = {"id": "9007199254740993", "orderId": "100", "orderNum": "PO-100",
                "shipTime": "2026-11-01T10:00:00", "receiptTime": None, "status": 0}
    data = {**facts, "purchase": {"records": [], "withoutTask": [], "ledger": [],
            "shipments": [shipment], "scope": "shop", "limitation": "order-level records"},
            "assumptions": {"dailyDemand": "2", "targetDays": 10, "confirmedIncoming": 0}}
    response = client.post("/internal/inventory-analysis", json=data, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    result = response.json()
    assert result["facts"]["purchase"]["shipments"] == [shipment]
    assert result["suggestedQuantity"] == 12
    assert result["coverage"]["effectiveIncoming"] == 0
