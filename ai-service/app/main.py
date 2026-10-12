import hmac
import os
from pathlib import Path
from uuid import UUID
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, model_validator
from app.core.config import shared_secret
from app.schemas.business import BusinessFacts
from app.workflows.business import run_business
from app.workflows.inventory_review import sync_review
from app.workflows.marketing import MarketingFacts, run_marketing
from app.workflows.risk import RiskFacts, run_risk

app = FastAPI(title="兴华 ERP AI 服务", version="0.2.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/internal/inventory-analysis")
def inventory_analysis(request: BusinessFacts, x_agent_secret: str = Header(default="")):
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, x_agent_secret):
        raise HTTPException(status_code=403, detail="禁止访问")
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
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, x_agent_secret):
        raise HTTPException(status_code=403, detail="禁止访问")
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
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, x_agent_secret):
        raise HTTPException(status_code=403, detail="禁止访问")
    return run_management(request)


@app.post("/internal/management-plan")
def management_plan(request: ManagementQuestion, x_agent_secret: str = Header(default="")):
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, x_agent_secret):
        raise HTTPException(status_code=403, detail="禁止访问")
    return plan_management(request)


@app.post("/internal/marketing-analysis")
def marketing_analysis(request: MarketingFacts, x_agent_secret: str = Header(default="")):
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, x_agent_secret):
        raise HTTPException(status_code=403, detail="禁止访问")
    return run_marketing(request)


@app.post("/internal/risk-analysis")
def risk_analysis(request: RiskFacts, x_agent_secret: str = Header(default="")):
    secret = shared_secret()
    if len(secret) < 32 or not hmac.compare_digest(secret, x_agent_secret):
        raise HTTPException(status_code=403, detail="禁止访问")
    return run_risk(request)
