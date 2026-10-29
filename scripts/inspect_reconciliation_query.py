"""只读比较库存批次聚合的实际执行计划，不调用模型或修改业务数据。"""
import argparse
import subprocess
import re


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", default="xinghua-erp-mysql-1")
    parser.add_argument("--database", default="xinghua-erp")
    parser.add_argument("--inventory-id", type=int, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.database):
        parser.error("数据库名无效")
    if args.inventory_id <= 0:
        parser.error("库存ID必须为正数")
    query = "SELECT /*+ MAX_EXECUTION_TIME(10000) */ COUNT(*),SUM(current_qty) FROM o_goods_inventory_batch {hint} WHERE inventory_id=" + str(args.inventory_id)
    values = []
    for label, hint in (("without_inventory_index", "IGNORE INDEX(idx_inventory_id)"), ("current_indexes", "")):
        sql = query.format(hint=hint)
        def run(statement):
            command = ["docker", "exec", args.container, "sh", "-c",
                       'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --database="$2" -N -e "$1"', "reconciliation-plan", statement, args.database]
            return subprocess.run(command, capture_output=True, text=True, encoding="utf-8", check=True).stdout.strip()
        values.append(run(sql))
        print(label)
        print(run("EXPLAIN ANALYZE " + sql))
    if values[0] != values[1]:
        raise SystemExit("相同条件聚合结果不一致")
    print("两种访问方式聚合结果一致；本地计划不能外推生产性能收益。")


if __name__ == "__main__":
    main()
