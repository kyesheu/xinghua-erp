from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)
SECRET = "x" * 32


def test_management_dispatches_only_available_sources(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    facts = {"days": 7, "scope": "绑定店铺", "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2, "previousPayment": "10.00", "limitation": "未扣退款"}}
    assert client.post("/internal/management-analysis", json=facts).status_code == 403
    response = client.post("/internal/management-analysis", json=facts, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    result = response.json()
    assert result["scope"] == "绑定店铺"
    assert [section["source"] for section in result["sections"]] == ["sales", "notice"]
    assert "12.50" in result["sections"][0]["summary"] and "10.00" in result["sections"][0]["summary"]
    assert "未扣退款" in result["sections"][0]["limitation"]


def test_finance_unavailable_ledger_is_not_zero(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    facts = {"days": 30, "scope": "总部", "finance": {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": False, "limitation": "非利润"}}
    response = client.post("/internal/management-analysis", json=facts, headers={"X-Agent-Secret": SECRET})
    assert response.status_code == 200
    sections = response.json()["sections"]
    assert [section["source"] for section in sections] == ["finance", "ledger", "notice"]
    assert "不能视为零" in sections[1]["summary"]
    assert client.post("/internal/management-analysis", json={**facts, "days": 8}, headers={"X-Agent-Secret": SECRET}).status_code == 422


def test_settlement_profit_reported_with_caveats_and_null_kept_explicit(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    headers = {"X-Agent-Secret": SECRET}
    base = {"days": 30, "scope": "总部", "finance": {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True, "limitation": "非利润"}}
    # 无结算数据：利润不可用必须显式说明，不代表零利润
    sections = client.post("/internal/management-analysis", json=base, headers=headers).json()["sections"]
    assert "暂无已结算订单" in sections[0]["summary"] and "不代表零利润" in sections[0]["summary"]
    # 有结算数据：利润与口径限制同节展示，含退款调整排除说明
    settled = {**base, "finance": {**base["finance"], "settlementProfit": "52.00", "settledOrders": 2}}
    sections = client.post("/internal/management-analysis", json=settled, headers=headers).json()["sections"]
    assert "已结算订单 2 笔" in sections[0]["summary"] and "52.00" in sections[0]["summary"]
    assert "不含退款调整" in sections[0]["summary"] and "未结算订单不含在内" in sections[0]["summary"]
    notice = [s for s in sections if s["source"] == "notice"][0]
    assert "结算口径" in notice["summary"] and "不含退款调整" in notice["summary"]


def test_both_tasks_are_combined_without_mixing_amounts(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    facts = {"days": 7, "scope": "总部",
             "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2, "previousPayment": "10.00", "limitation": "未扣退款"},
             "finance": {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True, "limitation": "非利润"}}
    sections = client.post("/internal/management-analysis", json=facts, headers={"X-Agent-Secret": SECRET}).json()["sections"]
    assert [section["source"] for section in sections] == ["sales", "finance", "notice"]
    assert "12.50" in sections[0]["summary"] and "80" in sections[1]["summary"]
    assert "不可相加" in sections[2]["summary"]


def test_model_is_opt_in_and_receives_only_curated_sections(monkeypatch):
    from types import SimpleNamespace
    import langchain_openai
    seen = []
    class FakeModel:
        def __init__(self, **kwargs): pass
        def invoke(self, messages):
            seen.extend(messages)
            return SimpleNamespace(content="请人工核对。")
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    facts = {"days": 7, "scope": "店铺", "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2, "previousPayment": "10.00", "limitation": "未扣退款"}, "question": "有哪些限制？"}
    headers = {"X-Agent-Secret": SECRET}
    assert client.post("/internal/management-analysis", json=facts, headers=headers).json()["modelStatus"] == "not_requested"
    assert seen == []
    result = client.post("/internal/management-analysis", json={**facts, "useModel": True}, headers=headers).json()
    assert result["modelStatus"] == "ok" and result["explanation"] == "请人工核对。"
    assert "有哪些限制？" in seen[1].content and "未扣退款" in seen[1].content
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    assert client.post("/internal/management-analysis", json={**facts, "useModel": True}, headers=headers).json()["modelStatus"] == "disabled"


def test_model_failure_keeps_rule_summary(monkeypatch):
    import langchain_openai
    class FailedModel:
        def __init__(self, **kwargs): pass
        def invoke(self, messages): raise RuntimeError("unavailable")
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FailedModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    facts = {"days": 7, "scope": "总部", "useModel": True,
             "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2, "previousPayment": "10.00", "limitation": "未扣退款"}}
    result = client.post("/internal/management-analysis", json=facts, headers={"X-Agent-Secret": SECRET}).json()
    assert result["modelStatus"] == "unavailable" and result["explanation"] is None
    assert result["sections"][0]["source"] == "sales"


def test_risk_task_runs_alone_and_after_finance(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    facts = {"days": 7, "scope": "店铺", "risk": {"refunds": 2, "limitation": "记录数不代表已确认风险"}}
    headers = {"X-Agent-Secret": SECRET}
    sections = client.post("/internal/management-analysis", json=facts, headers=headers).json()["sections"]
    assert [section["source"] for section in sections] == ["risk", "notice"]
    assert "2 条" in sections[0]["summary"] and "不代表已确认风险" in sections[0]["limitation"]
    facts["finance"] = {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True, "limitation": "非利润"}
    sections = client.post("/internal/management-analysis", json=facts, headers=headers).json()["sections"]
    assert [section["source"] for section in sections] == ["finance", "risk", "notice"]


def test_inventory_page_summary_runs_with_risk_and_preserves_limit(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    facts = {"days": 7, "scope": "店铺", "risk": {"refunds": 2, "limitation": "记录数"},
             "inventory": {"total": 150, "scanned": 100, "risks": {"缺货": 3}, "limitation": "仅第一页"}}
    sections = client.post("/internal/management-analysis", json=facts, headers={"X-Agent-Secret": SECRET}).json()["sections"]
    assert [item["source"] for item in sections] == ["risk", "inventory", "notice"]
    assert "150" in sections[1]["summary"] and "缺货 3 个" in sections[1]["summary"] and sections[1]["limitation"] == "仅第一页"


def test_sku_and_loss_facts_are_transferred_with_caveats(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    headers = {"X-Agent-Secret": SECRET}
    facts = {"days": 7, "scope": "总部",
             "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2,
                        "previousPayment": "10.00", "limitation": "未扣退款",
                        "sku": {"skuCount": 3, "top5Share": 500, "top10Share": 800, "top20Share": 1000,
                                 "quadrants": {"highSalesHighRefund": 1, "lowSalesLowRefund": 2}, "limitation": "SKU限制"}},
             "finance": {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True,
                          "limitation": "非利润",
                          "loss": {"count": 2, "causes": {"运费占比过高": 1, "零成本待核实": 1}, "limitation": "亏损限制"}}}
    sections = client.post("/internal/management-analysis", json=facts, headers=headers).json()["sections"]
    assert [section["source"] for section in sections] == ["sales", "finance", "notice"]
    sales_summary = sections[0]["summary"]
    assert "共 3 个商品" in sales_summary and "TOP5占比 50.0%" in sales_summary
    assert "高销量高退款 1 个" in sales_summary and "低销量低退款 2 个" in sales_summary
    assert "SKU限制" in sales_summary
    finance_summary = sections[1]["summary"]
    assert "负利润订单 2 笔" in finance_summary and "运费占比过高 1 笔" in finance_summary and "亏损限制" in finance_summary


def test_sku_and_loss_facts_reject_extra_fields(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    headers = {"X-Agent-Secret": SECRET}
    base_sales = {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2,
                  "previousPayment": "10.00", "limitation": "未扣退款"}
    base_finance = {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True, "limitation": "非利润"}
    extra_sku = {**base_sales, "sku": {"skuCount": 1, "limitation": "x", "rows": [{"skuId": "5"}]}}
    assert client.post("/internal/management-analysis", json={"days": 7, "scope": "总部", "sales": extra_sku},
                       headers=headers).status_code == 422
    extra_loss = {**base_finance, "loss": {"count": 1, "limitation": "x", "orders": [{"orderNo": "O1"}]}}
    assert client.post("/internal/management-analysis", json={"days": 7, "scope": "总部", "finance": extra_loss},
                       headers=headers).status_code == 422


def test_old_contract_without_sku_and_loss_still_works(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    headers = {"X-Agent-Secret": SECRET}
    facts = {"days": 7, "scope": "总部",
             "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2, "previousPayment": "10.00", "limitation": "未扣退款"},
             "finance": {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True, "limitation": "非利润"}}
    sections = client.post("/internal/management-analysis", json=facts, headers=headers).json()["sections"]
    assert [section["source"] for section in sections] == ["sales", "finance", "notice"]
    assert "SKU 表现" not in sections[0]["summary"]
    assert "负利润订单" not in sections[1]["summary"]


def test_question_planning_uses_structured_model_and_rejects_unsupported(monkeypatch):
    import langchain_openai
    from app.workflows.management import TaskPlan
    seen = []
    class FakeModel:
        def __init__(self, **kwargs): pass
        def with_structured_output(self, schema, method):
            assert schema is TaskPlan and method == "json_mode"
            return self
        def invoke(self, messages):
            seen.append(messages[1].content)
            if "历史库存" in messages[1].content:
                return TaskPlan(topics=["inventory"], unsupported=True)
            if "库存" in messages[1].content:
                return TaskPlan(topics=["sales", "inventory"], unsupported=False)
            return TaskPlan(topics=["sales", "risk", "sales"], unsupported=False, days=7)
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", FakeModel)
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    headers = {"X-Agent-Secret": SECRET}
    assert client.post("/internal/management-plan", json={"question":"销售和售后"}).status_code == 403
    result = client.post("/internal/management-plan", json={"question":"近7天销售和售后"}, headers=headers).json()
    assert result == {"status":"ok", "topics":["sales", "risk"], "days":7} and seen == ["近7天销售和售后"]
    result = client.post("/internal/management-plan", json={"question":"销售和库存"}, headers=headers).json()
    assert result == {"status":"ok", "topics":["sales", "inventory"], "days":None}
    assert client.post("/internal/management-plan", json={"question":"历史库存"}, headers=headers).json()["status"] == "unsupported"
    assert client.post("/internal/management-plan", json={"question":" "}, headers=headers).status_code == 422
    monkeypatch.setenv("AI_MODEL_ENABLED", "false")
    assert client.post("/internal/management-plan", json={"question":"销售"}, headers=headers).json()["status"] == "disabled"


def test_planner_parses_openai_compatible_json_response(monkeypatch):
    import json
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from threading import Thread
    seen = []
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            seen.append(payload)
            content = '{"topics":["risk"],"unsupported":false}' if len(seen) == 1 else '{"topics":["unknown"],"unsupported":false}'
            body = json.dumps({"id":"test", "object":"chat.completion", "created":1, "model":"test",
                               "choices":[{"index":0,"message":{"role":"assistant","content":content},"finish_reason":"stop"}]}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        def log_message(self, format, *args): pass
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    monkeypatch.setenv("AI_MODEL_ENABLED", "true")
    monkeypatch.setenv("AI_MODEL_NAME", "test")
    monkeypatch.setenv("AI_MODEL_API_KEY", "test")
    monkeypatch.setenv("AI_MODEL_BASE_URL", f"http://127.0.0.1:{server.server_port}/v1")
    monkeypatch.setenv("NO_PROXY", "127.0.0.1,localhost")
    for name in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy"):
        monkeypatch.delenv(name, raising=False)
    try:
        result = client.post("/internal/management-plan", json={"question":"售后情况"}, headers={"X-Agent-Secret": SECRET}).json()
        assert result == {"status":"ok", "topics":["risk"], "days":None}
        assert seen[0]["response_format"] == {"type":"json_object"}
        invalid = client.post("/internal/management-plan", json={"question":"其他问题"}, headers={"X-Agent-Secret": SECRET}).json()
        assert invalid["status"] == "unavailable" and invalid["topics"] == []
    finally:
        server.shutdown()
        thread.join()
        server.server_close()


def test_dispatch_fans_out_all_available_topics_in_parallel():
    from langgraph.types import Send
    from app.workflows.management import ManagementFacts, SalesFacts, FinanceFacts, RiskFacts, InventoryFacts, dispatch
    all_facts = ManagementFacts(
        days=7, scope="总部",
        sales=SalesFacts(orders=1, paidOrders=1, payment="1", previousOrders=0, previousPaidOrders=0, previousPayment="0", limitation="x"),
        finance=FinanceFacts(payment="1", refund="0", difference="1", ledgerAvailable=True, limitation="x"),
        risk=RiskFacts(refunds=0, limitation="x"),
        inventory=InventoryFacts(total=0, scanned=0, risks={}, limitation="x"),
    )
    sends = dispatch({"facts": all_facts})
    assert isinstance(sends, list) and all(isinstance(s, Send) for s in sends)
    assert [s.node for s in sends] == ["sales", "finance", "risk", "inventory"]
    # 单一资料只扇出一个节点
    single = ManagementFacts(days=7, scope="总部",
                             finance=FinanceFacts(payment="1", refund="0", difference="1", ledgerAvailable=True, limitation="x"))
    assert [s.node for s in dispatch({"facts": single})] == ["finance"]
    # 无可用资料直接走汇总提示
    assert [s.node for s in dispatch({"facts": ManagementFacts(days=7, scope="总部")})] == ["aggregate"]


def test_all_topics_execute_in_one_parallel_superstep_and_aggregate(monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", SECRET)
    facts = {"days": 7, "scope": "总部",
             "sales": {"orders": 5, "paidOrders": 3, "payment": "12.50", "previousOrders": 4, "previousPaidOrders": 2, "previousPayment": "10.00", "limitation": "未扣退款"},
             "finance": {"payment": "100", "refund": "20", "difference": "80", "ledgerAvailable": True, "limitation": "非利润"},
             "risk": {"refunds": 2, "limitation": "记录数"},
             "inventory": {"total": 150, "scanned": 100, "risks": {"缺货": 3}, "limitation": "仅第一页"}}
    result = client.post("/internal/management-analysis", json=facts, headers={"X-Agent-Secret": SECRET}).json()
    # 四个主题的 section 全部生成，且按固定口径排序输出（并行合并不确定顺序，输出顺序必须确定）
    assert [section["source"] for section in result["sections"]] == ["sales", "finance", "risk", "inventory", "notice"]
    assert "不可相加" in result["sections"][-1]["summary"]
