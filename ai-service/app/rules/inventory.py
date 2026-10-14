"""确定性库存核查；需求与在途只接受人工确认，不从销量推断。"""
from collections import Counter
from decimal import Decimal, ROUND_DOWN
from app.schemas.business import BusinessFacts


def effective_incoming(a) -> int:
    if a.incomingBatches is not None:
        return sum(batch.quantity for batch in a.incomingBatches if batch.arrivalDays <= a.targetDays)
    return a.confirmedIncoming if a.incomingArrivalDays is None or a.incomingArrivalDays <= a.targetDays else 0


def arrival_projection(a, available: int) -> dict | None:
    if a.incomingBatches is None:
        return None
    arrivals = Counter()
    for batch in a.incomingBatches:
        if batch.arrivalDays <= a.targetDays:
            arrivals[batch.arrivalDays] += batch.quantity
    balance = Decimal(available)
    previous = 0
    first_gap = None
    steps = []
    for day in sorted(set(arrivals) | {a.targetDays}):
        before = balance - a.dailyDemand * (day - previous)
        if before < 0 and first_gap is None:
            first_gap = Decimal(previous) + max(Decimal(0), balance) / a.dailyDemand
        balance = before + arrivals[day]
        steps.append({"day": day, "incoming": arrivals[day], "beforeArrival": str(before), "afterArrival": str(balance)})
        previous = day
    return {"firstShortageDay": None if first_gap is None else str(first_gap.quantize(Decimal("0.01"), rounding=ROUND_DOWN)),
            "endBalance": str(balance), "steps": steps,
            "limitation": "按固定人工日需求连续扣减，负数为累计供需缺口，并非真实库存；同日到货视为当天起始可用，未包含本次建议采购。"}


def inventory_checks(data: BusinessFacts) -> list[dict]:
    counts = Counter((r.warehouseId, r.merchantId, r.shopId, r.stockStatus) for r in data.inventory)
    result = []
    for row in data.inventory:
        key = (row.warehouseId, row.merchantId, row.shopId, row.stockStatus)
        problems = []
        if (row.warehouseId is None or row.warehouseId <= 0 or row.merchantId is None
                or row.shopId is None or row.merchantId < 0 or row.shopId < 0):
            problems.append("库存归属不完整")
        if counts[key] > 1:
            problems.append("同仓库及主体存在重复库存记录")
        if any(v is None or v < 0 for v in (row.quantity, row.availableQuantity, row.lockedQuantity)):
            problems.append("库存数量缺失或为负")
        elif row.quantity != row.availableQuantity + row.lockedQuantity:
            problems.append("总量不等于可用量加锁定量")
        if row.stockStatus != 1:
            problems.append("非良品库存")
        if row.batchCount is None or row.batchCount == 0:
            problems.append("缺少库存批次，需核实")
        elif row.invalidBatches is None or row.invalidBatches > 0 or row.batchQuantity is None or row.batchQuantity != row.quantity:
            problems.append("库存批次数量或归属异常")
        status = "待核实" if problems else "缺货" if row.availableQuantity == 0 else "可核查"
        result.append({"warehouseId": row.warehouseId, "merchantId": row.merchantId,
                       "shopId": row.shopId, "status": status, "issues": problems,
                       "batchCount": row.batchCount, "batchQuantity": row.batchQuantity,
                       "availableQuantity": row.availableQuantity if not problems else None})
    return result


def coverage_metrics(data: BusinessFacts, quantity: int | None) -> dict | None:
    if quantity is None:
        return None
    a = data.assumptions
    available = data.inventory[0].availableQuantity
    # 向下取两位小数，避免四舍五入高估覆盖时间；在途到达日期未知，不计入当前覆盖天数。
    days = (Decimal(available) / a.dailyDemand).quantize(Decimal("0.01"), rounding=ROUND_DOWN)
    effective = effective_incoming(a)
    projection = arrival_projection(a, available)
    risks = []
    if a.leadTimeDays is not None and Decimal(available) < a.dailyDemand * a.leadTimeDays:
        risks.append("仅靠当前库存不足以覆盖采购交期，请结合已确认到货评估加急或调拨。")
    if a.confirmedIncoming > 0 and a.incomingArrivalDays is not None and Decimal(available) < a.dailyDemand * a.incomingArrivalDays:
        risks.append("当前库存可能在该批在途到达前耗尽，周期总量充足也不能排除中途缺货。")
    if projection and projection["firstShortageDay"] is not None:
        risks.append("多批次到货计划存在中途供需缺口，请核对首次缺货时间并评估加急或调拨。")
    return {"dailyDemand": str(a.dailyDemand), "targetDays": a.targetDays,
            "availableQuantity": available, "confirmedIncoming": a.confirmedIncoming,
            "currentCoverageDays": str(days), "targetDemand": str(a.dailyDemand * a.targetDays),
            "effectiveIncoming": effective, "incomingArrivalDays": a.incomingArrivalDays,
            "leadTimeDays": a.leadTimeDays, "timingRisks": risks,
            "arrivalProjection": projection,
            "rule": "max(0, ceil(日需求 × 目标天数 - 可用库存 - 本周期有效到货))",
            "limitation": ("按人工固定日需求及逐批人工到货计划计算；未包含本次建议采购到货，不能替代实际需求预测。" if projection is not None else "按人工固定日需求估算；单批到货间隔适用于全部确认在途同批到达，未填交期或到货间隔时无法判断对应的中途缺货风险。")}


def purchase_checks(data: BusinessFacts) -> list[dict]:
    if data.purchase is None:
        return []
    # 采购任务是事实核查列表；同一采购明细可能重复生成任务，绝不直接累加。
    records = data.purchase.records
    counts = Counter(str(r.get("sourceItemId")) for r in records if r.get("sourceItemId") is not None)
    result = []
    for row in records:
        issues = []
        source = row.get("sourceItemId")
        if source is None or str(source) == "0":
            issues.append("缺少采购明细关联")
        elif counts[str(source)] > 1:
            issues.append("同一采购明细存在多个任务，需排查重复")
        quantity, received = row.get("quantity"), row.get("inQuantity")
        valid = (type(quantity) is int and type(received) is int
                 and quantity > 0 and 0 <= received < quantity)
        if not valid:
            issues.append("任务数量或已入库数量异常")
        if row.get("itemStatus") not in (0, 1):
            issues.append("任务状态与待入库数量不一致")
        if row.get("warehouseId") is None:
            issues.append("任务仓库缺失")
        result.append({"sourceNo": row.get("sourceNo"), "warehouseId": row.get("warehouseId"),
                       "status": "待核实" if issues else "待入库任务",
                       "remainingQuantity": quantity - received if valid else None,
                       "issues": issues})
    return result
