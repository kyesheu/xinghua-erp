from datetime import datetime
from decimal import Decimal
from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator


class InventoryRow(BaseModel):
    model_config = ConfigDict(extra="forbid")
    warehouseId: int | None = None
    merchantId: int | None = None
    shopId: int | None = None
    quantity: int | None = None
    availableQuantity: int | None = None
    lockedQuantity: int | None = None
    stockStatus: int | None = None
    batchCount: int | None = None
    batchQuantity: int | None = None
    invalidBatches: int | None = None


class Sales(BaseModel):
    skuId: int
    quantity: int
    start: datetime
    endExclusive: datetime
    scope: str
    limitation: str


class Purchase(BaseModel):
    shipments: list[dict[str, Any]] = Field(default_factory=list, max_length=200)
    ledger: list[dict[str, Any]] = Field(default_factory=list, max_length=200)
    records: list[dict[str, Any]] = Field(max_length=200)
    withoutTask: list[dict[str, Any]] = Field(default_factory=list, max_length=200)
    scope: str
    limitation: str


class IncomingBatch(BaseModel):
    model_config = ConfigDict(extra="forbid")
    quantity: int = Field(gt=0, le=1000000000)
    arrivalDays: int = Field(ge=0, le=3650)


class Assumptions(BaseModel):
    model_config = ConfigDict(extra="forbid")
    dailyDemand: Decimal = Field(ge=Decimal("0.000001"), le=1000000, max_digits=13, decimal_places=6)
    targetDays: int = Field(ge=1, le=365)
    confirmedIncoming: int = Field(ge=0, le=1000000000)
    incomingArrivalDays: int | None = Field(default=None, ge=0, le=3650)
    leadTimeDays: int | None = Field(default=None, ge=0, le=3650)
    incomingBatches: list[IncomingBatch] | None = Field(default=None, max_length=50)

    @model_validator(mode="after")
    def validate_batches(self):
        if self.incomingBatches is not None:
            if self.incomingArrivalDays is not None:
                raise ValueError("多批次计划不能同时填写单批到货间隔")
            if sum(batch.quantity for batch in self.incomingBatches) != self.confirmedIncoming:
                raise ValueError("批次数量之和必须等于确认在途总量")
        return self


class BusinessFacts(BaseModel):
    model_config = ConfigDict(extra="forbid")
    skuId: int = Field(gt=0)
    warehouseId: int | None = Field(default=None, gt=0)
    days: Literal[7, 30]
    retrievedAt: datetime
    inventory: list[InventoryRow] = Field(max_length=200)
    sales: Sales | None = None
    purchase: Purchase | None = None
    inventoryLimitation: str
    assumptions: Assumptions | None = None
    question: str = Field(default="", max_length=500)
    useModel: bool = True
