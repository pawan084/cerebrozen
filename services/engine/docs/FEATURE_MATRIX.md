# Feature list — what exists, what doesn't

Checked against the running code, not remembered. Regenerated after four features shipped that
weren't here when this was first written.

**Totals:** 15 agents · 35 endpoints · 1,443 tests · 98.7% branch coverage.

Legend: **✅ shipped** · **⚠️ partial** · **❌ not built**

---

## 1. Coaching engine — the part that works

| Feature | | Notes |
|---|---|---|
| Deterministic multi-agent arc | ✅ | Routing is code. Reproducible sessions. An auditor can read the graph. |
| Three coaching methods | ✅ | Routed by a structured decision the graph enforces. **The taxonomy is the incumbent's IP — see [WHITE_LABEL.md](WHITE_LABEL.md).** |
| Rehearsal / role-play | ✅ | Practice against a profiled counterpart. The most distinctive thing in the product. |
| Situational-judgement simulation | ✅ | |
| Action commitments | ✅ | Structured cards. **A session cannot close without one** — that gate is code. |
| Contract monitors + in-turn repair | ✅ | An agent that drops a routing decision is caught and re-prompted. Was failing ~1 handoff in 6, silently. |
| Stuck-stage watchdog | ✅ | Built after a live 78-turn incident. |
| Pattern reflection across sessions | ✅ | |
| Mood capture | ✅ | **Disable-able — see §4.** |
| Coachability intake | ✅ | **Disable-able — see §4.** Also: ~8 questions, up to 20 turns. Fine for B2B, **fatal for consumer onboarding.** |
| Micro-learning delivery | ⚠️ | The agent works. **The knowledge base is empty — see §5.** |
| Prompts as versioned data | ✅ | Coaches edit behaviour; content-hashed, validated, reversible. No release needed. |
| Evaluation harness | ✅ | Coaching quality becomes a number, on every change. |
| 7-day check-in scheduler | ⚠️ | Decides **who is due**. Nothing delivers it — see §5. |

## 2. Safety

| Feature | | Notes |
|---|---|---|
| Deterministic crisis takeover | ✅ | Zero tokens, model never consulted, cannot be persuaded. |
| Multilingual lexicon screen | ✅ | ~20 languages, 1ms, free, works air-gapped. **Alone it catches 1 implicit disclosure in 22.** |
| **Crisis classifier** | ✅ | **New.** Runs on everything the lexicon lets through. **1/22 → 20/22 measured.** ~$0.00014/session. |
| **Escalation to a human** | ✅ | **New.** Notifies a designated contact. Sends a *signal*, never the disclosure. Logs at ERROR if unconfigured. |
| Crisis reply in the user's language | ✅ | 7 languages written; detection spans ~20. The gap logs itself. |
| Red-team scorecard | ✅ | Published, including the bad number. Pinned so it can't regress. **Reproducible:** `python -m scripts.redteam_report` scores the same scenarios as CI and reprints 1/22 — a buyer can run it. |

## 3. Security & privacy

| Feature | | Notes |
|---|---|---|
| **GDPR erasure** | ✅ | **New.** All six locations, three databases. **Verifies** and returns 500 if anything survived. |
| **Data export (right of access)** | ✅ | **New.** Same registry as erasure — you can't export what you forgot you kept. |
| Rate limiting on paid endpoints | ✅ | Keyed on the signed JWT subject, never the request body. |
| Dev auth bypass cannot reach prod | ✅ | Refused outside a development-class `ENV`, logged at ERROR. |
| Tenant isolation guard | ✅ | A second tenant **cannot** inherit the first's database or bucket by accident. |
| Secret scanning in CI | ✅ | gitleaks + `.env` is-ignored gate. |
| At-rest encryption of transcripts | ❌ | Application layer does nothing. Depends entirely on your volume encryption. |
| SOC 2 / ISO | ❌ | None held. None claimed. |

## 4. The EU AI Act switch

| Feature | | Notes |
|---|---|---|
| **Regulated-workplace mode** | ✅ | **New.** `CEREBROZEN_REGULATED_WORKPLACE=true`. |
| — no emotion inference | ✅ | Refused **at the store** — the last gate before the disk. |
| — no durable person-score | ✅ | Refused **at registry load** — never registered, so no prompt edit can bring it back. |
| Aggregate-only org insight | ✅ | **Enforced now.** Platform `/orgs/me/analytics` nulls any metric under a k-anonymity `COHORT_FLOOR`; admin `OrgAnalytics` renders counts/trends only. (This row read ⚠️/unbuilt until 2026-07-17 — stale; the product exists.) |
| **Governance attestation** | ✅ | **New.** `GET /v1/governance` — machine-readable model card + AI inventory + the non-decisional guarantee, built from live config. Human companion: [MODEL_CARD.md](MODEL_CARD.md). |

## 5. Deployment & platform

| Feature | | Notes |
|---|---|---|
| Air-gapped stack | ⚠️ | **Runs** — Postgres + pgvector + Ollama, no cloud. **Coaching quality unmeasured.** |
| Postgres backend | ✅ | Mongo-compatible shim. |
| Cloud (OpenAI) backend | ✅ | ~5¢/session, 83% prompt-cache hit. |
| Prompt cache | ✅ | 48% cheaper, 23% faster per turn. |
| Circuit breaker / retry / cascade | ✅ | Docstring bug flagged: half-open releases every caller, not one probe. |
| Metrics (Prometheus) | ⚠️ | Emitted. **Nothing watches them** — no dashboards, no alerts. |
| CI | ✅ | Tests, coverage gate at 96%, workbook validation, gitleaks. |
| Voice | ⚠️ | Code exists. **livekit not installed, never exercised by any test.** |
| **Knowledge base (RAG)** | ❌ | **Retrieval layer works. It returns nothing.** No corpus on any box we control — so the coach is currently improvising, confidently. |
| **Notification delivery** | ⚠️ | The scheduler knows who's due, and `POST /v1/nudges/dispatch` now delivers a content-free signal via `CEREBROZEN_NUDGE_DELIVERY_URL` (a generic webhook; logged no-op when unset). **Still no native push/email/Slack channel** — that adapter is the remaining work. (This row read ❌ until 2026-07-17.) |
| **Auth / signup / login** | ❌ | This service **validates** JWTs. It does not mint them. |
| Analytics / dashboards | ❌ | Not built. |
| Gamification | ❌ | Not built. Not wanted. |
| Mobile app | ❌ | Not built. |

---

## The four that matter, in order

1. **RAG has no corpus.** The coach is doing the exact thing the architecture exists to prevent —
   improvising without an evidence base. Everything else is polish until this is fixed.
2. **Notification delivery.** The check-in scheduler is correct and unreachable. The accountability
   loop *is* the product, and today it cannot reach a person who closed the app.
3. **Auth.** No identity layer. Fine if another service mints tokens; fatal if not.
4. **Dashboards.** The metrics that would catch a silent regression are emitted into the void.

Everything else on the ❌ list is a choice. These four are debts.
