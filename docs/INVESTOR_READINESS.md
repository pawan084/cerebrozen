# CereBro — Investor Readiness

> What seed-stage diligence will measure CereBro against, and the gap list to close.
> Benchmarks below are from the 2026-07-03 deep-research pass (adversarially verified,
> 3-0 unless noted; primary sources cited). Vendor-panel caveat: RevenueCat/Adapty figures
> come from apps instrumented with those SDKs (selection/survivorship bias), are mostly
> Health & Fitness or cross-category medians — not mental-wellness-specific — and reflect
> 2025 data. Companions: [PRD.md](PRD.md), [SLEEP_TRACKING.md](SLEEP_TRACKING.md),
> [WEB_APP_PLAN.md](WEB_APP_PLAN.md), [TODO.md](TODO.md).

## 1. The numbers investors will benchmark us against

| Metric | Median | Top quartile / P90 | Source |
| --- | --- | --- | --- |
| Install → trial | 6–11 % | 20.3 % (P90) | [RevenueCat 2025/2026](https://www.revenuecat.com/state-of-subscription-apps/), [Adapty 2026](https://adapty.io/state-of-in-app-subscriptions/) |
| Trial → paid (Health & Fitness — best of any category) | 35–42 % | > 51.4 % | RC 2026 (37.7 %), [Adapty H&F](https://adapty.io/blog/health-fitness-app-subscription-benchmarks/) (35.0–42.2 %) |
| Install → paid, freemium (D35) | 2.1–2.2 % (H&F 2.9 %) | > 6.2 % | RC 2025/2026 (hard paywall ~5× higher at 10.7–12.1 %) |
| Monthly-plan subscriber retention @ 1 yr | ~17 % | — | RC 2025 (18.8→17.0 % YoY), Adapty (17 %; 43 % @ D90) |
| Annual-plan Year-1 retention | 27–44 % (edition-dependent range) | — | RC 2025 vs RC 2026 — different cohort definitions; treat as range |
| First-renewal retention, H&F (lowest category) | 30.3 % | — | Adapty 2026 (2-1 vote; plan-mix skews it) — ~70 % churn before first renewal |
| Renewal-to-renewal retention among surviving payers | 70–84 % | 86 % (US late-stage) | Adapty 2025 |
| **India (IN/SEA) reality check** | | | |
| Year-1 LTV per payer | **$14** (vs $32 NA, $23 global) | — | RC 2026 (medium confidence — India not standalone; grouped IN/SEA) |
| Trial → paid, IN/SEA | 15.2 % (lowest region) | > 25.0 % | RC 2026 |
| D35 download → paid, IN/SEA | 1.37 % (vs 2.56 % NA) | — | RC 2026 |

**Trial-design levers (correlational — A/B inputs, not guarantees):** 17–32-day trials
convert at 42.5 % median vs 25.5 % for <4-day; 55.4 % of 3-day-trial cancellations happen
Day 0; **89.4 % of trial starts happen Day 0** — the first-session paywall is the
highest-leverage monetization surface; H&F skews to 4–7-day trials
([RC 2026](https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026/), Adapty).

**Plan mix:** H&F is the highest-LTV category ($1.21 median 1-yr install LTV globally;
$0.63 revenue/install @ 60 d vs $0.31 all-category) and **annual plans generate 57–59 %
of H&F subscription revenue** with the best 12-month LTV of any plan length (Adapty + RC,
independently). India caveat: fewer users convert, but payers who do pay spend
comparably — supports keeping the premium ₹1,499 tier.

## 2. Funding backdrop (favorable, but top-heavy)

US digital-health funding hit **$14.2 B in 2025 (+35 % YoY, best since 2022)**;
fitness & wellness vaulted to the **3rd most-funded value proposition ($2.0 B/44 deals)**;
**AI-enabled companies captured 54 % of dollars with a ~19 % deal-size premium**
([Rock Health year-end 2025](https://rockhealth.com/insights/2025-year-end-digital-health-funding-overview-a-tale-of-two-markets/)).
Qualifiers verified with the claim: the recovery is concentrated (remove the top nine
companies and 2025 trails 2024; Oura's $900 M is ~half the wellness total) and this is
US-only venture data. Net: an AI-companion wellness app is in the funded lane, but the
bar for evidence of traction is set by the winners.

## 3. Top gaps to close (ordered)

1. **We can't report a single metric.** ~~The "no trackers" promise currently means no
   D1/D30 retention, no conversion funnel, no churn.~~ **Instrument shipped 2026-07-03**:
   `GET /admin/metrics/overview` + the admin Analytics tab compute DAU/WAU/MAU,
   signup-cohort D1/D7/D30 retention, the activation funnel, and 7-day engagement as
   first-party SQL aggregates (no third-party SDKs, no device IDs, no content read).
   Remaining: disclose the first-party measurement in the privacy hub copy, and let the
   numbers accumulate post-launch.
2. **Not live.** Benchmarks attach to a shipped product. Phase 0/1 of the PRD roadmap
   (TestFlight → App Store v1) precedes everything in this document.
3. **No annual plan.** Pricing is monthly-only; the category earns 57–59 % of revenue on
   annual plans. Action: add annual SKUs (intro price), design a 7-day trial, and treat
   the first-session paywall as the primary experiment surface (89.4 % of trial starts
   are Day 0).
4. **Model the raise on India numbers, not US decks.** $14 Y1 LTV/payer, 15.2 %
   trial-to-paid, 1.37 % D35 install-to-paid — then show the two levers that blend it
   upward: global (US) App Store distribution and the ₹1,499 premium tier.
5. **Retention is the category's weak point — make it our proof.** H&F has the worst
   first-renewal retention (30.3 %). CereBro's thesis (daily plan + streak + companion +
   sleep loop) is precisely a retention machine — instrument it (gap 1) and lead the
   pitch with D30/renewal curves the moment they exist.
6. **Clinical credibility is a valuation driver we can cheaply bank.** Ship the
   CBT-I-informed sleep program citing conservative meta-analytic effects (ISI SMD −0.85,
   depression −0.47 — [SLEEP_TRACKING.md](SLEEP_TRACKING.md)), recruit a named clinical
   advisor, and document the crisis-safety design (already built: region-aware resources,
   consent-gated escalation, never-blocks scanning) as a diligence artifact.
7. **B2B needs a web surface.** Employer/enterprise wellness sales (Wysa for-employers,
   Headspace for-organizations pattern) presuppose a web app + employer-facing reporting;
   see [WEB_APP_PLAN.md](WEB_APP_PLAN.md). Also unlocks web billing (Stripe) economics.
8. **Compliance posture as an asset.** Apple's health-data rules already structurally
   match our architecture (no third-party data sharing, no PHI in iCloud); add the DPDP
   Act checklist (§5) and we can hand diligence a privacy story competitors can't.

## 4. What's already investor-friendly (keep visible)

Honest feature-status PRD (rare, diligence-friendly) · 95 %+ backend test coverage with
hermetic provider degradation · crisis-safety architecture · hard account deletion +
full export · App Store receipt verification server-side · deploy/CI runbooks — the
engineering-quality story is already strong; the measurement and traction stories are not.

## 5. India funding ground truth (single-pass cited research, 2026-07-03)

Not adversarially verified — re-confirm amounts before quoting in a deck.

| Company | Most recent round (as of mid-2026) | Source |
| --- | --- | --- |
| Wysa | No new equity 2024–26; last: **$20M Series B** (HealthQuad + BII, Jul 2022) | [Wysa](https://blogs.wysa.io/blog/company-news/wysa-secures-20m-to-address-global-mental-health-demand-with-ai-digital-therapeutics) |
| Amaha (InnerHour) | Extended Series A: **~₹50 cr (~$6M)** Fireside-led (Jan 2024) + **₹50 cr at ~₹300 cr (2×) valuation** (Mar 2026) | [PR Newswire](https://www.prnewswire.com/in/news-releases/fireside-ventures-leads-funding-round-of-inr-50-crore-in-indias-leading-mental-health-organisation-amaha-302033492.html), [Entrackr](https://entrackr.com/exclusive/exclusive-mental-health-startup-amaha-raises-fresh-capital-at-2x-valuation-premium-11436328) |
| Lissun | **$2.5M pre-Series A** (RPSG, Sep 2024); follow-on ₹2.77 cr at ₹101 cr valuation (2026); acquired Being Cares (US, 2025) | [Entrackr](https://entrackr.com/2024/09/mental-health-platform-lissun-raises-2-5-mn-in-pre-series-a/) |
| Evolve | ₹2.5 cr seed (Indian Angel Network, Dec 2023); nothing since | [IAN](https://iangroup.vc/2023/12/20/evolve-raises-inr-2-5-crore-led-by-indian-angel-network/) |
| Infiheal | No verified equity; ₹2.25 cr AI-for-All grant (2026); 850K+ users | [Infiheal](https://www.infiheal.com/achievements) |
| Mave Health | **$2.1M seed** (Blume, Mar 2026) — neurotech wearable + app | [India Hood](https://www.indiahood.com/mave-health-raises-2-1m-to-transform-mental-healthcare-with-neurotechnology/) |

**The pattern in investors' own words:** what raised in India 2024–26 was clinical
credibility ("FDA Breakthrough Device Designation… one of the few clinically validated
solutions" — HealthQuad on Wysa), hybrid/"phygital" delivery (Fireside on Amaha, RPSG on
Lissun), and underserved-access/B2B stories (BII on Wysa) — **not standalone consumer
meditation apps** (the pure-app players raised no new equity in the window). This
directly weights gaps #6 (clinical credibility) and #7 (B2B web surface) above pure
consumer polish, and strengthens the sleep module's CBT-I framing as raise-relevant.

## 6. Still open (no reliable numbers found)

Concrete seed-vs-Series-A ARR/MRR bars for consumer subscription and D1/D7/D30 *usage*
retention norms did not survive verification in either research round — treat any such
numbers encountered elsewhere as unvetted. DPDP compliance: done — see
[DPDP_COMPLIANCE.md](DPDP_COMPLIANCE.md) (gap #8's diligence artifact).
