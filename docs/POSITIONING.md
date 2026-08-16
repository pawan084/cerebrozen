# CereBro — Market Positioning & USP

> Research pass 2026-08-16. The shareable version of this lives as a deck:
> claude.ai/code/artifact/01d01938-813d-4d35-9e1c-17008e250e54
>
> Companions: [INVESTOR_READINESS.md](INVESTOR_READINESS.md) (benchmarks + the
> India funding ground truth), [PRD.md](PRD.md) (honest feature status),
> [CLAIMS_MAP.md](CLAIMS_MAP.md) (the honesty mechanism this document sells).

## 1. The one-line position

**A proactive AI wellness companion that never claims to be a clinician — built,
before it was required to be, for the safety rules regulators are now writing.**

## 2. Why now: the category's ground shifted

This is the strongest and most perishable part of the story. Between Aug 2025 and
Jul 2026 the regulatory answer to "what may an AI mental-health product be?"
arrived, and most of the category was designed before it.

| Event | Date | Why it matters to us |
| --- | --- | --- |
| Illinois WOPR Act — AI may not deliver therapy unless a licensed human does; $10,000/violation | Aug 2025 | The "AI therapist" positioning became a liability, not a differentiator |
| Nevada, Rhode Island, Maine follow with bans; Utah, New York, California, Nebraska regulate (disclosure, crisis referral, minor protection) | → Jul 2026 | Eight states now constrain the category. Disclosure + crisis routing + minor protection is the emerging floor |
| **Woebot retires its consumer app** after ~1.5M users; founder cites FDA-path cost vs LLM pace | 30 Jun 2025 | The flagship pure-play chatbot exited consumer. Being *not* a medical device is now the cheaper, more durable posture |
| No AI therapy chatbot holds FDA clearance | as of 2026 | Any pitch implying clinical status carries unpriced regulatory risk |
| FTC 6(b) inquiry into companion chatbots (7 companies), harms to minors | Sep 2025 | Companion apps without safety architecture are the ones under the microscope |

**The direction of travel** — disclose you are AI, never claim therapy, always
route to a human in crisis, protect minors, prove it — is a description of
CereBro's existing architecture, not a roadmap for it.

## 3. The USP, in four defensible pillars

Each is something a competitor would have to *rebuild*, not add.

1. **Proactive, with manners.** The companion opens the day, logs sleep and mood
   *from the conversation*, and follows up on what you actually did — while
   capping itself at one nudge a day, honouring quiet hours, and never naming
   mental health on a lock screen. Restraint is the differentiator; anyone can
   send more notifications.
2. **Safety adds, never blocks.** Crisis scanning cannot reject or edit what you
   write; it can only attach support. Three rungs (normal reply → inline concern
   card → full crisis surface), Tele-MANAS 14416 first, region-aware beyond
   India. This is the inverse of the failure mode regulators are reacting to.
3. **Honesty enforced by CI.** Every doubtable user-facing claim has a
   [CLAIMS_MAP](CLAIMS_MAP.md) row naming its mechanism *and* its test;
   `scripts/check-claims.mjs` fails the build on banned phrasing across all
   clients. We have removed our own over-claims with it. This is a diligence
   artifact competitors cannot produce retroactively.
4. **India-first, not translated later.** Hindi UI including crisis copy,
   Tele-MANAS leading every safety surface, a 13-language DPDP consent notice
   readable in any app language, all six consent categories off by default,
   offline-safe helplines, family-context notification privacy.

## 4. Positioning map

Two axes decide the category now: **proactivity** (waits to be opened ↔ reaches
out) and **clinical claim** (explicit clinical claim ↔ companion, no claim).

- **Calm / Headspace** — passive content libraries, no clinical claim. Safe,
  enormous, but they wait for you.
- **Wysa / Amaha** — clinical-leaning; Amaha is a therapy/psychiatry
  marketplace. Strong crisis pathways, and they carry the regulatory weight.
- **Replika / general companion AI** — proactive, no clinical claim, and no
  safety architecture. Precisely the group drawing FTC attention.
- **CereBro** — proactive **and** explicitly non-clinical **and** safety-
  architected. The top-right is not empty by accident: being proactive without a
  clinical claim only works if you have somewhere safe to send people, which is
  engineering rather than copywriting.

## 5. Market size — and why we quote a range

Seven firms put the **2026 global** mental-health-app market between **$8.6B and
$16.7B**, growing **14.7–19.2%** annually. India estimates disagree even more
sharply and by scope: one firm sizes India *online mental health* at $151M
(2025) → $464M (2034, 12.9% CAGR); another sizes India *mental health apps* at
$498M (2024) → $1.41B (2030, 18.5% CAGR).

**We quote the spread deliberately.** A single confident TAM figure in this
category is a choice about which report flatters you. The two figures that do not
move are the ones worth planning against: a **>84% treatment gap** and **800M+
smartphone users**.

## 6. Economics we actually plan on

From [INVESTOR_READINESS.md](INVESTOR_READINESS.md) (RevenueCat/Adapty panels;
vendor-panel bias noted there):

- **$14** Year-1 LTV per payer in IN/SEA (vs $32 NA)
- **15.2%** trial→paid in IN/SEA — the lowest region
- **1.37%** D35 install→paid in IN/SEA (2.56% NA)

Three levers, all built or code-complete: global store distribution at NA
economics, annual plans (57–59% of H&F subscription revenue, best 12-month LTV),
and B2B2C sponsored seats (portal + work coaching already exist).

**The category's weak point is our thesis.** Health & Fitness converts trials
better than any category (35–42%) but has the *worst* first-renewal retention
(30.3%). A companion that follows up is a retention machine — to be proven with
D30 curves, not asserted.

## 7. What this document must not become

Everything above is either sourced or marked as our own assessment. The deck
carries an explicit "not true yet" panel: not live, zero users, no retention
data, no named clinical advisor, iOS/web on the older shape, Hindi safety copy
unreviewed. **Keep that panel in every version.** An honest status list is rare
enough in diligence to be a signal in itself, and it is the same discipline
CLAIMS_MAP enforces on the product's copy.

## 8. Open / to refresh before quoting

- India funding rounds in §5 of INVESTOR_READINESS were single-pass research
  (2026-07-03) and were never adversarially verified — re-confirm amounts.
- Competitor rows in the deck are assessed from public product surfaces, not
  from their internals; re-check before any external use.
- Market figures are vendor reports, not audited data; the spread is the honesty.
