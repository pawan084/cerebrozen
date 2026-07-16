# Security, Privacy & Safety

Last updated: 2026-07-16

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
| Coaching transcripts, journals, commitments content | The individual only. No HR/admin/API surface exposes content — endpoints don't exist ("counts never content"). **Structural, not procedural:** content is served by the *engine* (`/v1/wellness/*`, `/v1/sessions/*`); the *platform* is the database an HR admin's token reaches, and it holds no content column and no content route. Pinned from both sides — a platform route or user-table column named `journal`/`sleep`/`mood` fails the build (`test_wellness_account.py`), and the engine grows no org-wide wellness view (`test_wellness.py`). |
| Commitments status (counts, completion) | The individual; org-level aggregates only for HR. |
| Analytics | Aggregated + anonymized, with a k-anonymity cohort floor (no aggregate rendered for cohorts under N; N per contract, default 8) enforced **server-side in the aggregation layer** (`analytics._floored`, after the COUNT query) — never in the UI, so no client can render what the floor suppressed. Rates inherit the weakest cohort of their components. |
| Emotion/mood **inferred by the system**, person scores | **Regulated mode: not recorded at all.** Default ON for all new tenants (EU AI Act Art. 5 workplace emotion inference; Annex III worker management). Opt-out is a contract-level decision with counsel sign-off, not an admin toggle. Enforcement at the store layer (refused at write), proven by a dedicated test suite — the mechanism the site's "16 tests in front of your DPO" claim refers to. |
| Mood/journal/sleep the person **wrote themselves** | Theirs, and only theirs. Self-report is not inference: the prohibition is on a system reading emotions *off* a worker, and a regulated tenant does not thereby lose their own diary. Stored in the engine, addressable only by the subject of the token (no `user_id` parameter exists on any wellness route), erased with them, and included in their export. A tenant who wants none of it stored sets `CEREBROZEN_SELF_REPORT_WELLNESS=false`. The two paths are pinned side by side in one test so nobody later "simplifies" them into one flag. |
| Consent (6 DPDP categories) | The person's, enforced rather than merely recorded. The platform signs the six flags into the access token; the engine **refuses to store** a category the person declined (403), so the toggle changes behaviour and not just a row. A withdrawal rotates the token pair, so it bites on the very next request instead of at token expiry. Never visible to the org: no HR surface reads or sets an employee's consent. |
| Deletion & export | Product functions in the app — **wired in the client 2026-07-16**; both servers had implemented them and nothing called either, so the claim had no mechanism. Because content lives in the engine and the account in the platform, neither service alone can answer "everything about me": the export is a **merge**, and a half that fails is *named* rather than silently dropped. Erasure is **ordered — engine (content) first, platform (account) second** — and the order is load-bearing: the platform's delete revokes every refresh token, so account-first would strand a failed content erasure forever with no way to authenticate and retry. The engine re-scans and returns 500 `verified:false` on a partial erasure; the client **aborts** on that and leaves the account intact rather than round it up to success (`apps/app/lib/privacy.ts`, `e2e/tests/06-privacy.spec.ts`). Erasure must cover every store **including LangGraph checkpoints** — the reference documents that the checkpointer held 32 of 33 records; a post-erasure re-scan test is mandatory. |
| Retention | Define per-tenant retention windows for transcripts (default: contract-set, not indefinite). The reference's indefinite-retention posture is a named gap — we don't inherit it. |

## Crisis safety

Inherited architecture (keep): deterministic takeover — lexicon screen
before any model call, scripted zero-token crisis reply with regional
helplines, crisis text in code not workbook, optional escalation webhook
that sends a signal (ids + timestamp), never the disclosure content.

**Helplines are per-region, and the client does not hold them** (fixed
2026-07-16; the contract had said this all along while Android shipped India's
numbers as literals to every user, and offered a region picker whose answer
nothing read). The directory lives in the engine (`app/safety/helplines.py`) —
safety code, unreachable from the prompt workbook. `GET /v1/safety/helplines`
is **total**: no input, including junk or a blank region, can return an empty
list, because a crisis screen with nothing on it is the worst outcome
available. An unknown region resolves to an international finder that routes to
the caller's own country, never to a guess — and clients' offline floor is that
same neutral list, so a first run with no network still renders something
dialable without naming a country we haven't confirmed. The region comes from
the platform's resolved `crisis_region` (the person's own choice first, then
their org's default); it is never inferred from an IP or a SIM.

