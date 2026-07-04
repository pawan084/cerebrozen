# CereBro — India DPDP Act Compliance

> Digital Personal Data Protection Act 2023 + DPDP Rules 2025 (G.S.R. 846(E), notified
> 13/14 Nov 2025) as they apply to CereBro storing mood/journal/chat/sleep data of Indian
> users on our own backend. Single-pass research (2026-07-03) against the gazette texts
> ([Act](https://www.meity.gov.in/static/uploads/2024/06/2bf1f0e9f04e6fb4f8fef35e82c42aa5.pdf),
> [Rules](https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf),
> [PIB explainer](https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf))
> with DLA Piper / Latham & Watkins interpretation. **Not legal advice — confirm with
> Indian counsel before launch.** Companions: [PRIVACY_LABELS.md](PRIVACY_LABELS.md),
> [INVESTOR_READINESS.md](INVESTOR_READINESS.md), [TODO.md](TODO.md).

## 1. Timeline — what applies when

- **Now → 13 May 2027:** DPDP substantive obligations are NOT yet in force. The operative
  law is **IT Act s. 43A + SPDI Rules 2011**, which classify **mental-health condition
  and medical records as Sensitive Personal Data** — requiring consent, a published
  privacy policy, and reasonable security practices *today*. CereBro's existing
  consent/privacy/security posture must satisfy this regime already.
- **14 Nov 2026:** Consent Manager registration opens (optional ecosystem — we only need
  to honour consents routed through one if it materialises).
- **13 May 2027** (use the earlier of the disputed 13/14 dates): everything below becomes
  enforceable — notice/consent, security safeguards, breach reporting, retention,
  children's rules, rights SLAs, cross-border powers, penalties.

## 2. Compliance checklist (work items → owners in TODO.md)

| # | Obligation (source) | CereBro status / action |
| --- | --- | --- |
| 1 | **Itemised consent notice** — standalone (not in T&Cs), itemised data categories per specified purpose, withdrawal + complaint link (Act s. 5, Rule 3) | ✅ 2026-07-04: six per-category flags (mood, chat/AI memory, journal, sleep, voice, training) itemised on the consent screen + privacy hub + web account page, each enforced server-side at its read site. Standalone-notice wording review still due before May 2027. |
| 2 | **Language option** — notice/consent available in English or any Eighth-Schedule language (ss. 5(3), 6(3)) | Gap: UI is English-only. Minimum: consent + notice screens localizable first (Hindi/Tamil already in the localization roadmap). |
| 3 | **Withdrawal as easy as granting** + processors must also cease (s. 6(4)–(6)) | Toggles are symmetric ✅; document that consent-off stops LLM/provider processing (AI-memory-off already drops long-term history ✅). |
| 4 | **Security safeguards** (s. 8(5), Rule 6 — the ₹250 cr obligation): encryption, access control, **logs + monitoring + review**, backups, **1-yr log retention**, processor-contract security clauses | Mostly in place (TLS, bcrypt, JWT revocation, Caddy-only surface). Gaps: formal log/monitoring review, backup policy doc, and **DPA-style clauses with OpenAI/Anthropic/Deepgram/ElevenLabs/SMTP/Twilio**. |
| 5 | **Breach notification — every breach, no threshold** (s. 8(6), Rule 7): users "without delay" (plain language, consequences, mitigation, contact); Board initial "without delay" + detailed report ≤ 72 h | ✅ Runbook written 2026-07-04: [BREACH_RUNBOOK.md](BREACH_RUNBOOK.md) (roles, clock, user-notice + incident-log templates, preparedness checklist). Stricter than GDPR — no materiality filter. |
| 6 | **Retention tension with hard delete** (s. 8(7)–(8), Rule 8(3)): all fiduciaries must keep "personal data, associated traffic data and other logs of the processing" **1 year minimum** (state-access purposes) — gazette illustration: logs survive account deletion | ✅ 2026-07-04: `deletion_ledger` ships (hashed email + account age, same-transaction write, 12-month ops purge) while content hard-cascades. Scope (logs-only vs data) is contested — watch guidance. Fixed 3-yr inactivity clocks (Third Schedule) do NOT apply to us (e-commerce/social/gaming only). |
| 7 | **Grievance machinery** (ss. 8(9)–(10), 13; Rules 9, 14): publish a contact who can answer processing questions, repeat it in every rights response, ≤ **90-day** response SLA | ✅ 2026-07-04: grievance@cerebrozen.in + 90-day SLA published on web policy + in-app policy screen (owner: create the mailbox). Users must exhaust our channel before the Board. |
| 8 | **Children (under-18)** (s. 9, Rule 10): verifiable parental consent, no tracking/behavioural monitoring/targeted ads at children; ₹200 cr exposure | Our 18+ gate avoids parental flows but **self-attestation is not a safe harbour**. Actions: keep the gate + ToS 18+ restriction, don't ignore minor signals, document a takedown path; DigiLocker-token age check is the escalation option if guidance demands more. The Fourth-Schedule healthcare exemption almost certainly does **not** cover a consumer wellness app. |
| 9 | **Cross-border** (s. 16, Rule 15): blacklist model; **no restricted countries notified** as of Jul 2026 | **EU (Contabo) hosting stays permitted.** The Act still applies extraterritorially (s. 3(b)). Watch: restricted-list orders, Rule 15 foreign-government-access orders, and SDF localization (Rule 13(4)) if ever designated. |
| 10 | **SDF designation** (s. 10, Rule 13): government-notified; sensitivity of data is the first statutory factor; duties = India-based DPO, annual DPIA + audit, algorithmic due-diligence (would touch Oracle/LLM) | Unlikely first-wave (small volume), but a future class notification covering health/wellness apps is plausible — monitor MeitY. **Pursue DPIIT startup recognition** to be eligible for the s. 17(3) startup exemption if notified. |

## 3. Positioning notes

- **No special category under DPDP** — unlike GDPR Art. 9, mental-health data gets no
  statutory uplift (Latham comparison). But sensitivity feeds SDF designation odds and
  penalty calibration (s. 33(2) considers "type and nature of the data affected").
- **Avoid clinical positioning:** the Mental Healthcare Act 2017 confidentiality regime
  attaches to "mental health establishments/professionals" — CereBro's non-medical,
  non-diagnostic framing (already a product rule, see
  [SLEEP_TRACKING.md](SLEEP_TRACKING.md)) keeps us outside it. Don't drift.
- **Penalty ceilings** (enforceable May 2027): security safeguards ₹250 cr · breach
  notification ₹200 cr · children's data ₹200 cr · residual ₹50 cr. No turnover-linked
  fines; no private right of compensation.
- **Investor angle:** a documented DPDP-readiness posture is the diligence artifact
  referenced by INVESTOR_READINESS.md gap #8 — most India consumer apps won't have one.

## 4. Open items to watch

13-vs-14 date discrepancy (plan to 13 May 2027) · Rule 8(3) scope (logs vs data) ·
age-assurance guidance · any risk-threshold guidance on breach reporting · pending
notifications (SDF classes, restricted countries, s. 17(3) startup exemptions, Consent
Manager standards due before Nov 2026).
