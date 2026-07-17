# Model card & AI inventory

This is the human-readable companion to `GET /v1/governance`, which returns the same facts
as JSON, assembled from the **running deployment's** config (see `app/governance.py`). If
this page and the endpoint ever disagree, the endpoint is right — it is generated, this is
written.

## What the system is

A deterministic multi-agent coaching engine. Routing between the ~15 agents is **code**, not
a model decision; the model supplies words inside stages it does not get to reorder, and it
cannot talk its way past a gate (e.g. a session cannot close without a saved commitment).
The full, live agent list — each with its purpose and governance flags — is the
`ai_inventory` array of the endpoint.

## The non-decisional guarantee

**Coaching outputs are never an input to hiring, promotion, termination, task allocation, or
performance evaluation.** This is not a policy line bolted on top — it is the composition of
three enforced mechanisms:

- **Counts, never content.** No admin/HR surface or API exposes a transcript, journal, or
  commitment body. There is nothing individual for a manager to act on.
- **Aggregate-only analytics** with a k-anonymity floor (platform): any metric with fewer
  than *N* distinct contributors is nulled server-side.
- **Regulated-workplace mode** (the shipped default): emotion inference and durable
  per-person scoring are refused at the store. See `config._REGULATED` and
  `tests/test_regulated_workplace.py`.

Every row of the AI inventory carries `decisional: false`, and a test
(`tests/test_governance.py`) fails the build if any agent is ever marked otherwise.

## Emotion inference & person-scoring (EU AI Act Art. 5 / Annex III)

Two agents touch legally-loaded territory: `feedback_mood_capture_agent` (mood) and
`pattern_agent` (cross-session profiling). Both are **off by default** and gated at the
store; the inventory reports their live `active` state so the attestation cannot claim a
posture the deployment doesn't hold. Turning them on is a conscious, contract-level decision
(`CEREBROZEN_REGULATED_WORKPLACE=false`), never a drift.

## Certifications

**None held, none claimed** — reported as such (`soc2 / iso27001 / iso42001: false`) rather
than left blank. ISO 42001 (AI management system) is the fastest-rising enterprise
requirement; this card + the inventory + the runtime attestation are the substrate an ISO
42001 effort would build on, not a substitute for the audit.

## Not legal advice

This document makes the governance question *answerable* and the answer *checkable*. It does
not substitute for counsel.
