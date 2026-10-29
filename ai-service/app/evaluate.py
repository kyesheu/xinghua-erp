"""离线业务口径评测。不会调用模型；可额外检查人工提供的候选回答。"""
import argparse
import json
from pathlib import Path
from pydantic import ValidationError
from app.workflows.management import ManagementFacts, run_management


def answer_errors(case, answer):
    if not isinstance(answer, str) or not answer.strip():
        return ["candidate_answer_invalid"]
    failures = []
    for required in case.get("answerRequired", []):
        if required not in answer:
            failures.append("missing_answer_claim:" + required)
    for forbidden in case.get("forbidden", []):
        if forbidden in answer:
            failures.append("unsupported_claim:" + forbidden)
    return failures


def evaluate(dataset, answers=None):
    results = []
    for case in dataset["cases"]:
        failures = []
        try:
            facts = ManagementFacts.model_validate({**case["facts"], "useModel": False})
            if case.get("reject"):
                failures.append("invalid_contract_was_accepted")
            else:
                result = run_management(facts)
                if [section["source"] for section in result["sections"]] != case["sources"]:
                    failures.append("section_order")
                text = json.dumps(result, ensure_ascii=False)
                for required in case.get("required", []):
                    if required not in text:
                        failures.append("missing_fact_or_limitation:" + required)
                for forbidden in case.get("forbidden", []):
                    if forbidden in text:
                        failures.append("unsupported_claim:" + forbidden)
                if answers is not None:
                    if case["id"] not in answers:
                        failures.append("candidate_answer_missing")
                    else:
                        failures.extend(answer_errors(case, answers[case["id"]]))
        except ValidationError:
            if not case.get("reject"):
                failures.append("valid_contract_rejected")
        results.append({"id": case["id"], "passed": not failures, "failures": failures})
    return {"datasetVersion": dataset["version"], "mode": "offline_rules", "results": results,
            "passed": sum(item["passed"] for item in results), "total": len(results),
            "limitation": "固定业务契约和回答规则检查，不代表模型整体准确率；未调用真实模型。"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cases", type=Path, default=Path(__file__).resolve().parents[1] / "evals/business_cases.json")
    parser.add_argument("--answers", type=Path)
    args = parser.parse_args()
    dataset = json.loads(args.cases.read_text(encoding="utf-8"))
    answers = json.loads(args.answers.read_text(encoding="utf-8")) if args.answers else None
    report = evaluate(dataset, answers)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report["passed"] != report["total"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
