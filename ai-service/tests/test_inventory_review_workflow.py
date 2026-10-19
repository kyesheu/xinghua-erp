from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.types import Command

from app.workflows.inventory_review import build_review_graph
from fastapi.testclient import TestClient
from app.main import app


def test_review_interrupt_survives_reopening_checkpoint(tmp_path):
    path = str(tmp_path / "review.sqlite")
    config = {"configurable": {"thread_id": "task-1"}}
    with SqliteSaver.from_conn_string(path) as checkpointer:
        graph = build_review_graph(checkpointer)
        paused = graph.invoke({"task_id": "task-1", "suggested_quantity": 15}, config)
        assert paused["__interrupt__"][0].value == {"taskId": "task-1", "suggestedQuantity": 15}
    with SqliteSaver.from_conn_string(path) as checkpointer:
        graph = build_review_graph(checkpointer)
        assert graph.get_state(config).next == ("await_review",)
        finished = graph.invoke(Command(resume={"approved": True, "version": 2}), config)
        assert finished["decision"] == {"approved": True, "version": 2}
        assert graph.get_state(config).next == ()


def test_sync_recovers_after_missing_pending_sync_and_is_idempotent(tmp_path, monkeypatch):
    monkeypatch.setenv("ERP_AGENT_SECRET", "a" * 32)
    monkeypatch.setenv("ERP_AGENT_CHECKPOINT_DB", str(tmp_path / "sync.sqlite"))
    client = TestClient(app)
    url = "/internal/inventory-review/sync"
    headers = {"X-Agent-Secret": "a" * 32}
    task = {"taskId": "9cd00957-f254-4de5-8e90-d716cc3bbf6f", "suggestedQuantity": 15,
            "status": "APPROVED", "version": 2, "reviewedBy": 7}
    assert client.post(url, json=task).status_code == 403
    first = client.post(url, json=task, headers=headers)
    assert first.status_code == 200
    assert first.json()["decision"] == {"status": "APPROVED", "version": 2, "reviewedBy": 7}
    assert client.post(url, json=task, headers=headers).json() == first.json()
    assert client.post(url, json={**task, "status": "REJECTED"}, headers=headers).status_code == 409
    assert client.post(url, json={**task, "suggestedQuantity": 16}, headers=headers).status_code == 409
