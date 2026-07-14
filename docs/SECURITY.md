# Security, Privacy & Safety

Last updated: 2026-07-14

The marketing site already sells a specific security story (air-gapped
deployment, regulated workplace mode, routing an auditor can read, tests run
in front of the DPO, deletion as a product function). This document is the
engineering commitment behind each claim — plus the gaps the references
teach us to close *before* a customer finds them. Rule zero: **the site may
not claim anything this document doesn't back with a mechanism.**

## Tenancy (the sharpest inherited edge)

The AgentMan reference isolates tenants by deployment config (separate DB
names/buckets per deploy) and ships the incumbent tenant's real resource
names as defaults — documented there as the most dangerous white-labeling
trap. Our position:

- `org_id` is a first-class column/key on every engine store, every platform
  table, and every checkpointer thread id. Enforced in code, tested with
  cross-tenant access tests (tenant A token can never read tenant B rows —
  a dedicated test class, not an assumption).
- JWTs carry `org_id`; the engine rejects tokens without it.
- `STRICT_TENANT` boot-guard behavior is default-on: refusing to boot in
  prod with placeholder/foreign resource names.
- Single-tenant dedicated deployment (incl. air-gapped) remains available
  for customers who buy that isolation level; app-layer tenancy is the
  floor, not the ceiling.

## Privacy model

| Data | Who sees it |
|---|---|
| Coaching transcripts, journals, commitments content | The individual only. No HR/admin/API surface exposes content — endpoints don't exist ("counts never content"). |
| Commitments status (counts, completion) | The individual; org-level aggregates only for HR. |
| Analytics | Aggregated + anonymized, with a k-anonymity cohort floor (no aggregate rendered for cohorts under N; N per contract, default 8) enforced in the SQL, not the UI. |
| Emotion/mood records, person scores | **Regulated mode: not recorded at all.** Default ON for all new tenants (EU AI Act Art. 5 workplace emotion inference; Annex III worker management). Opt-out is a contract-level decision with counsel sign-off, not an admin toggle. Enforcement at the store layer (refused at write), proven by a dedicated test suite — the mechanism the site's "16 tests in front of your DPO" claim refers to. |
| Deletion & export | Product functions in the app (`/privacy/me/export`, delete with cascade + deletion ledger). Erasure must cover every store **including LangGraph checkpoints** — the reference documents that the checkpointer held 32 of 33 records; a post-erasure re-scan test is mandatory. |
| Retention | Define per-tenant retention windows for transcripts (default: contract-set, not indefinite). The reference's indefinite-retention posture is a named gap — we don't inherit it. |

## Crisis safety

Inherited architecture (keep): deterministic takeover — lexicon screen
before any model call, scripted zero-token crisis reply with regional
helplines, crisis text in code not workbook, optional escalation webhook
that sends a signal (ids + timestamp), never the disclosure content.

Inherited **weakness** (public on our own Evidence page): the lexicon
catches ~1 in 22 realistic implicit disclosures. Commitments:

1. The classifier layer (cheap model, fail-safe to lexicon) is on by
   default in every cloud deployment; the red-team catch-rate with
   classifier enabled becomes the number the Evidence page publishes.
2. The red-team suite is a release gate: no release may regress it.
3. Crisis replies localized only with native-speaker + clinical review;
   unreviewed languages fall back to English (reference rule, kept).
4. Escalation-to-human: v1 ships the webhook + ops-admin safety queue; "we
   alert a designated contact" is **not claimed** until built end-to-end
   (the Evidence page already says this honestly — keep it that way).

## Application security

- **Auth**: JWT access (short-lived) + single-use refresh rotation with
  coalesced retry (Zen pattern); Android stores refresh tokens in
  EncryptedSharedPreferences; HS512 shared secret between platform and
  engine, rotated per environment; key revocation procedure documented
  before first customer (named TODO in the reference — do not inherit it
  unresolved).
- **Roles**: server-side dependency enforcement (`org_admin`,
  `internal_admin`, user) — 403 by default; admin surfaces additionally
  gated by nonce-CSP middleware.
- **Transport/perimeter**: Caddy terminates TLS (auto-ACME), HSTS, nosniff,
  frame-deny, Permissions-Policy; API returns JSON-only CSP; DB and services
  never publish host ports (prod compose).
- **Rate limiting**: on auth endpoints and the two paid engine endpoints
  (session start / turn), reference limits as starting points.
- **Prompt injection**: RAG chunks and any client-uploaded content are
  framed as data-not-instructions in system prompts; self-serve KB upload
  does not ship until this framing + eval cases exist (reference names this
  as a growing risk — we gate it).
- **Secrets**: gitleaks in CI, `.env` never committed, boot-guards refuse
  default secrets in prod. Immediate action item: keys found in
  `ref/Agent/.env` are treated as compromised (TODO P0).
- **At-rest encryption**: disk/volume encryption at minimum; app-layer
  encryption of transcript content is a stated compliance gap in the
  reference against its own enterprise promises — scheduled work here, not
  a claim we make early.

## Deployment postures

1. **Cloud (default)**: our infra, tenant-isolated, cloud LLM via the OpenAI
   model cascade (single vendor by decision — no cross-vendor fallback is
   claimed), full observability.
2. **Perimeter/air-gapped**: the engine's offline profile — Ollama local
   model (grammar-forced routing), Postgres + pgvector, codebase prompt
   source, lexicon crisis floor, zero egress. Caveat carried from the
   reference: prompt sizes must be reduced for local-model latency, and
   air-gapped coaching *quality* is unmeasured — the Evidence page already
   discloses this; measure with the first design partner before committing.

## Compliance mapping (claims → mechanisms)

| Site claim | Mechanism | Status |
|---|---|---|
| "Regulated mode, 16 tests for your DPO" | Store-layer refusal + test suite, default-on | Inherit + re-verify |
| "Routing an auditor can read" | Deterministic graph, mermaid export, reproducible sessions | Inherit |
| "Deletion is a product function" | Cascade + ledger + checkpoint re-scan test | Inherit + extend |
| "Air-gapped deployment" | Offline profile | Inherit (quality unmeasured — disclosed) |
| "Encryption in transit and at rest" | TLS everywhere; volume encryption; app-layer transcript encryption pending | Partial — scheduled |
| "SOC 2 / ISO 27001 aligned" | Marketing language only ("aligned") until an actual audit engagement | Honest wording, keep |
| GDPR / DPDP | Export/erasure/retention + DPDP checklist from `ref/Zen/docs/DPDP_COMPLIANCE.md` | Adapt checklist |

## Incident response

Adopt `ref/Zen/docs/BREACH_RUNBOOK.md` as the seed: severity ladder,
customer-notification clocks (GDPR 72h), key-rotation steps, single
communications owner. To be adapted in Phase 1 and rehearsed before first
enterprise tenant.
