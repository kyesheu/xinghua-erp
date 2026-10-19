"""初始化 PostgreSQL 检查点表：python -m app.setup_checkpoints。"""
import os
from langgraph.checkpoint.postgres import PostgresSaver

dsn = os.environ["ERP_AGENT_CHECKPOINT_POSTGRES_DSN"]
with PostgresSaver.from_conn_string(dsn) as checkpointer:
    checkpointer.setup()
