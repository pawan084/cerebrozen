# services/engine — Coaching Engine

Not built yet (Phase 1, see `docs/TODO.md`). Will be adapted from
`ref/Agent` (FastAPI + LangGraph): the governed session arc, prompt
workbook, safety pipeline, regulated mode, evals. Key adaptation work:
`CEREBROZEN_*` rename sweep, Postgres-first storage, app-layer `org_id`
tenancy. See `docs/ARCHITECTURE.md` §"Backend 1".

Already present: `agent_prompts.xlsx` — the CereBroZen workbook fork
(15 agents, rebranded, rewritten environment wrapper, clean Catalog).
Provenance and adaptation rules: `docs/PROMPTS_SPEC.md`.
