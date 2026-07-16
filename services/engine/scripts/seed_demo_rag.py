"""Seed the RAG index from a local corpus directory (no S3).

    python -m scripts.seed_demo_rag                 # bundled rag_seed/ demo corpus
    RAG_SEED_DIR=/path/to/corpus python -m scripts.seed_demo_rag

Needs an embedding provider (OPENAI_API_KEY, or CEREBROZEN_LLM_PROVIDER=ollama with a
local model). Without one, embedding degrades and the index stays empty — check the
logs. Idempotent: records upsert by stable id, so re-running refreshes rather than
duplicating.
"""

import sys

from app.rag.seed_demo import seed_from_dir


def main() -> int:
    root = sys.argv[1] if len(sys.argv) > 1 else None
    result = seed_from_dir(root)
    print(
        f"seeded={result['seeded']} root={result['root']} "
        f"sskb_chunks={result['sskb']} cskb_chunks={result['cskb']}"
    )
    # Non-zero only when a directory was found but nothing was written (likely no
    # embedding provider) — so a cron/CI seeding step surfaces the miss.
    if result["seeded"] and result["sskb"] == 0 and result["cskb"] == 0:
        print("  WARNING: seed dir found but nothing indexed — is an embedding provider configured?")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
