# Consumer Terms of Service & Privacy — DRAFT

> **⚠️ DRAFT FOR COUNSEL REVIEW — NOT LEGAL ADVICE, NOT YET IN FORCE.**
> This is engineering's starting point for the direct-to-consumer (B2C) legal
> surface introduced with self-serve personal accounts. It must be reviewed and
> completed by a qualified lawyer before a single personal account is charged or,
> ideally, before self-serve signup is enabled in production. Every `‹NOTE›`
> marks a decision only counsel/business can make.

## Why a separate consumer contract exists

The enterprise Terms (`apps/web/src/app/terms/page.tsx`) assume the user reaches
the service **"through their organization's subscription"**, and they bound our
liability **"as set out in the applicable customer agreement."** A personal
(self-serve) account has **no organization and no negotiated agreement** — so that
liability shield does not exist for it. This document is the contract that stands
in its place. Getting the disclaimers, the liability cap, and the
not-a-medical-service framing right is the entire point: consumer mental-wellness
apps are the litigated segment of this market, and the enterprise wrapper was what
kept us out of it.

Legally load-bearing, non-negotiable pieces (safety-as-code, mirrored from the
product): **not therapy / not medical / not emergency care**, **18+ only**,
**deterministic crisis routing to real helplines**, **content stays private to the
user**. These are already enforced in code; the contract must restate them.

---

## Part A — Terms of Service (personal accounts)

### 1. The agreement and the service
CereBroZen provides AI-based wellness **coaching** for your personal use. For a
personal account, this agreement is **directly between you and CereBroZen** — there
is no organization in between. Coaching output is guidance to support your own
judgement. **It is not professional medical, psychological, legal, or financial
advice, and it is not a substitute for therapy, diagnosis, treatment, or emergency
services.** ‹NOTE: confirm the operating legal entity, address, and governing law.›

### 2. Eligibility (18+)
The service is for adults. You must be **18 or older** to create an account; you
confirm this at signup. The service is **not directed to children** and we do not
knowingly collect their data. ‹NOTE: confirm the age threshold per target market —
DPDP (India) treats minors' data distinctly; some markets use 16.›

### 3. Not an emergency or crisis service
CereBroZen is **not a crisis line, hotline, or emergency service** and cannot
summon help. If you are in danger or crisis, contact your local emergency number
or a helpline — the app surfaces these to you (the "Urgent support" screen). We do
not monitor conversations in real time and cannot guarantee any response.

### 4. Your account
Keep your credentials confidential and tell us promptly if your account may be
compromised. You are responsible for activity under your account. One person per
account; don't share it.

### 5. Subscriptions, billing, and cancellation
The core experience is **free**. **CereBro Plus** is an optional paid subscription
that unlocks additional features.

- **Price:** ‹NOTE: US $9.99/month or $59.99/year proposed — confirm per-market
  pricing, currency, and tax handling.›
- **Auto-renewal:** Plus renews automatically at the end of each period at the
  then-current price until cancelled. We'll disclose the price and renewal terms at
  purchase.
- **Where you're billed:** if you subscribe **through the Apple App Store or Google
  Play**, that store processes payment and its rules govern billing, renewal, and
  refunds — manage or cancel in your store account. If you subscribe **directly**
  (web), ‹NOTE: payment processor (Stripe) terms, and our own cancellation/refund
  policy, go here.›
- **Cancellation:** you can cancel anytime; access continues until the end of the
  paid period, after which the account reverts to Free. ‹NOTE: confirm whether any
  pro-rata refunds are offered on direct billing; app-store purchases follow the
  store's refund policy.›
- **Free features are never a paywall for safety:** crisis support, check-ins and
  breathing/grounding tools are **unlimited on the Free plan** and are never
  metered, rate-limited or withheld for non-payment.
- **What the Free plan does limit:** ordinary coaching conversations are capped at
  **five turns per day** on Free (`CEREBROZEN_FREE_TURN_DAILY_LIMIT`, enforced
  server-side). Reaching that cap never affects the safety features above.
  ‹NOTE: this cap must be stated plainly in the paid-plan comparison and at the
  point of sale, not only here. The earlier draft said Free retained "the coach's
  core availability" without naming a number — a consumer-protection regulator
  reads an unquantified availability promise narrowly, and the honest version is
  also the more defensible one.›

