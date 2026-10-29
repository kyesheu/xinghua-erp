"""库存审核的可恢复流程；ERP 仍是审核决定和业务写入的权威来源。"""
from typing import TypedDict
from contextlib import closing, contextmanager, nullcontext
from pathlib import Path
import sqlite3
import time

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


@contextmanager
def _sqlite_review_lock(path: str):
    """使用独立锁库保护完整读/恢复过程，覆盖同机多个 worker。"""
    lock_path = str(Path(path).resolve()) + ".review-lock"
    with closing(sqlite3.connect(lock_path, timeout=30)) as guard:
        guard.execute("BEGIN IMMEDIATE")
        try:
            _prepare_checkpoint_db(path)
            yield
        finally:
            guard.rollback()


def _prepare_checkpoint_db(path: str) -> None:
    """在持有文件锁的前提下确认检查点库处于 WAL 且可用。"""
    with closing(sqlite3.connect(path, timeout=30)) as conn:
        conn.execute("PRAGMA busy_timeout=30000")
        # 先读后写：已经是 WAL 就不要再执行切换。
        # 切库要拿数据库文件的写锁，而 busy_timeout 只对 SQLITE_BUSY 重试——
        # Windows 上另一进程的句柄/-wal/-shm 正在收尾时返回的是 SQLITE_READONLY，
        # 这里等多久都不会重试。四个进程里只有首个创建者真正需要切库，
        # 后到的三个如果照旧无脑切一次，等于每次都主动去撞那个窗口。
        mode = conn.execute("PRAGMA journal_mode").fetchone()
        if not mode or str(mode[0]).lower() != "wal":
            _switch_to_wal(conn)
        conn.commit()


def _switch_to_wal(conn) -> None:
    """首次建库才会走到这里。READONLY/LOCKED/BUSY 属于句柄交接的瞬时状态，做有界重试。"""
    last = None
    for attempt in range(1, 6):
        try:
            conn.execute("PRAGMA journal_mode=WAL")
            return
        except sqlite3.OperationalError as exc:
            text = str(exc).lower()
            # 只吞「瞬时争用」这三类；权限、损坏、磁盘错误必须原样上抛，不能把真故障藏起来。
            if "readonly" not in text and "locked" not in text and "busy" not in text:
                raise
            last = exc
            time.sleep(0.05 * attempt)
    raise last



def sync_review(path: str, task_id: str, suggested_quantity: int, decision: dict | None, postgres_dsn: str = "") -> dict:
    """从 Java 的已提交状态恢复检查点，重复同步不重复恢复流程。"""
    config = {"configurable": {"thread_id": task_id}}
    saver = PostgresSaver.from_conn_string(postgres_dsn) if postgres_dsn else SqliteSaver.from_conn_string(path)
    with (nullcontext() if postgres_dsn else _sqlite_review_lock(path)), saver as checkpointer:
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
