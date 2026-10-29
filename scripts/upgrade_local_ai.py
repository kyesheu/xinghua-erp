"""检查或应用本地 AI 优化所需增量结构，不重建业务表。默认只检查。"""
import argparse
from pathlib import Path
import subprocess


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--container",default="xinghua-erp-mysql-1")
    parser.add_argument("--apply",action="store_true")
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    def sql(statement):
        command=["docker","exec",args.container,"sh","-c",'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --database=xinghua-erp -N -e "$1"',"schema-check",statement]
        return subprocess.run(command,capture_output=True,text=True,encoding="utf-8",check=True).stdout.strip()
    scripts=["upgrade-ai-session-access.sql","upgrade-ai-usage-governance.sql"]
    for name in scripts:
        if args.apply:sql((root/"docker/mysql"/name).read_text())
    columns=[("ai_call_request","trace_id","varchar(64) NOT NULL DEFAULT ''"),
             ("ai_circuit_state","manual_disabled","tinyint(1) NOT NULL DEFAULT 0"),
             ("order_event_outbox","lease_token","varchar(36) NULL"),
             ("order_event_outbox","lease_until","datetime NULL")]
    changes=[]
    for table,column,definition in columns:
        exists=sql(f"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' AND column_name='{column}'")
        if exists=="0":changes.append(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")
    indexes=[("ai_conversation_history","idx_ai_history_context","session_id,user_id,timestamp,id"),
             ("o_order","idx_ai_order_merchant_time","merchant_id,order_time,shop_id"),
             ("o_order","idx_ai_order_time","order_time")]
    for table,name,fields in indexes:
        exists=sql(f"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='{table}' AND index_name='{name}'")
        if exists=="0":changes.append(f"CREATE INDEX {name} ON {table}({fields})")
    if not args.apply:
        for table in ("ai_conversation_session","ai_usage_bucket","ai_call_request","ai_circuit_state"):
            if sql(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='{table}'")=="0":
                print("MISSING TABLE",table)
    for statement in changes:
        print(("APPLY " if args.apply else "PENDING ")+statement)
        if args.apply:sql(statement)
    print("检查完成；业务表和数据未重建。" if not args.apply else "增量结构已就绪；业务表和数据未重建。")


if __name__=="__main__":main()
