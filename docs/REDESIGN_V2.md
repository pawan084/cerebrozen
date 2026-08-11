# CereBro Light Dawn Redesign Spec (v2)

**Source:** `ref/` — `CereBro Master Product Specification.pdf` (48 pages, 15 sections,
69 numbered modules) plus four single-file prototypes: `mobile.html` (126 screens,
15 flows), `web.html` (37 routes), `portal.html` (36 routes), `landing.html` (7 pages).
**Started:** 2026-08-06 · **Supersedes** the visual-language half of [REDESIGN.md](REDESIGN.md)
(§4.1 "Dusk & Dawn", §4.2 tokens). Its IA and credibility work (§2, §3) still stands.

---

## 0. The one-paragraph verdict

The new `ref/` inverts the design system: the spec names a **"Light Dawn visual system"**
with **"optional dark appearance later"**, where this repo was built night-first on deep
indigo. It also replaces the accent family — lavender/periwinkle becomes **plum**, with
sage, rose and amber as the tonal set. That much is a straight re-point. The harder parts
are that the spec is **Android-first and names neither iOS nor an authenticated web client**,
and that it introduces an entire **B2B2C organisation layer** (sponsored access, seats,
cohorts, entitlements, a 36-route admin portal) for which this backend has **no model, no
role and no route**. Phase 1 below is the token inversion, which is done and verified;
everything after it is real product work, not restyling.

---

## 1. What the sources actually contain

Worth stating plainly, because it changes where the values come from:

- **The PDF contains no design values.** No hex, no type scale, no spacing, no radii, no
  motion, no contrast ratios, no touch-target size. `"Light Dawn visual system"` and
  `"Optional dark appearance later"` are the complete visual content of all 48 pages.
  Every token below therefore comes from the prototypes, not the spec.
- **The PDF leaves almost every threshold unquantified** — cohort minimums, pricing, free
  message caps, retention windows are all named as concepts with no number. The one
  concrete number is in the prototypes: **minimum cohort = 20 active members** (options
  20/30/50, configurable per organisation).
- **The four prototypes do not share one palette.** `mobile.html`, `landing.html` and
  `portal.html` agree on the core; `web.html` is the outlier on every one of them.

| Role | mobile | landing | portal | web.html |
|---|---|---|---|---|
| ground | `#F8F4EE` | `#F8F4EE` | `#F8F4EE` | **`#F4F1EC`** |
| paper | `#FFFCF8` | `#FFFCF8` | `#FFFCF8` | **`#FFFEFC`** |
| ink | `#211D20` | `#211D20` | `#211D20` | **`#231F24`** |
| plum | `#5A2B5C` | `#5A2B5C` | `#5A2B5C` | **`#542454`** |
| line | `#E4DDD7` | `#E4DDD7` | `#E4DDD7` | **`#E6DED7`** |

  **Resolution: the three-way agreement wins; `web.html`'s palette is not adopted.**
  `web.html` also inverts two role names (`--sage`/`--rose` are *ink* there and *surface
  tints* in landing) — do not port its naming.