The **clients render it**. Until 2026-07-16 the web app had no crisis surface at
all: the engine took over, streamed the scripted reply, and `apps/app` painted it
as an ordinary chat bubble — the takeover fired and the person saw a normal-looking
message with no way to reach anyone. `components/crisis.tsx` now renders a
`role="alert"` panel on `safety_flag == "crisis"`, seeded from the neutral floor so
it paints something dialable before any fetch resolves, then filled from
`/v1/safety/helplines` for the person's resolved region.

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
  `internal_admin`, user) — 403 by default. (Roles are enforced on the
  server; the admin's CSP below is defense-in-depth, not a role gate.)
- **Operator surfaces on the engine** require `internal_admin`
  (`app/auth/dependencies.py::require_internal_admin`). Closed 2026-07-16: the
  engine inherited the reference's single-tenant posture — `require_auth`'s
  docstring said "no role or sender checks, per design" — so **any**
  authenticated employee at **any** customer could `GET /v1/prompts/download`
  and take the whole coaching workbook, and any token could `PUT
  /v1/prompts/{stage}`, rewriting or disabling a coaching agent for **every**
  tenant (the workbook is global, not org-scoped). Found by the e2e suite on
  its first run against the composed stack, which is the only place it was
  visible. Now gated: the workbook (read, write, upload, download, reload), the
  compiled arc (`/v1/graph`, `/v1/agents`), the console runners (also a billing
  hole), `/v1/nudges`, and `/v1/safety/escalations` — the escalation queue names
  which *employees* hit the crisis screen, so an org's own HR must not read it
  either. Role refusals are **403, not 401**: a 401 tells a client its token is
  stale and invites a refresh-and-retry loop that can never succeed.
  Deliberately **not** gated: `/v1/safety/helplines` (any authenticated user —
  it backs a crisis screen) and a person's own session stage/transcript, which
  are scoped by *ownership*, the correct control for their own content
  (`test_role_gate.py` pins both halves).
- **Admin CSP**: the console sets a per-request nonce CSP in
  `apps/admin/proxy.ts` (Next 16 renamed the `middleware` convention to
  `proxy`). `script-src` carries no `'unsafe-inline'`, so an injected script
  cannot execute without that request's unguessable nonce; `'unsafe-eval'`
  appears in dev only. This forces dynamic rendering — Next stamps the nonce
  during SSR from the request's CSP header, and a prerendered page has no
  request — hence `export const dynamic = "force-dynamic"` in the admin
  layout. Two deliberate carve-outs: `connect-src` names the platform API and
  engine origins (a browser client that cannot call its own backend is a dead
  console), and `style-src` keeps `'unsafe-inline'` with **no** nonce, because
  React Flow positions every agent-flow node with a `style` attribute — a
  nonce covers `<style>` elements but never attributes, and per CSP3 a nonce
  in `style-src` would *disable* `'unsafe-inline'` and break the canvas. XSS
  defense lives in `script-src`; inline styles are a far smaller exposure.
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
- **At-rest encryption**: a **datastore-layer** concern — Postgres/Mongo
  transparent encryption, or an encrypted volume. This is deliberately NOT
  app-layer field encryption: that would add a native crypto dependency the
  project avoids (see the platform's stdlib-PBKDF2 note) and break content
  queryability, for defense the datastore layer already provides. The engine
  cannot *verify* the datastore is encrypted, so it carries the operator's
  **attestation** (`CEREBROZEN_DATASTORE_ENCRYPTED`) and surfaces it at
  `/health.storage.encrypted` (`true`/`false`/`unknown`); a deployed env that
  hasn't attested `true` gets a loud boot warning (`app/config.py`,
  `test_datastore_encryption.py`). A deployment is thus never *silently* assumed
  encrypted — the claim is only as good as the operator's declaration, and the
  declaration is visible. Turning the datastore encryption on remains a
  deployment step (docs/DEPLOY or the infra runbook), not an app feature.

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
| "Encryption in transit and at rest" | TLS everywhere; at-rest = datastore-layer (DB TDE / encrypted volume), **attested** via `CEREBROZEN_DATASTORE_ENCRYPTED` + surfaced at `/health.storage.encrypted`, deployed-env boot warning if unattested. App-layer field crypto deliberately not used (dependency + queryability trade-off). | Attestation surface shipped; enabling the datastore encryption is a deploy step |
| "SOC 2 / ISO 27001 aligned" | Marketing language only ("aligned") until an actual audit engagement | Honest wording, keep |
| GDPR / DPDP | Export/erasure/retention + DPDP checklist from `ref/Zen/docs/DPDP_COMPLIANCE.md` | Adapt checklist |

## Incident response

Adopt `ref/Zen/docs/BREACH_RUNBOOK.md` as the seed: severity ladder,
customer-notification clocks (GDPR 72h), key-rotation steps, single
communications owner. To be adapted in Phase 1 and rehearsed before first
enterprise tenant.
