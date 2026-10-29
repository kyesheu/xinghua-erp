import hmac
import json
import os
from pathlib import Path
from typing import Iterator
from uuid import UUID
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import StreamingResponse
from starlette.concurrency import run_in_threadpool
from app.core.model_policy import _POLICY, RequestPolicy, current_policy
from pydantic import BaseModel, Field, model_validator
from app.core.config import apply_model_override, clear_model_override, shared_secret
from app.schemas.business import BusinessFacts
from app.tools.erp import bind_tool_token, reset_tool_token
from app.workflows.agent import ModelNotConfigured, ModelUnavailable, ping, run_text, stream_agent
from app.workflows.business import run_business
from app.workflows.inventory_review import sync_review
from app.workflows.marketing import MarketingFacts, run_marketing
from app.workflows.risk import RiskFacts, run_risk

app = FastAPI(title="兴华 ERP AI 服务", version="0.3.0")


def require_secret(provided: str) -> None:
    """内部接口统一鉴权：共享密钥缺失或过短时一律拒绝，不保留「未配置即放行」的通道。"""
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, provided):
        raise HTTPException(status_code=403, detail="禁止访问")


@app.middleware("http")
async def bind_request_context(request: Request, call_next):
    """把本次请求携带的模型参数与工具令牌绑到上下文。

    模型参数来自 Java 侧「模型配置」页面保存的凭据；工具令牌用于 Python 回调 Java 查数据
    时证明发起人身份。两者都用 ContextVar，未携带时保持空值，回退到环境变量与本地配置。
    """
    token = apply_model_override(
        request.headers.get("x-model-api-key", ""),
        request.headers.get("x-model-base-url", ""),
        request.headers.get("x-model-name", ""),
    )
    request_id = request.headers.get("x-request-id", "")
    import re
    from uuid import uuid4
    if not re.fullmatch(r"[a-fA-F0-9-]{16,64}", request_id):
        request_id = str(uuid4())
    policy_token = _POLICY.set(RequestPolicy(request_id=request_id, report_usage=request.headers.get("x-ai-usage") == "1"))
    tool_token = bind_tool_token(request.headers.get("x-tool-token", ""))
    try:
        response = await call_next(request)
        policy = current_policy()
        response.headers["X-Request-ID"] = request_id
        if request.headers.get("x-ai-usage") == "1" and not response.headers.get("content-type", "").startswith("application/x-ndjson"):
            response.headers["X-AI-Usage"] = json.dumps(policy.usage())
        return response
    finally:
        _POLICY.reset(policy_token)
        reset_tool_token(tool_token)
        clear_model_override(token)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/internal/inventory-analysis")
def inventory_analysis(request: BusinessFacts, x_agent_secret: str = Header(default="")):
    require_secret(x_agent_secret)
    return run_business(request)


class ReviewSync(BaseModel):
    taskId: UUID
    suggestedQuantity: int = Field(gt=0)
    status: str
    version: int
    reviewedBy: int | None = None

    @model_validator(mode="after")
    def valid_status(self):
        if (self.status == "PENDING" and self.version == 1 and self.reviewedBy is None):
            return self
        if self.status in ("APPROVED", "REJECTED") and self.version == 2 and self.reviewedBy and self.reviewedBy > 0:
            return self
        raise ValueError("审核状态无效")


@app.post("/internal/inventory-review/sync")
def inventory_review_sync(request: ReviewSync, x_agent_secret: str = Header(default="")):
    require_secret(x_agent_secret)
    decision = None if request.status == "PENDING" else {"status": request.status, "version": request.version, "reviewedBy": request.reviewedBy}
    path = os.getenv("ERP_AGENT_CHECKPOINT_DB", str(Path(__file__).resolve().parent.parent / "review-checkpoints.sqlite"))
    try:
        return sync_review(path, str(request.taskId), request.suggestedQuantity, decision, os.getenv("ERP_AGENT_CHECKPOINT_POSTGRES_DSN", ""))
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error

