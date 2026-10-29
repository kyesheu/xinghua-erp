import sqlite3
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

import pytest

from app.workflows.inventory_review import _switch_to_wal, sync_review


def test_sqlite_multi_process_resume_is_serialized(tmp_path):
    path = str(tmp_path / "review.sqlite")
    code = """import sys
from app.workflows.inventory_review import sync_review
assert not sync_review(sys.argv[1], 'task', 15, {'status': 'APPROVED'})['waiting']
"""
    with ThreadPoolExecutor(max_workers=4) as executor:
        results = list(executor.map(lambda _: subprocess.run(
            [sys.executable, "-c", code, path], capture_output=True, text=True, timeout=45), range(4)))
    assert all(result.returncode == 0 for result in results), [r.stderr for r in results]
    assert sync_review(path, "task", 15, {"status": "APPROVED"})["decision"] == {"status": "APPROVED"}
    with pytest.raises(ValueError, match="审核决定"):
        sync_review(path, "task", 15, {"status": "REJECTED"})


class _FlakyConn:
    """按预设脚本依次抛错或成功，用于验证重试次数与「只吞瞬时争用」的边界。"""

    def __init__(self, errors):
        self.errors = list(errors)
        self.calls = 0

    def execute(self, sql):
        self.calls += 1
        if self.errors:
            raise sqlite3.OperationalError(self.errors.pop(0))
        return self


def test_switch_to_wal_retries_transient_readonly(monkeypatch):
    # Windows 上另一进程句柄收尾时返回的是 READONLY 而非 BUSY，busy_timeout 不会重试它，
    # 这里的高负载窄窗口只能靠有界重试兜住，前两次失败第三次成功是实测到的形态。
    monkeypatch.setattr("app.workflows.inventory_review.time.sleep", lambda _: None)
    conn = _FlakyConn(["attempt to write a readonly database", "database is locked"])
    _switch_to_wal(conn)
    assert conn.calls == 3


def test_switch_to_wal_does_not_swallow_real_errors(monkeypatch):
    # 权限、磁盘、损坏这类错误不是瞬时争用，重试毫无意义，必须立刻上抛暴露问题。
    monkeypatch.setattr("app.workflows.inventory_review.time.sleep", lambda _: None)
    conn = _FlakyConn(["disk I/O error"])
    with pytest.raises(sqlite3.OperationalError, match="disk I/O error"):
        _switch_to_wal(conn)
    assert conn.calls == 1

