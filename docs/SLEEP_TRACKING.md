# CereBro — Sleep Tracking Module (validated plan)

> Research-backed decision + module design, from the 2026-07-03 deep-research pass
> (23 claims confirmed 3-0 by adversarial verification; 2 refuted; primary sources cited
> inline). Companions: [PRD.md](PRD.md) (inventory/roadmap), [ARCHITECTURE.md](ARCHITECTURE.md),
> [TODO.md](TODO.md) (task breakdown), [PRIVACY_LABELS.md](PRIVACY_LABELS.md).

## 1. Decision

**GO — but as "sleep awareness + guided sleep program", not phone-sensing.**

| Scope option | Verdict | Why |
| --- | --- | --- |
| Phone-only passive staging (mic/accelerometer "sleep phases") | **NO** | In home-PSG validation, none of 4 phone apps could score REM and 3/4 failed basic sleep-wake detection ([PubMed 31674096](https://pubmed.ncbi.nlm.nih.gov/31674096/)); consumer-tracker accuracy is wildly heterogeneous (macro F1 0.26–0.69, [PMC10654909](https://pmc.ncbi.nlm.nih.gov/articles/PMC10654909/)). REM detection physiologically needs EOG/EEG. The "phone apps can beat wearables" claim was **refuted 0-3** in verification — do not use it. |
| Manual sleep diary + subjective quality (morning check-in) | **YES — v1 core** | Self-monitoring alone measurably improves perceived sleep quality (PROMIS B=-1.69, [PMC7849813](https://pmc.ncbi.nlm.nih.gov/articles/PMC7849813/)); it is the data backbone for real Insights and the AASM-sanctioned "awareness" framing. |
| CBT-I-informed guided sleep program (wind-down, stimulus control, sleep hygiene) | **YES — the evidence engine** | Digital CBT-I durably improves **both insomnia and depression**: meta-analysis of 7 RCTs, n≈7,097 — ISI SMD −0.85, depression SMD −0.47, maintained at follow-up ([PMC10624170](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10624170/)); efficacy did not differ by income/race/sex/age/education in the NIMH SPREAD RCT ([PubMed 29792241](https://pubmed.ncbi.nlm.nih.gov/29792241/)). This is the mental-health-relevant differentiator vs pure sleep apps. Cite the conservative meta-analytic numbers, **not** the manufacturer-funded d=−1.94 single-app trial. |
| HealthKit read (Apple Watch stages when present) | **YES — v1.5, optional & off by default** | `HKCategoryValueSleepAnalysis` gives AASM-mapped stages (core/deep/REM) on iOS 16+ without us building sensing ([Apple docs](https://developer.apple.com/documentation/healthkit/hkcategoryvaluesleepanalysis)). Caveat: iPhone-alone mostly yields inBed/unspecified — stages effectively require a Watch, so it's an enhancer, not the base. |
| Own wearable / clinical claims | **NO** | Diagnosis/treatment claims require FDA clearance (cf. Somryst); AASM position: consumer sleep tech "cannot be utilized for the diagnosis and/or treatment of sleep disorders" but has value for **awareness** ([PMC5940440](https://pmc.ncbi.nlm.nih.gov/articles/PMC5940440/)). |

**Positioning rule (hard):** every sleep surface is framed as *non-diagnostic sleep
awareness/wellness* — same honesty bar as the rest of the app. Never claim staging
accuracy, diagnosis, or treatment. Say AASM "recognizes" (not "endorses") an adjunct role.

## 2. Platform/regulatory constraints (verified, load-bearing)

- **App Store 5.1.3(i)/5.1.1/5.1.2(iii):** health/HealthKit data may never go to
  advertising/marketing/data-mining third parties — permitted uses are health management
  and research, with permission. Matches our no-trackers posture; keep it that way.
- **5.1.3(ii):** no false/inaccurate data written into HealthKit; **no personal health
  data in iCloud** — sleep data syncs via our backend (as journal/moods do) or stays
  on-device. Never CloudKit.
- **1.4.1:** accuracy claims for health measurement need disclosed, validatable
  methodology — another reason not to market "tracking accuracy" at all.
- **AASM 2018 position (reaffirmed 2021, unamended through 2026):** non-diagnostic
  framing is sanctioned; the prohibition is "at this time" with an FDA pathway — a future
  therapeutic ambition is possible but is a different product tier entirely.
- **India DPDP Act obligations** for storing sleep/mood data server-side:
  [DPDP_COMPLIANCE.md](DPDP_COMPLIANCE.md) — note that until 13 May 2027 the SPDI Rules
  2011 govern, under which mental-health data **is** sensitive (consent + published
  policy + reasonable security, all already in place).

## 3. Module design

### v1 — "Sleep, made real" (diary + program + real insights)

The Sleep tab today is a polished audio player over static `Dummy` content with zero
tracking (audit 2026-07-03). v1 turns it into a loop: **evening wind-down → morning
check-in → weekly insight → plan adjustment.**

**Backend** (new, mirrors existing moods/journal patterns):

- `sleep_logs` table (Alembic revision): `id`, `user_id` (FK cascade), `date`,
  `bedtime`, `wake_time`, `quality` (1–5), `awakenings` (int), `source`
  (`manual | healthkit`), `note`, `created_at`. Duration derived server-side.
- `/sleep` router: `POST /sleep` (upsert by date), `GET /sleep` (range),
  `GET /sleep/summary` (weekly aggregates: avg duration, consistency = bedtime stddev,
  quality trend). Bearer auth, same shape as `/moods`.
- `insights.py`: replace the illustrative "sleep trends" strings with computed
  diary aggregates + **sleep × mood correlation** (the mental-health angle no pure
  sleep app has) — degrade to "not enough data yet" honestly.
- `agentic.py`: plan generation reads last week's sleep summary (short sleep →
  gentler plan; late bedtimes → wind-down step), extending the existing
  `Sleep better` step library.
- Nudges: new `wind_down` nudge kind scheduled from the user's target bedtime
  (reuses the existing scheduler/dispatch infra).
- Chat/Oracle: `log_sleep` tool + sleep-diary activity widget kind (confirm-before-write
  like `log_mood`).

**iOS:**

- Morning check-in card on Home + Sleep tab (one-tap: felt-quality + bed/wake times,
  pre-filled from last entry; local-first in `AppState`, additive sync like moods).
- Sleep tab: "Tonight" wind-down plan (reminder + soundscape + wind-down content),
  7-day trend strip (duration/quality dots — real data only), diary history.
- Wind-down program content: CBT-I-*informed* psychoeducation + stimulus-control
  nudges as content items (kind `sleep`), served from `/content` — this finally
  executes the existing "migrate Sleep rails off `Dummy`" TODO in the same stroke.
- All new UI gated under `-resetState` determinism rules (no new async surprises in
  UITests).

**Cross-stack contract additions** (ARCHITECTURE table): sleep log schema + `source`
enum, `wind_down` nudge kind, `log_sleep` widget kind — backend + iOS in one commit.

### v1.5 — HealthKit enhancer (optional, off by default)

- Read `HKCategoryValueSleepAnalysis` (stages when a Watch feeds them; inBed/unspecified
  otherwise) to pre-fill the diary; user confirms — manual stays the source of truth.
- Entitlement + purpose strings + PRIVACY_LABELS.md gains the Health & Fitness data
  category (linked-to-user, not tracking) — same honest-labels process as before.
- Consider writing **Mindful Minutes** for completed wind-downs (true data, allowed);
  never write inferred sleep we didn't measure (5.1.3(ii)).

### Explicit non-goals

- No microphone/accelerometer overnight sensing, no smart alarm, no snore detection.
- No "sleep score" implying measurement precision; trends are shown as ranges/dots.
- No ISI/PSQI verbatim in-product until licensing is verified (both instruments carry
  copyright; commercial use typically needs permission — tracked in TODO). A
  plain-language 1–5 baseline (already designed, removed from onboarding) returns
  contextually instead and feeds the same `sleep_logs`/insights pipeline.

## 4. Why this strengthens the product story

- Converts the Sleep tab from static content into a daily retention loop wired to the
  differentiators (plans, insights, companion) — and makes today's illustrative
  Insights honest by giving them real data.
- Hardware-free and evidence-aligned: the validated effect (dCBT-I on insomnia **and**
  depression) is exactly the mental-health × sleep intersection CereBro occupies.
- Zero new privacy exposure model: same consent, same additive sync, same no-third-party
  rule — and Apple's health-data rules structurally reward the existing architecture.
- Investor framing (benchmarks pending the follow-up research round in
  [INVESTOR_READINESS.md](INVESTOR_READINESS.md)): engagement-driving, clinically
  literate, and honestly scoped — no accuracy claims a diligence pass would puncture.

## 5. Rollout order

1. ✅ 2026-07-03 — Backend: `sleep_logs` + `/sleep` + summary + tests.
2. ✅ 2026-07-03 — iOS: morning check-in + Sleep-tab trends, local-first + sync.
3. ✅ 2026-07-03 — Content: wind-down guide via `/content` (`wind_down` kind), Sleep
   rails server-driven with offline fallback.
4. ✅ 2026-07-03 — Insights (real Sleep metric + gated sleep×mood note), sleep-aware
   plans, bedtime-anchored `wind_down` nudges, `log_sleep` Oracle tool +
   `sleep_checkin` widget.
5. ✅ 2026-07-03 — v1.5 HealthKit read: opt-in, off-by-default check-in pre-fill
   (read-only; user confirms; `source: healthkit`); entitlement + purpose string +
   privacy-label row shipped. Remaining owner step: HealthKit capability on the
   App ID (device builds).
6. ✅ 2026-07-03 — Web parity: sleep diary page in `apps/app` (morning check-in,
   `enough_data`-honest weekly summary, history) — see [WEB_APP_PLAN.md](WEB_APP_PLAN.md).
