"""在自动清理的独立MySQL库生成批次规模样本，不操作业务库。"""
import argparse
import re
import subprocess
import sys
import uuid
from pathlib import Path


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--container",default="xinghua-erp-mysql-1")
    args=parser.parse_args()
    schema="xinghua_bench_"+uuid.uuid4().hex[:12]
    assert re.fullmatch(r"xinghua_bench_[a-f0-9]{12}",schema)
    def sql(statement):
        return subprocess.run(["docker","exec",args.container,"sh","-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -e "$1"',"fixture",statement],capture_output=True,text=True,encoding="utf-8",check=True).stdout
    try:
        sql("CREATE DATABASE "+schema)
        sql("USE "+schema+";CREATE TABLE o_goods_inventory_batch(id BIGINT PRIMARY KEY,inventory_id BIGINT,current_qty INT,INDEX idx_inventory_id(inventory_id));"
            "SET SESSION cte_max_recursion_depth=50001;INSERT INTO o_goods_inventory_batch WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<50000) SELECT n,MOD(n,500)+1,MOD(n,19) FROM seq;ANALYZE TABLE o_goods_inventory_batch;")
        print("Isolated fixture: 50000 batches / 500 inventory IDs",flush=True)
        subprocess.run([sys.executable,str(Path(__file__).with_name("inspect_reconciliation_query.py")),"--container",args.container,"--database",schema,"--inventory-id","1"],check=True)
    finally:
        sql("DROP DATABASE IF EXISTS "+schema)


if __name__=="__main__":main()
