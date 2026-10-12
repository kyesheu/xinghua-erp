# 生产镜像：仅含运行依赖，不含测试工具、本地配置与检查点文件。
# 配置一律通过环境注入：ERP_AGENT_CONFIG 或 ERP_AGENT_SECRET / AI_MODEL_*；
# 部署时必须设置 ERP_AGENT_CHECKPOINT_POSTGRES_DSN（本地 SQLite 模式仅限单进程开发），
# 并以同一配置执行一次 python -m app.setup_checkpoints 初始化检查点表。
# 容器内监听 0.0.0.0 是容器网络要求，"不暴露到网络"的边界由部署侧网络策略保证，
# 不要把该端口直接发布到不受信任的网络。
FROM python:3.12-slim

ENV TZ=Asia/Shanghai \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /service

COPY pyproject.toml ./
COPY app ./app

RUN pip install --no-cache-dir .

RUN useradd --system --create-home --shell /usr/sbin/nologin agent \
    && chown -R agent:agent /service
USER agent

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/docs', timeout=4).status==200 else 1)"

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
