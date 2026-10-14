"""库存审核的可恢复流程；ERP 仍是审核决定和业务写入的权威来源。"""
from typing import TypedDict
from threading import Lock

from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt


class ReviewState(TypedDict, total=False):
    task_id: str
    suggested_quantity: int
    decision: dict


def await_review(state: ReviewState) -> dict:
    decision = interrupt({"taskId": state["task_id"], "suggestedQuantity": state["suggested_quantity"]})
    return {"decision": decision}


def build_review_graph(checkpointer):
    builder = StateGraph(ReviewState)
    builder.add_node("await_review", await_review)
    builder.add_edge(START, "await_review")
    builder.add_edge("await_review", END)
    return builder.compile(checkpointer=checkpointer)


_sync_lock = Lock()


def sync_review(path: str, task_id: str, suggested_quantity: int, decision: dict | None, postgres_dsn: str = "") -> dict:
    """从 Java 的已提交状态恢复检查点，重复同步不重复恢复流程。"""
    config = {"configurable": {"thread_id": task_id}}
    saver = PostgresSaver.from_conn_string(postgres_dsn) if postgres_dsn else SqliteSaver.from_conn_string(path)
    with _sync_lock, saver as checkpointer:
        if postgres_dsn:
            # 会话锁随数据库连接关闭释放，确保多个 Python 进程串行处理同一任务。
            checkpointer.conn.execute("SELECT pg_advisory_lock(hashtextextended(%s, 0))", (task_id,))
        graph = build_review_graph(checkpointer)
        state = graph.get_state(config)
        if not state.values:
            graph.invoke({"task_id": task_id, "suggested_quantity": suggested_quantity}, config, durability="sync")
            state = graph.get_state(config)
        if state.values["suggested_quantity"] != suggested_quantity:
            raise ValueError("审核任务与已有检查点不一致")
        if decision is not None:
            if state.next:
                graph.invoke(Command(resume=decision), config, durability="sync")
                state = graph.get_state(config)
            elif state.values.get("decision") != decision:
                raise ValueError("审核决定与已有检查点不一致")
        return {"taskId": task_id, "waiting": bool(state.next), "decision": state.values.get("decision")}
