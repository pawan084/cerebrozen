# CereBroZen Documentation

CereBroZen is a B2B enterprise AI coaching platform: every employee gets an
always-on performance coach, and HR/leadership gets aggregated behavioral
analytics. The commercial model follows sherlockperformance.com (enterprise
subscription, demo-gated sales, CHRO/L&D buyers); the engineering is adapted
from two reference codebases in `ref/`.

## The documents

| Doc | Answers |
|---|---|
| [PRODUCT.md](PRODUCT.md) | What are we building, for whom, and what does each surface do? |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How do the services and apps fit together? What comes from which reference? |
| [COACHING_FLOW.md](COACHING_FLOW.md) | The agent graph: 18 nodes, 15 agents, routing rules, safety nets, the prompt workbook, and inherited traps. |
| [PROMPTS_SPEC.md](PROMPTS_SPEC.md) | The adaptation brief for the 15 agent prompts — contracts, placeholders, voice, length budgets, eval requirements. |
| [prompts/](prompts/README.md) | All 18 reference prompts extracted verbatim (~420K chars) + Catalog/extraction/variables tabs — the working content base. |
| [DESIGN.md](DESIGN.md) | Brand palette, typography, tokens, accessibility gates, per-surface design rules. |
| [ENGINEERING.md](ENGINEERING.md) | Standards: testing gates, CI, conventions, cross-stack contracts, repo layout. |
| [SECURITY.md](SECURITY.md) | Threat posture, tenancy, privacy modes, crisis safety, compliance mapping. |
| [LICENSING.md](LICENSING.md) | **What this codebase inherited and from whom** — the provenance inventory + the open questions for counsel. **Gates Phase 1 and below.** Read before promising anyone anything. |
| [TODO.md](TODO.md) | The prioritized build plan, phase by phase, with inherited risks from the references. |

## The reference codebases (read-only)

| Path | Codename | What we take from it |
|---|---|---|
| `ref/Agent` | AgentMan | The coaching engine: LangGraph graph, prompt workbook, safety screen, commit gate, regulated mode, evals harness. |
| `ref/Zen` | CereBro | Android app (Compose, transport, offline, auth), admin dashboard, platform backend patterns (Postgres, roles, deletion ledger), design-token sync, e2e + Caddy deploy. |
| `apps/web` | — | Already built: the marketing site (Next.js 16, coral palette). |

Rules for `ref/`: never edit it, never run its `.env` files, never reuse its
secrets (at least one live key was found committed there — see TODO P0), and
never ship its media assets.

## Status

- 2026-07-14 — docs created; marketing site live in `apps/web`; no product
  code written yet. Start at [TODO.md](TODO.md) Phase 0.