- **Display font diverges three ways** — Iowan Old Style (landing, web), Georgia (portal),
  Fraunces (mobile's final layer). This repo already ships **Newsreader** via `next/font`
  on all three web apps; it stays. No prototype loads a webfont at all, so none of them is
  evidence for a change.

---

## 2. Canonical Light Dawn tokens

Live in [`design/tokens.css`](../design/tokens.css); propagated by `scripts/sync-tokens.mjs`;
gated by the new `scripts/check-contrast.mjs`.

Four tonal roles were **darkened from the prototype values** because they failed this
repo's existing 4.5:1 gate. The changes are 4–9% and read as the same hue:

| Role | Prototype | Worst ratio | Adopted | Worst ratio |
|---|---|---|---|---|
| `--text-faint` | `#716A70` | 4.00 | `#686267` | 4.53 |
| `--warm` | `#B4596B` | 3.91 | `#A45161` | 4.56 |
| `--danger` | `#CC3D36` | 4.21 | `#C23A33` | 4.57 |
| `--amber` | `#AE7423` | 3.40 | `#92611D` | 4.58 |
| `--line` | `#E4DDD7` | 1.16 vs field | `#DFD9D3` | 1.21 |

Six roles are adopted **exactly as specified**: `--text #211D20`, `--text-secondary
#514A50`, `--accent #5A2B5C`, `--accent-2 #8A4A78`, `--ok #49634F`, `--info #315C7A`.

The gate is **per legal pairing**, not every-role-on-every-surface: a tonal role must clear
4.5:1 on the three neutral grounds and on *its own* wash. Gating amber against a danger
wash is a pairing that never occurs and would force the brand colours needlessly dark.

**Night is retained**, reconciled from `landing.html`'s `[data-theme="dark"]` and
`web.html`'s `[data-theme="night"]` (they agree within a few points). It is no longer the
default — it is the opt-in appearance *and* the pinned theme for surfaces that are night by
design: the Sleep tab and wind-down ritual, the onboarding funnel and auth card, the public
`/crisis` page. `mobile.html` ships no dark theme at all (`[data-theme="night"]` is
deliberately identical to Dawn); we keep ours because the sleep argument is real and
`e2e/tests/theme.spec.ts` already pins it.

**Naming.** New code reads semantic roles (`--surface`, `--surface-raised`, `--surface-field`,
`--line`, `--text`, `--text-secondary`, `--text-faint`, `--accent`, `--accent-2`,
`--accent-soft`, `--on-accent`, `--ok`, `--warm`, `--danger`, `--amber`, `--info`, each
tonal role with a `-soft` wash). A legacy alias block re-points the ~900 existing
`var(--night)` / `var(--lav)` / `var(--muted)` call sites so nothing broke on day one.
**Do not add new usages of the aliases**; migrate call sites opportunistically.

---

## 3. Information architecture

### 3.1 Tabs — the spec's five, and what that costs us

Spec order: **Today · Explore · Talk · Journal · You**, with urgent support globally
accessible outside the tab set.

| Client | Today | 2nd | 3rd | 4th | 5th |
|---|---|---|---|---|---|
| Spec / `mobile.html` | Today | **Explore** | Talk | Journal | You |
| Android + iOS today | Home | **Sleep** | Talk | Journal | You |
| `apps/app` sidebar today | Home | Talk | Sleep | Journal | **Insights** |

Two deltas to close: **Sleep is not a top-level tab in the spec** (it lives under Explore,
plus a Today entry point and quick tools), and the web client's fifth slot is Insights
where native has You. `mobile.html` maps sub-screens to owning tabs by id prefix —
`Today ← TOD|PRC|INS`, `Explore ← EXP|SLP|SND|MIX|GMS|PRG|VID`, `You ← YOU|PVR|SAF|PRM|ACC|ORG`.

Demoting Sleep is a genuine product decision, not a mechanical one — Sleep is this
product's evidenced flagship (REDESIGN.md §3.2, sleep g=0.71) and burying it under Explore
argues against the strongest thing we have. **Flagged for owner decision; not actioned.**

### 3.2 Route reconciliation

`mobile.html` defines **126 screens in 15 flows**; Android's `NavHost` has **57**
destinations. Note REDESIGN.md §2.2 targeted 40→28 and the count instead grew to 57, with
routes explicitly marked KILL still registered (`onegoodthing`, `intention`, `breathing`,
the `games`/`tools` aliases, `baseline`, `bubblepop`, `patternglow`). Reconcile against the
prototype's flow map before adding anything new.

### 3.3 The organisation layer is new, end to end

`portal.html` is 36 routes across Identity, Dashboard, Eligibility, Cohorts, Programmes,
Campaigns, Referrals, Analytics, Governance, Commercial and a member-experience preview.
Against that, this repo has:

| Concept | Status today |
|---|---|
| Organisation / tenant | **absent** — no model, field or FK anywhere |
| Sponsorship | **absent** — billing hangs directly off `User` |
| Entitlement / seat | **absent** — only `User.subscription_tier` as a string |
| Cohort | **absent** as a model (a computed grouping in `services/metrics.py` only) |
| RBAC | **binary** — `User.is_admin`; the portal needs 7 roles |
| Programme | present (`ProgramEnrollment` → `ContentItem`) |

`apps/admin` is **not** this portal. It is a single ~2000-line client component with a
`useState` tab switcher, gated by one boolean, scoped to the whole platform (all users, all
content, prompts, safety events). It is an internal staff console and should stay one; the
organisation portal is a **new surface**.

---

## 4. Non-negotiables the sources reinforce

These already match this repo's rules; the spec states them as product values, so they are
now doubly binding.

- **Anti-gamification is explicit in five places** — M4 "No streak pressure", M22 "No
  streak-loss messaging", M19 "No leaderboards / No competitive scoring / No intelligence
  claims", persona C6 "No performance scoring", O5 "non-coercive copy". The only sanctioned
  progress affordances are neutral: small progress summary, seven-day history, programme
  progress, completion indicator.
- **Crisis is never paywalled** — M26 "No Premium requirement"; M30 "Safety features remain
  available after expiry". Tele-MANAS **14416** leads, then **112**.
- **Safety never blocks and never guesses** — when the safety classifier is unavailable the
  app routes to humans rather than guessing (`TLK-07`).
- **No organisation notification, ever** — M26/M29/O10. §12 lists nine categories of
  prohibited organisation access; the portal's own sidebar carries a permanent privacy wall.
- **Region honesty** — `mobile.html` marks **only India** `verified:true` and renders an
  explicit unverified warning elsewhere. This repo's own audit flagged the opposite bug
  (Indian numbers shown as "Verified" for all countries). Keep the honest version.

---

## 5. Phasing

| Phase | Scope | Status |
|---|---|---|
| **1. Token inversion** | `design/tokens.css` → Light Dawn light-first + Night opt-in; new `scripts/check-contrast.mjs` gate; sync into web/admin/app; primary CTA moves from white pill to accent fill; nav/topbar de-hardcoded from indigo | **done, verified** |
| **2. Native token port** | Android `ui/theme/*.kt` and iOS `DesignSystem/Theme.swift` to the same role scale, both contrast suites green | **Android done** (Dawn default, Night re-toned to plum, every canonical role byte-pinned against `tokens.css`, `ContrastTest` 19→22 green — no value needed adjusting for contrast); **iOS not started** |
| **3. Surface sweep** | The ~53 `rgba(255,255,255,…)` night-era veils across the three web apps; regenerate the baked marketing screenshots (still show the indigo app, and one shows a "3-day streak" — a banned affordance) | **not started** |
| **4. IA** | Tab reconciliation, route reconciliation against the 126-screen flow map | Unblocked by §6. **Five-tab IA done on `apps/app` and Android** (Today · Explore · Talk · Journal · You; Sleep pushed under Explore; a support door on Explore *and* You so crisis stayed ≤2 taps). **iOS pending; route reconciliation not started** |
| **5. B2B2C** | Organisation/membership/entitlement/cohort models, 7-role RBAC, aggregation with threshold + small-cell suppression, then the 36-route portal as a new app | **not started** |

---

## 6. Owner decisions — RESOLVED 2026-08-06

**Ruling: follow `ref/` strictly, across all five surfaces.** Where the spec and this repo
disagree, the spec wins. Recorded so later readers know these were decided, not drifted into:

1. **Sleep is demoted from the top-level tabs.** Tabs become the spec's five —
   **Today · Explore · Talk · Journal · You** — with Sleep reached via Explore's Sleep
   category, Today's "Tonight's sleep plan" entry, and the sleep quick tools. This overrides
   the recommendation in the previous revision of this section, which argued Sleep is the
   evidenced flagship (sleep g=0.71, REDESIGN.md §3.2) and should keep its tab. That
   argument is not withdrawn on the merits — it lost to the ruling. If engagement with
   sleep content drops after this ships, this is the first thing to re-examine.
2. **The spec's IA is authoritative** wherever it is explicit, including the id-prefix →
   owning-tab map in `mobile.html` (`Today ← TOD|PRC|INS`, `Explore ← EXP|SLP|SND|MIX|GMS|
   PRG|VID`, `You ← YOU|PVR|SAF|PRM|ACC|ORG`).
3. **iOS and `apps/app` are still redesigned**, both to the spec's IA. "Strictly" resolves
   conflicts in the spec's favour; it does not delete surfaces the spec merely omits.
   Android leads (the spec's primary platform), the others follow to the same structure.
4. **Spelling is en-GB** — organisation, programme, personalise. `landing.html`'s
   "organization" is the outlier and is not adopted.
5. **Cohort floor is 20 active members**, options 20/30/50, configurable per organisation.

## 7. Execution order

Sequenced by dependency so nothing is built twice. Each wave is verifiable on its own.

| Wave | Scope | Why here |
|---|---|---|
| **1. Tabs** | The five-tab IA + Sleep demotion, on Android, iOS and `apps/app` in one change | Load-bearing: every screen nests inside it, and it is a cross-stack contract (ARCHITECTURE.md) |
| **2. Landing** | 7 pages to `landing.html`'s structure; regenerate the stale indigo screenshots | Self-contained, public, no auth or backend — the fastest complete surface |
| **3. Member web** | 37 routes to `web.html`'s shell and route groups | Fastest to iterate and visually verify locally |
| **4. Android** | 126 screens / 15 flows | The richest prototype and the spec's primary platform |
| **5. iOS** | Parity to the same IA and screens | Follows Android per the existing parity practice |
| **6. B2B2C** | Organisation/membership/entitlement/cohort models, 7-role RBAC, threshold + small-cell aggregation, then the 36-route portal | Greenfield; mostly backend before any screen exists to design |

### 7.1 Design-first working mode (owner direction, 2026-08-06)

Screens are designed **before** they are wired. Redesigned screens live on a no-backend,
no-auth **design surface** (`apps/app/app/design/**`, `noindex`) rendered entirely from local
mock constants — structurally the same thing the `ref/` prototypes are. Two reasons: the live
screens keep their working API wiring until a design is signed off, and a screen behind the
session guard cannot be reviewed without a running Postgres. Each screen graduates into its
real route once approved; the surface is scaffolding and should shrink to nothing.

### 7.2 Parallel wave in flight (2026-08-06)

Four independent surfaces, deliberately on non-overlapping paths so they cannot collide:

| Surface | Path touched | Depends on |
|---|---|---|
| Android foundation — tokens + 5-tab IA + Explore | `apps/android/**` | nothing; **blocks all Android screen work** |
| Landing rebuild to `landing.html` | `apps/web/**` | web tokens (done) |
| Organisation portal scaffold | `apps/portal/**` (new app, port 3003) | nothing; mock data only |
| Member-web design screens (TOD-02, EXP-01, SLP-01, SAF-01) | `apps/app/app/design/**` | the existing `/design/today` pattern |

`apps/portal` must be added to `scripts/sync-tokens.mjs` TARGETS once it exists, or its token
block will silently drift from `design/tokens.css`.

### 7.3 Concurrency lesson (2026-08-06)

Two agents were run against `apps/android` **in the same working tree at the same time**
(the raw-hex sweep and the Today redesign). They collided: `TodayScreen.kt` changed under
the sweep mid-run and was briefly brace-unbalanced, and the sweep ran `git stash` /
`git stash pop` on the shared tree to diagnose it — which momentarily reverted the other
agent's in-flight work. The tree was verified intact afterwards (empty stash list, all files
present, all gates green, 144→0 literals), but it was luck rather than design.

**Rule going forward:** never run two agents against the same client in one tree. Either
serialise them, or give each `isolation: "worktree"` so they get their own checkout. Agents
must also not run `git stash` on a shared tree — it is not a local operation.
