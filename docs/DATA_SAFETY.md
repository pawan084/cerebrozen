# App-store Data Safety — answers, grounded in the code

The **Google Play Data Safety form** and **Apple Privacy Nutrition Label** both ask, per
data type: is it *collected*, is it *shared*, is it *required*, why, and can the user delete
it. This document answers those from what the system actually does — every row cites the
mechanism, so the store submission is honest and matches `docs/CLAIMS_MAP.md`.

> **DRAFT for transcription + counsel review.** Form UIs change; transcribe these answers
> into the current form and have privacy counsel confirm before submitting. The consumer
> ToS/Privacy Policy (`docs/legal/CONSUMER_TERMS_DRAFT.md`) is the companion.

## The one-line posture

CereBroZen collects account data and (only with per-category opt-in consent) self-reported
wellness data. **Nothing is sold, nothing is used for ads, and a personal account shares
data with no one.** Health/wellness content lives only on the coaching engine — never on the
platform an employer's admin can reach ("counts, never content", a schema property). Users
can export and delete everything.

## Data types

| Data type (store category) | Collected? | Shared? | Optional? | Purpose | Where it lives / mechanism |
|---|---|---|---|---|---|
| **Name, email** (Personal info) | Yes | No | Required for an account | Sign-in, addressing you | Platform `models.User` |
| **Password** (Personal info) | Yes (hashed) | No | Required (unless OTP/Google) | Authentication | Platform `security.hash_password` (PBKDF2) |
| **Mood check-ins** (Health & fitness) | Only if consented (`mood_history`) | No | **Optional — off by default** | Insights you asked for | Engine `wellness.py`; consent-gated (403 without) |
| **Journal entries** (Health & fitness) | Only if consented (`journal_memory`) | No | Optional — off by default | Your private reflection | Engine; content-firewalled off the platform |
| **Sleep log** (Health & fitness) | Only if consented (`sleep_history`) | No | Optional — off by default | Sleep trends | Engine |
| **Coaching conversations** (Messages / App activity) | Yes | No | Core of the product | Coaching | Engine; never on the platform |
| **AI-derived memory / patterns** | Only if consented (`ai_memory`) | No | Optional — off by default | Continuity between sessions | Engine `stores/patterns.py`; deletable |
| **Consent choices + change history** | Yes | No | — | DPDP transparency | Platform `models.ConsentEvent` (content-free) |
| **Trusted contact** (Contacts — *user-entered, not device contacts*) | Only if you add one | **Never** shown to employer/anyone | Optional | Shown to *you* in a crisis | Platform; deletable |
| **Subscription reference** (Purchases) | Yes (opaque id only) | With the payment provider | If you buy Plus | Billing | Platform `models.Subscription` — **no card data**; card handled by Stripe / Play |
| **Anonymous product/funnel events** | Yes | No | — | Product analytics | Platform `FunnelEvent` — **no account link by construction** |
| **Aggregate coaching activity** (org accounts only) | Yes (kind-only counts) | With the employer, **k-anon floored** | Enterprise only | Program ROI | Platform `ActivityEvent` — counts, never content |

## NOT collected

Precise or approximate **location**; device **contacts**, **photos**, **files**, **calendar**;
**advertising identifiers**; **financial info** (card data never touches our servers);
**biometrics** stored server-side (on-device biometric unlock stays on the device). Voice is
**on-device by default**; cloud voice is off unless keys are configured and you consent
(`voice_storage`, off by default).

## Data-handling practices (both forms ask these)

- **Encrypted in transit:** yes (HTTPS).
- **At rest:** datastore-layer encryption, attested per deployment (`/health` `storage`).
- **Data sold:** **no.**
- **Data used for advertising / tracking:** **no** — by construction, for every account.
  There is no ad SDK, no third-party analytics, and no cross-app identifier anywhere in the
  build; the only analytics are first-party aggregates computed in our own Postgres.
  (An earlier draft said tracking "is disabled for any account not 18+-attested", which
  implied a conditional mechanism keyed on the `adult` claim. No such conditional exists —
  and it would be a weaker statement than the truth. The 18+ attestation gates *coaching*,
  via `require_adult`; it has nothing to do with tracking, because there is none to gate.)
- **User can request deletion:** **yes** — in-app: Settings → Delete account (platform
  tombstone + `users.delete_me`), plus engine erasure (`privacy.py`), and "forget AI memory"
  (a strict subset that keeps your journal). Personal accounts also retire their solo org
  (`test_personal_org_cleanup.py`).
- **User can export:** **yes** — Settings → Export my data (platform + engine export routes).
- **Children:** the app is **18+**; not directed to children; a child account is blocked/
  deleted. Verifiable-parental-consent is required before any under-18 use.
- **Consent:** all six wellness/AI categories are **opt-in, off by default**, per-category,
  withdrawable, and enforced from a signed token so withdrawal bites on the next request.

## Apple-specific label mapping

- **Data Used to Track You:** none.
- **Data Linked to You:** name, email, health/wellness data (only if consented), purchases
  (opaque), user content (journal/coaching).
- **Data Not Linked to You:** anonymous product/funnel events.

## Reviewer notes (for the store's health-app review)

- Positioned as **general wellness / coaching, not a medical device** — no diagnosis,
  treatment, or disease claims (see `CLAIMS_MAP.md` and the enforced `check-claims.mjs` gate).
- **Crisis handling:** deterministic, in-app referral to helplines on self-harm signals
  (Google Play / Apple both require this for wellness+chat apps) — `engine crisis.py`.
- **AI disclosure:** the coach is disclosed as AI, and as not a therapist/crisis service,
  in-product.