from app.workflows.product import product_draft
app.post("/internal/product-draft")(product_draft)
from app.workflows.management import ManagementFacts, ManagementQuestion, run_management, plan_management

@app.post("/internal/management-analysis")
def management_analysis(request: ManagementFacts, x_agent_secret: str = Header(default="")):
    require_secret(x_agent_secret)
    return run_management(request)


@app.post("/internal/management-plan")
def management_plan(request: ManagementQuestion, x_agent_secret: str = Header(default="")):
    require_secret(x_agent_secret)
    return plan_management(request)


@app.post("/internal/marketing-analysis")
def marketing_analysis(request: MarketingFacts, x_agent_secret: str = Header(default="")):
    require_secret(x_agent_secret)
    return run_marketing(request)


@app.post("/internal/risk-analysis")
def risk_analysis(request: RiskFacts, x_agent_secret: str = Header(default="")):
    require_secret(x_agent_secret)
    return run_risk(request)


class ChatMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(default="", max_length=8000)


class ChatRequest(BaseModel):
    system: str = Field(default="", max_length=8000)
    messages: list[ChatMessage] = Field(min_length=1, max_length=40)


class AnalysisRequest(BaseModel):
    system: str = Field(default="", max_length=8000)
    prompt: str = Field(min_length=1, max_length=8000)


class BriefRequest(BaseModel):
    system: str = Field(default="", max_length=8000)
    prompt: str = Field(min_length=1, max_length=16000)


def ndjson(events) -> Iterator[str]:
    for item in events:
        yield json.dumps(item, ensure_ascii=False) + "\n"
    policy = current_policy()
    if policy is not None and policy.report_usage:
        yield json.dumps({"type": "usage", "content": json.dumps(policy.usage())}) + "\n"


async def owned_ndjson(http: Request, events):
    policy = current_policy()
    iterator = iter(ndjson(events))
    end = object()
    try:
        while True:
            item = await run_in_threadpool(next, iterator, end)
            if item is end or await http.is_disconnected():
                break
            yield item
    finally:
        if policy is not None:
            policy.cancelled.set()
        try:
            iterator.close()
        except (ValueError, RuntimeError):
            pass


@app.post("/internal/chat")
def chat(request: ChatRequest, http: Request, x_agent_secret: str = Header(default="")):
    """多轮对话：模型自行调用 query_erp 取数，逐段返回文本。"""
    require_secret(x_agent_secret)
    messages = [message.model_dump() for message in request.messages]
    return StreamingResponse(owned_ndjson(http, stream_agent(request.system, messages)), media_type="application/x-ndjson")


@app.post("/internal/analysis")
def analysis(request: AnalysisRequest, http: Request, x_agent_secret: str = Header(default="")):
    """深度分析：同样走工具循环，提示模板由 Java 组装后传入。"""
    require_secret(x_agent_secret)
    messages = [{"role": "user", "content": request.prompt}]
    return StreamingResponse(owned_ndjson(http, stream_agent(request.system, messages)), media_type="application/x-ndjson")


@app.post("/internal/brief")
def brief(request: BriefRequest, x_agent_secret: str = Header(default="")):
    """工作台简报：结构固定、数据已由 Java 汇总，不需要工具，直接一次性生成。"""
    require_secret(x_agent_secret)
    try:
        return {"text": run_text(request.system, request.prompt)}
    except ModelNotConfigured as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    except ModelUnavailable as error:
        raise HTTPException(status_code=502, detail=str(error)) from error


@app.post("/internal/model-ping")
def model_ping(x_agent_secret: str = Header(default="")):
    """连通性自检：页面「测试连接」由 Java 转发到这里，用的仍是页面保存的配置。"""
    require_secret(x_agent_secret)
    try:
        return {"ok": True, "reply": ping()}
    except ModelNotConfigured as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    except ModelUnavailable as error:
        raise HTTPException(status_code=502, detail=str(error)) from error

