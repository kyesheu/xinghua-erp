"""在本地容器只读对比经营聚合查询的结果及实际执行计划。"""
import argparse
from datetime import datetime
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", default="xinghua-erp-mysql-1")
    parser.add_argument("--merchant-id", type=int, required=True)
    parser.add_argument("--shop-id", type=int, required=True)
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    args = parser.parse_args()
    start = datetime.fromisoformat(args.start)
    end = datetime.fromisoformat(args.end)
    if args.merchant_id < 0 or args.shop_id < 0 or start >= end:
        parser.error("主体与时间范围无效")
    where = (f" WHERE merchant_id={args.merchant_id} AND shop_id={args.shop_id} "
             f"AND order_time>='{start.isoformat(sep=' ')}' AND order_time<'{end.isoformat(sep=' ')}' "
             "AND payment>0 AND order_status IN (1,2,3,101)")
    select = "SELECT COUNT(*),COALESCE(SUM(payment),0) FROM o_order"
    variants = {"existing_indexes": select + " IGNORE INDEX(idx_ai_order_merchant_time,idx_ai_order_time)" + where,
                "current_indexes": select + where}
    results = []
    for label, query in variants.items():
        print(label, flush=True)
        for sql in (query, "EXPLAIN ANALYZE " + query):
            command = ["docker", "exec", args.container, "sh", "-c",
                       'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --database=xinghua-erp -N -e "$1"', "mysql-check", sql]
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", check=True)
            print(result.stdout, flush=True)
            if sql == query:
                results.append(result.stdout.strip())
    if len(set(results)) != 1:
        raise SystemExit("两种执行方式的业务结果不一致")
    print("聚合结果一致。小样本结果不能推断生产性能提升。")


if __name__ == "__main__":
    main()