### 6. Acceptable use
Use the service only for lawful, personal purposes. Do not attempt to access
another user's data, probe or circumvent security controls, resell access, or use
the service to build a competing product. Do not use it to harm yourself or others;
if you're struggling, please use the crisis resources in section 3.

### 7. Your data and privacy
Your coaching conversations, journal, check-ins, and sleep entries are **private to
you** (see Part B). You own the content you provide and grant us only the rights
needed to operate the service for you. Because a personal account has no employer,
**no aggregate analytics about you are shared with anyone** — the "counts, never
content" firewall that governs organization accounts simply has no third party to
report to here.

### 8. Our intellectual property
The platform, its coaching methodology, and all associated software and content are
owned by us or our licensors. These terms grant a right to use the service — not a
licence to copy, modify, or redistribute it.

### 9. Disclaimers and limitation of liability
The service is provided **"as is"** and **"as available"** to the extent permitted
by law. We do not warrant that coaching guidance will achieve any particular
outcome. **To the maximum extent permitted by law, our aggregate liability arising
out of or relating to the service is limited to** ‹NOTE: the standard consumer cap
counsel selects — commonly the greater of fees paid in the prior 12 months or a
small fixed sum; and confirm which consumer-protection laws (e.g. certain
jurisdictions) prevent limiting particular liabilities›. **Nothing limits liability
that cannot be limited by law**, including for death or personal injury caused by
negligence where applicable.

### 10. Suspension and termination
We may suspend or terminate access for material breach, for security reasons, or
where required by law. You may stop using the service and delete your account at any
time (Settings → Delete account). On termination, data is handled as in Part B.

### 11. Governing law and disputes
‹NOTE: governing law, venue, and any arbitration/class-action-waiver clause — a
core counsel decision; consumer-arbitration clauses are unenforceable or restricted
in several jurisdictions and must be tailored per market.›

### 12. Changes
We may update these terms. For material changes we'll update this page, note the new
effective date, and notify you in-app or by email before they take effect for you.

### 13. Contact
‹NOTE: support/legal contact address.›

---

## Part B — Privacy notes specific to personal accounts

The engineering privacy model is unchanged from the enterprise product and already
enforced in code; these are the **consumer-specific clarifications** a full privacy
policy must carry:

- **Content stays with you.** Journals, moods, sleep, and coaching conversations are
  served only by the coaching engine and are private to your account. The identity
  platform (the database, orgs, billing) holds **no content column and no content
  route** — this is a schema property, not a promise (`test_wellness_account.py`).
- **Consent is opt-in, per category.** All six data-use consents default **off**; you
  turn on what you want in Privacy & memory, and can withdraw anytime. The coaching
  engine enforces this from a signed claim, so a category you haven't consented to is
  never written down.
- **No employer, no sharing.** With no organization, there is no HR/analytics
  recipient. We do not sell personal data. ‹NOTE: state any processors — payment
  provider, email, LLM/voice providers if keys are configured — and cross-border
  transfer basis (DPDP/GDPR).›
- **Your rights.** Export (Settings → Export my data), delete (Settings → Delete
  account, which tombstones identity and erases content), and forget derived AI
  memory (Pattern dashboard → Delete all memory) are all self-serve. ‹NOTE: map these
  to the specific statutory rights per market (DPDP correction/erasure, GDPR
  access/portability/erasure/objection).›
- **Payment data** is handled by the payment provider (App Store / Google Play /
  Stripe); we store only an **opaque subscription reference**, never card details
  (`models.Subscription`).
- **Children.** Not for under-18s; if we learn a child created an account we will
  delete it.

---

### Open items for counsel (summary checklist)
1. Operating entity, governing law, venue, arbitration posture — per market.
2. Consumer liability cap language and carve-outs that cannot be limited.
3. Per-market pricing, currency, tax, and refund policy (direct vs app-store).
4. Age threshold per market (18 vs 16) and children-data handling.
5. Data-processor list and international-transfer basis (DPDP May 2027, GDPR).
6. Reconcile with app-store subscription-disclosure requirements (Apple/Google).
