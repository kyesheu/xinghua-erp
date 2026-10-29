"""所有模型入口共用的资源边界与实际用量采集，不保存提示词或密钥。"""
from contextvars import ContextVar
from dataclasses import dataclass, field
from threading import Lock, Event
from time import monotonic
import os
import logging
from uuid import uuid4

from langchain_core.callbacks import BaseCallbackHandler


class ModelBudgetExceeded(RuntimeError):
    pass


def positive_env(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


@dataclass
class RequestPolicy:
    request_id: str = field(default_factory=lambda: str(uuid4()))
    report_usage: bool = False
    started: float = field(default_factory=monotonic)
    calls: int = 0
    completed: int = 0
    tool_calls: int = 0
    input_chars: int = 0
    input_tokens: int = 0
    output_tokens: int = 0
    unknown: bool = False
    lock: Lock = field(default_factory=Lock)
    cancelled: Event = field(default_factory=Event)

    def begin(self, chars: int):
        with self.lock:
            if self.cancelled.is_set():
                raise ModelBudgetExceeded("AI 请求已取消")
            if monotonic() - self.started >= positive_env("ERP_AI_REQUEST_SECONDS", 90):
                raise ModelBudgetExceeded("AI 请求已达到总时限")
            if self.calls >= positive_env("ERP_AI_MAX_MODEL_CALLS", 4):
                raise ModelBudgetExceeded("AI 请求已达到模型调用次数上限")
            if chars > positive_env("ERP_AI_MAX_INPUT_CHARS", 12000) or self.input_chars + chars > positive_env("ERP_AI_REQUEST_INPUT_CHARS", 48000):
                raise ModelBudgetExceeded("AI 请求上下文超过限制")
            self.calls += 1
            self.input_chars += chars

    def begin_tool(self):
        with self.lock:
            if self.cancelled.is_set() or monotonic()-self.started >= positive_env("ERP_AI_REQUEST_SECONDS",90):
                raise ModelBudgetExceeded("AI 请求已取消或达到总时限")
            if self.tool_calls >= positive_env("ERP_AI_MAX_TOOL_CALLS",8):
                raise ModelBudgetExceeded("AI 请求已达到工具调用次数上限")
            self.tool_calls += 1

    def usage(self):
        with self.lock:
            return {"calls": self.calls, "inputTokens": self.input_tokens, "outputTokens": self.output_tokens,
                    "known": not self.unknown and self.completed == self.calls}


_POLICY: ContextVar[RequestPolicy | None] = ContextVar("model_request_policy", default=None)


def current_policy():
    return _POLICY.get()


class UsageCallback(BaseCallbackHandler):
    raise_error = True

    def __init__(self, policy: RequestPolicy):
        self.policy = policy
        self.started = {}

    def on_chat_model_start(self, serialized, messages, **kwargs):
        self.policy.begin(sum(len(str(message.content)) for batch in messages for message in batch))
        self.started[str(kwargs.get("run_id"))] = monotonic()

    def on_llm_start(self, serialized, prompts, **kwargs):
        self.policy.begin(sum(len(prompt) for prompt in prompts))
        self.started[str(kwargs.get("run_id"))] = monotonic()

    def on_llm_new_token(self, token, **kwargs):
        if self.policy.cancelled.is_set() or monotonic()-self.policy.started >= positive_env("ERP_AI_REQUEST_SECONDS",90):
            raise ModelBudgetExceeded("AI 请求已取消或达到总时限")

    def on_llm_end(self, response, **kwargs):
        elapsed = (monotonic()-self.started.pop(str(kwargs.get("run_id")), self.policy.started))*1000
        logging.getLogger(__name__).info("AI model requestId=%s elapsedMs=%d", self.policy.request_id, elapsed)
        usage = (getattr(response, "llm_output", None) or {}).get("token_usage")
        normalized = False
        if not usage:
            for batch in response.generations:
                for generation in batch:
                    usage = getattr(getattr(generation, "message", None), "usage_metadata", None)
                    if usage:
                        normalized = True
                        break
                if usage:
                    break
        with self.policy.lock:
            self.policy.completed += 1
            if not isinstance(usage, dict):
                self.policy.unknown = True
                return
            inp = usage.get("input_tokens" if normalized else "prompt_tokens")
            out = usage.get("output_tokens" if normalized else "completion_tokens")
            if not isinstance(inp, int) or not isinstance(out, int) or inp < 0 or out < 0:
                self.policy.unknown = True
                return
            self.policy.input_tokens += inp
            self.policy.output_tokens += out

    def on_llm_error(self, error, **kwargs):
        logging.getLogger(__name__).warning("AI model requestId=%s errorType=%s", self.policy.request_id, type(error).__name__)
        with self.policy.lock:
            self.policy.unknown = True


def build_chat_model(**kwargs):
    from langchain_openai import ChatOpenAI
    policy = current_policy() or RequestPolicy()
    kwargs["max_tokens"] = min(kwargs.get("max_tokens") or 1024, positive_env("ERP_AI_MAX_OUTPUT_TOKENS", 1024))
    kwargs["max_retries"] = 0
    kwargs["timeout"] = max(0.1, min(float(kwargs.get("timeout", 20)), 20, positive_env("ERP_AI_REQUEST_SECONDS", 90)-(monotonic()-policy.started)))
    kwargs["callbacks"] = [*kwargs.get("callbacks", []), UsageCallback(policy)]
    if kwargs.get("streaming"):
        kwargs["stream_usage"] = True
    return ChatOpenAI(**kwargs)
