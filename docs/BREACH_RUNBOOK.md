# CereBro — Personal-Data Breach Runbook

> Operational playbook for DPDP Act s. 8(6) + Rule 7 (enforceable 13 May 2027;
> notified 2025) and today's SPDI/IT Act posture. DPDP has **no materiality
> threshold** — EVERY personal-data breach triggers both notifications, with
> penalty exposure up to ₹200 cr for failure to notify. Companions:
> [DPDP_COMPLIANCE.md](DPDP_COMPLIANCE.md) (obligations map),
> [PRIVACY_LABELS.md](PRIVACY_LABELS.md). **Not legal advice** — engage Indian
> counsel on first invocation of this runbook.

## What counts as a breach

Any unauthorised processing, disclosure, acquisition, alteration, loss, or
destruction of personal data that compromises confidentiality, integrity, or
availability — including: a leaked database dump, an exposed backup, a
credential-stuffed admin account, a mis-scoped export, an LLM/voice
sub-processor incident affecting our transcripts, or ransomware that encrypts
user data (availability counts). Anonymous `product_events` rows are out of
scope by design (no personal data); everything else in Postgres is in scope,
and mood/journal/chat/sleep content is sensitive by nature.

## Roles (fill names before launch)

| Role | Who | Duty |
| --- | --- | --- |
| Incident lead | Founder (Pawan) | Declares the incident, owns the clock, approves comms |
| Technical lead | Founder / on-call engineer | Containment, forensics, evidence preservation |
| Comms drafter | Incident lead (+ counsel) | User notice, Board filings |
| Counsel | External (retain before launch) | Board interaction, legal privilege |

## The clock (from the moment we become AWARE)

| T | Action |
| --- | --- |
| T+0 | Declare the incident in writing (time-stamped) — awareness starts the statutory clocks. Open an incident log (see template). |
| T+0 → hours | **Contain**: rotate exposed credentials/keys (SECRET_KEY revokes all tokens via `token_version` semantics — bump globally if needed), isolate the VPS, snapshot disks BEFORE rebuilding, preserve Caddy/api logs. |
| Without delay | **Notify affected users** — plain language, via registered email (the account address) and an in-app/status notice: what happened, what data, likely consequences, what we're doing, what they can do, grievance contact. No materiality filter — if their data was touched, they're told. |
| Without delay | **Notify the Data Protection Board** (initial): nature/extent known so far, timing, containment underway. (Pre-May-2027: CERT-In reporting within 6 h of noticing applies under the IT Act regime — report at https://www.cert-in.org.in.) |
| ≤ 72 h | **Detailed Board report**: facts and circumstances, root cause, full scope (users × data categories), mitigation taken, user-notification evidence, remediation plan. Extensions only on written request. |
| T+1–2 wk | Post-mortem: root cause fixed and verified, runbook updated, safeguards review (Rule 6) re-run, processors' incident reports collected (Rule 6(1)(f) clauses). |

## User-notice template (plain language)

> **Subject: Important security notice about your CereBro account**
> On {date} we discovered that {what happened, one sentence}. The information
> involved was {categories: e.g. email address and journal entries between X–Y}.
> Your password was {not affected / reset as a precaution — you'll be asked to
> sign in again}. What we've done: {containment, one or two items}. What you can
> do: {actions, e.g. be alert to phishing referencing your entries}. We're
> sorry this happened. Questions or complaints: {grievance contact} — we
> respond within 90 days, and you may approach the Data Protection Board of
> India after using this channel.

## Incident-log template

`incident-YYYYMMDD.md` (keep out of the public repo): timeline (UTC), who knew
what when, systems/queries run, data categories × user counts, decisions +
rationale, copies of notices sent + delivery evidence, Board correspondence.

## Preparedness checklist (do these BEFORE any incident)

- [ ] Retain Indian counsel; store their emergency contact with the founders.
- [ ] Register on the CERT-In portal; bookmark the Board's filing channel when
      operational.
- [ ] Grievance contact live on web + app (see privacy hub) — the notice
      template references it.
- [ ] Test the "email all affected users" path (the nudge/email seams exist;
      a broadcast uses `POST /admin/nudges` + SMTP).
- [ ] Quarterly: restore-from-backup drill + this runbook re-read.
