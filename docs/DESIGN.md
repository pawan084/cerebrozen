# Design System

Last updated: 2026-07-21

## Brand

CereBroZen = *cerebro* (clarity) + *zen* (calm). The visual language is
already established by the marketing site: calm, editorial, high-whitespace,
near-black ink on warm cream, with a single coral accent and the enso
(open-circle) mark. All product surfaces extend this — the app an employee
opens must feel like the site their CHRO bought from.

## Palette

Source of truth: `design/tokens.css` — **built**, seeded from
`apps/web/src/app/globals.css`, synced to each surface by
`scripts/sync-tokens.mjs` with a CI drift check (`--check`) — the `ref/Zen`
pattern.

| Token family | Values | Role |
|---|---|---|
| `ink` (brand-950…500) | `#0a0a0a` → `#6b6b6b` | Text, dark surfaces |
| `coral` (zen-700…50) | `#d94f4f`, `#ef5b5b`, `#f56b6b`, `#f58a8a`, `#fcd9d4`, `#fdf1ee` | Accent |
| `cream` (mist-50…200) | `#f8efe4`, `#f3e7d7`, `#e7d6bf` | Warm backgrounds |
| `paper` | `#ffffff` | Base background |

### The two-tier accent rule (adopted from `ref/Zen`'s `Color.kt`)

Coral `#f56b6b` fails WCAG AA (4.5:1) as text on white/cream. Every accent
hue therefore exists in two tiers:

- **Fill tier** (`zen-500`/`zen-400`) — decorative: buttons with white text,
  icons, charts, rings. Never body/label text on light backgrounds.
- **Text tier** (`zen-700` and darker as needed) — the only coral allowed as
  text on light backgrounds. Dark theme flips this: `zen-400` is the
  text-safe tier on ink.

Enforced, not advised — **on Android**: `ContrastTest` fails the build if any
(text-token, background-token) pair drops below 4.5:1 (`ref/Zen` pattern).

**Web and admin have no automated contrast gate.** This doc previously said
they got "the same check in the token-sync script"; they do not —
`scripts/sync-tokens.mjs` only diffs custom-property *declarations* and
contains no luminance maths at all. So a web-only colour change can ship an AA
violation that nothing catches. Either port the ratio check into the sync
script or treat web contrast as a manual review step, but don't rely on a gate
that isn't there.

### Dark theme

The marketing site is light-only; the app and admin support Night and Day.
Night ground is `brand-950`/`brand-900` (not pure black), text `#f5f2ec`
(warm white), accent tier flip per above. Token file carries both themes;
Compose theme-aware getters mirror it.

## Typography

Marketing site: Poppins (headings), Inter (body), Playfair Display
(wordmark/serif moments) — keep.

Product surfaces (app, admin): **Inter only**, weights 400–700. Rationale:
single-family variable font for legibility and bundle size (the Zen
reference reached the same conclusion with Nunito); Poppins/Playfair remain
brand-voice fonts for marketing, not UI chrome. Android bundles Inter as a
variable font resource.

Scale (app): display 28/34, title 22/28, body 16/24, secondary 14/20,
caption 12/16. Minimum interactive target 48dp.

## Motion

- Marketing site: the `Reveal` scroll pattern (already built), 0.7s ease-out.
- App: fast and quiet — 150–250ms standard easing for navigation and state;
  the SSE coaching stream renders token-by-token with no artificial delay;
  the commit-gate card uses a single deliberate emphasis animation.
- Respect `prefers-reduced-motion` / `Settings.Global.ANIMATOR_DURATION_SCALE`
  everywhere (already done on web; port the check to Compose).

## Voice and content style

Calm, direct, second person. The coach asks before it tells. No exclamation
marks in UI chrome. Numbers honest and sourced — the Evidence-page posture
applies to product copy too: never show a metric the backend can't back.
Crisis and safety strings are clinical-reviewed, never A/B-tested, and only
localized after native-speaker + clinical review (a rule inherited from both
references).

## Per-surface rules

### Android app
- Material3 + Compose, dynamic color OFF (brand palette is fixed).
- 5-tab pill navigation (Today, Coach, Journeys, Actions, You).
- Chat/coach screen: coach text in ink on paper; user text in `brand-600`;
  state chips (stage/commitment) in the mono style the marketing SessionDemo
  established. Action cards use the coral fill tier with a check affordance.
- Every screen must render acceptably offline from the GET cache with the
  `servedStale` banner (transport contract, not per-screen improvisation).

### Admin / HR portal
- Same tokens; denser layout; hand-written CSS (no framework), nonce-CSP.
- Charts follow the dataviz discipline: one accent series (coral fill tier),
  neutral grays for comparison series, no red/green-only encodings; every
  aggregate chart footnotes its cohort floor ("cohorts under N hidden").

### Marketing site
- Already built; DESIGN changes flow tokens-first (edit `design/tokens.css`,
  run sync) rather than editing `globals.css` in place, once the token file
  exists.

## Assets

- Enso mark: coral on ink (favicon already unified). Provide 1x/2x/3x and
  adaptive-icon variants for Android from one SVG source.
- Photography: warm, natural-light, no stock-blue offices. All people photos
  in marketing are illustrative and disclaimed (established rule).
- No licensed audio/video is shipped unless we own or licensed it — the Zen
  reference's explicit copyright warning about teardown assets applies.
