"""Evaluation harness — does the agent emit what the graph ROUTES on?

    python -m scripts.eval                      # current provider (.env)
    python -m scripts.eval --provider ollama    # the offline model
    python -m scripts.eval --compare            # openai vs ollama, side by side
    python -m scripts.eval --repeat 3           # 3 samples per case (LLMs are stochastic)

Why this exists: the graph is deterministic, so it is only as reliable as the structured
signals the agents emit. A prompt edit — or a model swap — can silently stop emitting one,
and routing then takes the fallback path forever without erroring. Nothing else in the
repo catches that.

It runs the REAL pipeline: prompts composed by `guardrails.build_system_prompt`, output
parsed by `tools.parse_control`. A pass here means what ships works.

Exit code is non-zero when the score drops below --min-score, so this can gate a merge.
"""
from __future__ import annotations

import argparse
import os
import re
import statistics
import time
from typing import Any, Dict, List, Optional

# Load .env BEFORE anything imports the provider factory — it decides openai-vs-mock by
# looking for an API key at import time, so a late load silently gives you the MOCK
# provider and a meaningless score. (It did exactly that on the first run of this file.)
from app.env_loader import load_local_env

load_local_env()

PLACEHOLDER = re.compile(r"\{[A-Za-z0-9_.&\-]+\}")


def _run_case(case: Dict[str, Any], ctx: Dict[str, Any]) -> Dict[str, Any]:
    from app.graph.guardrails import build_system_prompt
    from app.graph.runtime import get_client, get_registry
    from app.graph.tools import parse_control
    from app.llm.responses_client import model_for

    reg = get_registry()
    stage = case["stage"]
    prompt = reg.get(stage)
    if not prompt.strip():
        return {"skipped": "prompt not authored"}

    system_prompt = build_system_prompt(
        reg.environment, prompt, None, ctx,
        {"user_message": case["message"], "conversation_history": case["message"]},
        invoking_agent=stage,
    )
    model = model_for(stage, catalog_model=reg.model_for(stage))

    t0 = time.perf_counter()
    resp = get_client().generate(
        system_prompt=system_prompt, user_prompt=case["message"], model=model, stage=stage,
    )
    latency = time.perf_counter() - t0

    reply, handoff, path = parse_control(resp.text)
    exp = case["expect"]
    checks: Dict[str, bool] = {}

    if "coaching_path" in exp:
        want = exp["coaching_path"]
        checks["coaching_path"] = (path in ("CIM", "CBT", "CH")) if want == "ANY" else (path == want)
    if exp.get("non_empty_reply"):
        checks["non_empty_reply"] = bool(reply.strip())
    if exp.get("no_placeholder_leak"):
        checks["no_placeholder_leak"] = not PLACEHOLDER.search(reply)

    return {
        "passed": all(checks.values()) if checks else False,
        "checks": checks,
        "got_path": path,
        "reply": reply[:70],
        "latency": latency,
        "prompt_tokens": resp.prompt_tokens,
    }


def run(provider: Optional[str], repeat: int) -> Dict[str, Any]:
    from evals.cases import CASES, default_context

    if provider:
        os.environ["CEREBROZEN_LLM_PROVIDER"] = provider
        if provider == "ollama":
            os.environ.setdefault("CEREBROZEN_MODEL_OVERRIDE",
                                  os.environ.get("CEREBROZEN_OLLAMA_MODEL", "gemma4:latest"))
    from app.llm.providers import reset_provider

    reset_provider()

    ctx = default_context()
    results: List[Dict[str, Any]] = []
    print(f"\n  {'case':<16} {'result':<26} {'lat':>6}")
    print("  " + "-" * 54)

    for case in CASES:
        runs = []
        for _ in range(repeat):
            try:
                runs.append(_run_case(case, ctx))
            except Exception as exc:  # noqa: BLE001
                runs.append({"passed": False, "error": str(exc)[:60], "latency": 0.0})
        if runs and runs[0].get("skipped"):
            continue

        passes = sum(1 for r in runs if r.get("passed"))
        lat = statistics.mean([r.get("latency", 0) for r in runs])
        ok = passes == len(runs)
        mark = "PASS" if ok else ("FLAKY" if passes else "FAIL")

        detail = ""
        if not ok:
            first = runs[0]
            if first.get("error"):
                detail = first["error"]
            elif "coaching_path" in case["expect"]:
                detail = f"path={first.get('got_path')} want={case['expect']['coaching_path']}"
            else:
                detail = ",".join(k for k, v in (first.get("checks") or {}).items() if not v)

        print(f"  {case['id']:<16} {mark:<5} {passes}/{len(runs)}  {detail[:18]:<18} {lat:>5.1f}s")
        results.append({"id": case["id"], "passed": ok, "rate": passes / max(len(runs), 1)})

    score = sum(r["rate"] for r in results) / max(len(results), 1)
    hard = sum(1 for r in results if not r["passed"])
    print("  " + "-" * 54)
    print(f"  score {score:.0%}   ({len(results) - hard}/{len(results)} cases fully passing)")
    return {"score": score, "results": results}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--provider", choices=["openai", "ollama", "mock"])
    ap.add_argument("--repeat", type=int, default=1, help="samples per case (LLMs are stochastic)")
    ap.add_argument("--compare", action="store_true", help="openai vs ollama side by side")
    ap.add_argument("--min-score", type=float, default=0.0, help="fail the run below this")
    args = ap.parse_args()

    if args.compare:
        scores = {}
        for p in ("openai", "ollama"):
            print(f"\n{'=' * 56}\n  PROVIDER: {p}\n{'=' * 56}")
            scores[p] = run(p, args.repeat)["score"]
        print(f"\n{'=' * 56}")
        print(f"  openai {scores['openai']:.0%}   ollama {scores['ollama']:.0%}   "
              f"delta {scores['ollama'] - scores['openai']:+.0%}")
        print(f"{'=' * 56}\n")
        worst = min(scores.values())
        return 0 if worst >= args.min_score else 1

    score = run(args.provider, args.repeat)["score"]
    if score < args.min_score:
        print(f"\n  FAILED: {score:.0%} < required {args.min_score:.0%}\n")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
