"""模型侧的对话与分析编排：工具循环、流式输出都在这里完成。

Java 只负责组装上下文（系统提示、历史消息、分析模板）与兜底，不再直接调模型。
"""

import json
from typing import Iterator

from app.core.config import model_settings
from app.tools.erp import query_erp

# 单次请求的上下文上限：防止前端或调用方塞入过长历史把模型上下文打满。
MAX_MESSAGES = 40
MAX_CONTENT_CHARS = 8000


class ModelNotConfigured(RuntimeError):
    """页面未配置可用模型，属于「需要人工补配置」，不是故障。"""


class ModelUnavailable(RuntimeError):
    """模型已配置但调用失败（密钥、地址、额度、网络等）。"""


def build_model(streaming: bool = False, timeout: int = 60):
    settings = model_settings()
    if not settings["enabled"]:
        raise ModelNotConfigured("未配置已启用的模型，请在 AI 智能 > 模型配置 中填写并启用")
    if not settings["api_key"] or not settings["name"]:
        raise ModelNotConfigured("模型配置不完整，请填写 API 密钥与模型名称")
    from langchain_openai import ChatOpenAI

    return ChatOpenAI(
        model=settings["name"],
        api_key=settings["api_key"],
        base_url=settings["base_url"] or None,
        timeout=timeout,
        max_retries=0,
        streaming=streaming,
    )


def _text_of(content) -> str:
    """LangChain 的内容可能是字符串，也可能是分块列表；统一取纯文本。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
        return "".join(parts)
    return ""


def _history(messages: list[dict]) -> list:
    from langchain_core.messages import AIMessage, HumanMessage

    history = []
    for item in messages[-MAX_MESSAGES:]:
        role = item.get("role") or ""
        content = (item.get("content") or "")[:MAX_CONTENT_CHARS]
        if not content:
            continue
        history.append(HumanMessage(content=content) if role == "user" else AIMessage(content=content))
    return history


def event(kind: str, content: str = "") -> dict:
    return {"type": kind, "content": content}


def stream_agent(system: str, messages: list[dict]) -> Iterator[dict]:
    """带工具的多轮流式对话：模型自行决定查哪些数据，逐段产出文本。"""
    try:
        model = build_model(streaming=True)
    except (ModelNotConfigured, ModelUnavailable) as exc:
        yield event("error", str(exc))
        return

    from langgraph.prebuilt import create_react_agent

    agent = create_react_agent(model, [query_erp], prompt=system)
    try:
        for chunk, meta in agent.stream({"messages": _history(messages)}, stream_mode="messages"):
            if not isinstance(meta, dict) or meta.get("langgraph_node") != "agent":
                continue
            text = _text_of(getattr(chunk, "content", ""))
            if text:
                yield event("message", text)
    except Exception as exc:  # 模型/网络/工具异常统一转成可读事件，避免半截流
        yield event("error", f"AI 服务调用失败：{exc}")
        return
    yield event("done")


def run_text(system: str, prompt: str, timeout: int = 60) -> str:
    """一次性生成：简报这类结构固定、无需工具的输出走这里。"""
    from langchain_core.messages import HumanMessage, SystemMessage

    try:
        model = build_model(timeout=timeout)
    except ModelNotConfigured as exc:
        raise ModelNotConfigured(str(exc)) from exc
    try:
        response = model.invoke([SystemMessage(content=system), HumanMessage(content=prompt[:MAX_CONTENT_CHARS])])
    except Exception as exc:
        raise ModelUnavailable(f"AI 服务调用失败：{exc}") from exc
    return _text_of(getattr(response, "content", ""))


def ping() -> str:
    """连通性自检：用页面保存的配置发一次最小请求。"""
    reply = run_text("你只需回应。", "ping", timeout=15)
    return reply[:200]
