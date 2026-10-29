"""读取本地 MySQL 规范化语句摘要，不输出业务参数和原始 SQL 文本。"""
import argparse
import subprocess


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--container",default="xinghua-erp-mysql-1")
    args=parser.parse_args()
    sql="""SELECT SCHEMA_NAME,DIGEST,LEFT(DIGEST_TEXT,500) AS normalized_query,COUNT_STAR,
        ROUND(AVG_TIMER_WAIT/1000000000,3) AS average_ms,
        ROUND(MAX_TIMER_WAIT/1000000000,3) AS max_ms,
        SUM_ROWS_EXAMINED,SUM_ROWS_SENT
        FROM performance_schema.events_statements_summary_by_digest
        WHERE SCHEMA_NAME='xinghua-erp' ORDER BY SUM_TIMER_WAIT DESC LIMIT 20"""
    command=["docker","exec",args.container,"sh","-c",'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --database=xinghua-erp -e "$1"',"sql-pressure",sql]
    result=subprocess.run(command,capture_output=True,text=True,encoding="utf-8",check=True)
    print(result.stdout)


if __name__=="__main__":main()
