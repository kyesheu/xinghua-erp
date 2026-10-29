from types import SimpleNamespace
import pytest
from app.core.model_policy import RequestPolicy, UsageCallback, ModelBudgetExceeded, build_chat_model


def test_total_input_and_call_limits_reject_before_next_model(monkeypatch):
    monkeypatch.setenv("ERP_AI_MAX_MODEL_CALLS", "2")
    policy = RequestPolicy()
    policy.begin(10); policy.begin(10)
    with pytest.raises(ModelBudgetExceeded): policy.begin(1)
    assert policy.calls == 2


def test_usage_counts_real_values_and_marks_missing_usage_unknown():
    policy = RequestPolicy(); callback = UsageCallback(policy)
    policy.begin(10)
    callback.on_llm_end(SimpleNamespace(llm_output={"token_usage": {"prompt_tokens": 11, "completion_tokens": 5}}, generations=[]))
    assert policy.usage() == {"calls": 1, "inputTokens": 11, "outputTokens": 5, "known": True}
    policy.begin(1)
    callback.on_llm_end(SimpleNamespace(llm_output={}, generations=[]))
    assert not policy.usage()["known"]


def test_factory_enforces_output_and_retry_limits_without_network(monkeypatch):
    import langchain_openai
    captured = {}
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", lambda **kwargs: captured.update(kwargs) or captured)
    monkeypatch.setenv("ERP_AI_MAX_OUTPUT_TOKENS", "32")
    result = build_chat_model(max_tokens=9000, max_retries=10, timeout=20)
    assert result["max_tokens"] == 32 and result["max_retries"] == 0
    assert isinstance(result["callbacks"][0], UsageCallback)


def test_context_and_deadline_are_checked(monkeypatch):
    monkeypatch.setenv("ERP_AI_MAX_INPUT_CHARS", "20")
    with pytest.raises(ModelBudgetExceeded): RequestPolicy().begin(21)
    monkeypatch.setenv("ERP_AI_REQUEST_SECONDS", "1")
    with pytest.raises(ModelBudgetExceeded): RequestPolicy(started=0).begin(1)


def test_cancellation_prevents_further_calls_and_stream_chunks():
    policy = RequestPolicy(); policy.cancelled.set()
    with pytest.raises(ModelBudgetExceeded): policy.begin(1)
    with pytest.raises(ModelBudgetExceeded): UsageCallback(policy).on_llm_new_token("x")


def test_tools_have_their_own_request_limit(monkeypatch):
    monkeypatch.setenv("ERP_AI_MAX_TOOL_CALLS","2")
    policy=RequestPolicy();policy.begin_tool();policy.begin_tool()
    with pytest.raises(ModelBudgetExceeded):policy.begin_tool()
    assert policy.tool_calls==2
