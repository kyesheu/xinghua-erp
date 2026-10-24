"""ERP 数据查询工具。

分工：模型调用、工具选择与多轮编排都在 Python；业务数据仍由 Java 按账号权限查询。
Python 不直连数据库，也不保存用户会话——每次工具调用都回传 Java 下发的一次性令牌，
由 Java 校验签名后才执行查询，因此 Python 侧无法通过篡改用户标识越权取数。
"""

import json
import os
from contextvars import ContextVar
from urllib import error, request

from langchain_core.tools import tool

from app.core.config import shared_secret

# 本次请求的一次性工具令牌：Java 生成，Python 原样回传。
_TOOL_TOKEN: ContextVar[str] = ContextVar("erp_tool_token", default="")

_TOOL_TIMEOUT_SECONDS = 15


def bind_tool_token(token: str) -> object:
    return _TOOL_TOKEN.set(token or "")


def reset_tool_token(handle: object) -> None:
    _TOOL_TOKEN.reset(handle)


def backend_base_url() -> str:
    return (os.getenv("ERP_BACKEND_URL") or "http://127.0.0.1:6149").rstrip("/")


def query_backend(resource: str, params: dict) -> str:
    """调用 Java 工具网关取一段已格式化的业务数据；失败时抛可读异常。"""
    body = json.dumps({"resource": resource, "params": params}, ensure_ascii=False).encode("utf-8")
    call = request.Request(backend_base_url() + "/internal/ai-tools/query", data=body, method="POST")
    call.add_header("Content-Type", "application/json")
    call.add_header("X-Agent-Secret", shared_secret())
    token = _TOOL_TOKEN.get()
    if token:
        call.add_header("X-Tool-Token", token)
    try:
        with request.urlopen(call, timeout=_TOOL_TIMEOUT_SECONDS) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except error.HTTPError as exc:
        raise RuntimeError(f"ERP 数据查询被拒绝（HTTP {exc.code}）") from exc
    except (error.URLError, TimeoutError, OSError) as exc:
        raise RuntimeError("无法连接 ERP 服务，业务数据暂时不可用") from exc
    text = payload.get("text") if isinstance(payload, dict) else None
    if not isinstance(text, str):
        raise RuntimeError("ERP 数据查询返回格式异常")
    return text


@tool
def query_erp(resource: str, params: dict | None = None) -> str:
    """查询兴华 ERP 的业务数据（只读，不会修改任何业务记录）。

    resource 取值与可传参数：
    - shop.list：店铺列表（无参数）
    - warehouse.list：仓库列表（无参数）
    - goods.search：商品搜索，params: {"keyword": "商品名"}
    - goods.sku：SKU 搜索，params: {"keyword": "SKU编码或名称"}
    - inventory.list：库存列表，params: {"availableLte": 可用库存上限, "limit": 条数}
    - order.list：订单列表，params: {"days": 天数, "orderStatus": 订单状态码, "limit": 条数}
    - refund.list：退款列表，params: {"days": 天数, "reason": 退款原因, "goodsName": 商品名, "limit": 条数}
    - purchase.list：采购单列表，params: {"days": 天数, "status": 采购状态码, "limit": 条数}
    - logistics.waybill：物流运单，params: {"days": 天数, "status": 运单状态码, "limit": 条数}
    - stock.in：入库单，params: {"days": 天数, "stockInType": 入库类型, "limit": 条数}
    - stock.out：出库单，params: {"days": 天数, "type": 出库类型, "limit": 条数}
    - member.list：会员列表，params: {"keyword": 姓名或手机号, "limit": 条数}
    - supplier.list：供应商列表，params: {"keyword": 名称关键词, "limit": 条数}

    订单状态码：0 新订单、1 待发货、2 已发货、3 已完成、11 已取消、12 退款中、13 已关闭、21 待付款。
    返回 Markdown 表格，可能附带「达到上限」等提示；据此判断是否需要换条件再查。
    """
    if not isinstance(resource, str) or not resource.strip():
        return "查询失败：resource 不能为空"
    if params is None:
        params = {}
    if not isinstance(params, dict):
        return "查询失败：params 必须是对象"
    try:
        return query_backend(resource.strip(), params)
    except RuntimeError as exc:
        return str(exc)
