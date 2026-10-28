import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

import pytest
from app.workflows.inventory_review import sync_review


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
