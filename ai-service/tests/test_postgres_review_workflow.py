import os
import subprocess
import sys
import uuid

import pytest
from langgraph.checkpoint.postgres import PostgresSaver

from app.workflows.inventory_review import sync_review


@pytest.mark.skipif(not os.getenv("TEST_CHECKPOINT_POSTGRES_DSN"), reason="需要隔离 PostgreSQL 测试库")
def test_postgres_restart_and_concurrent_resume():
    dsn = os.environ["TEST_CHECKPOINT_POSTGRES_DSN"]
    task_id = str(uuid.uuid4())
    decision = {"status": "APPROVED", "version": 2, "reviewedBy": 7}
    with PostgresSaver.from_conn_string(dsn) as saver:
        saver.setup()
    try:
        assert sync_review("", task_id, 15, None, dsn)["waiting"]
        code = ("import os; from app.workflows.inventory_review import sync_review; "
                "sync_review('', os.environ['TEST_TASK'], 15, "
                "{'status':'APPROVED','version':2,'reviewedBy':7}, os.environ['TEST_CHECKPOINT_POSTGRES_DSN'])")
        env = {**os.environ, "TEST_TASK": task_id}
        processes = [subprocess.Popen([sys.executable, "-c", code], env=env) for _ in range(2)]
        assert [process.wait(timeout=30) for process in processes] == [0, 0]
        recovered = sync_review("", task_id, 15, decision, dsn)
        assert not recovered["waiting"] and recovered["decision"] == decision
        with pytest.raises(ValueError):
            sync_review("", task_id, 15, {**decision, "status": "REJECTED"}, dsn)
    finally:
        with PostgresSaver.from_conn_string(dsn) as saver:
            saver.delete_thread(task_id)
