# CereBro Landing (apps/web) — Design/UX/Frontend Audit

> Principal-level audit of cerebrozen.in, 2026-07-03. Method: full source read
> (page.tsx, globals.css, layout.tsx, Waitlist.tsx) + real rendered captures via
> Playwright at 1440/834/390px, including per-viewport scroll captures.
> Priorities: C=Critical · H=High · M=Medium · L=Low.

## 1. Executive summary

The landing is **well above average for a pre-launch page**: coherent night-brand,
distinctive serif voice, semantic HTML, no webfonts/trackers (fast by construction,
139 KB of imagery total), honeypot-protected waitlist, reduced-motion guards, and
scroll-driven reveals done as progressive enhancement. It is genuinely close to good.
What separates it from Stripe/Linear-tier is: one structural rendering hazard, three
honesty/staleness issues that would embarrass a launch, missing mobile navigation,
missing keyboard focus treatment, and the absence of any social-proof/credibility layer.

**Top 10 highest-impact improvements**
1. (C) Replace `background-attachment: fixed` with a fixed-position gradient layer + solid dark fallback — the entire dark theme currently rides on one fragile technique; where it fails (iOS Safari quirks, print, some webviews/crawlers) the page renders white-on-white.
2. (C) Make pricing honest: "Offline downloads", "Unlimited voice companion", "Coach & therapist booking" are not built. Same fix as the app paywall (PRD checklist #1).
3. (C) Refresh the three phone mockups — all predate the 2026-07-03 app redesign; the sleep mockup shows retired desert-road art the product no longer contains.
4. (C) Mobile nav: below 640px all section links are removed with no menu — add a minimal disclosure menu.
5. (C) Keyboard focus: waitlist input has `outline: none` with no replacement; no `:focus-visible` styles anywhere (WCAG 2.4.7 fail).
6. (H) Replace emoji feature icons (🎙️🌙📖🫧🔥🆘) with brand SVG line icons in lavender wells — emojis render platform-inconsistently and the red 🆘 pierces the calm palette; iOS app uses symbol wells, so this is also a brand mismatch.
7. (H) Add a credibility layer: waitlist count ("Join 400+ people…"), a founder note, or press/beta quotes — the page currently asks for trust without offering proof.
8. (H) FAQPage JSON-LD + `text-wrap: balance` on headings ("Start free. Upgrade when it helps." widow; "Calm in two / minutes" wrap).
9. (M) Fix the oversized empty hero cell in the bento ("A companion that listens" spans 2×2 with content for 1×2 — dead space above/below).
10. (M) Pricing is ₹-only on a global page — caption it ("Launch pricing · India") or geo-adapt.

## 2. Critical issues (blockers)

| # | Issue | Location | Root cause | Fix |
|---|---|---|---|---|
| C1 | Whole page can render white-on-white | `globals.css` body background | `background-attachment: fixed` paints one viewport only; unsupported/mishandled contexts (iOS Safari history, print, headless captures, some in-app browsers) fall back to white with `--text:#f4f6ff` | Move gradient to `body::before { position: fixed; inset: 0; z-index: -1 }`, add `background-color: var(--night)` to `html, body` |
| C2 | ~~Unbuilt features sold on pricing~~ **FIXED 2026-07-03** (PLANS + FAQ scrubbed, app paywall too) | `page.tsx` PLANS | Copy drift from aspiration | done |
| C3 | Stale product mockups | `public/screens/*.webp` | Captured before the app redesign | Regenerate from the current UITest screenshot pack (same source that produced them) |
| C4 | No mobile navigation | `globals.css` @640px hides `.nav-links a:not(.btn)` | Overflow fix chose deletion over a menu | Small disclosure menu (native `<details>` styled, no JS needed) |
| C5 | No visible keyboard focus | `.wl-form input { outline: none }`, no `:focus-visible` rules | Reset without replacement | Global `:focus-visible { outline: 2px solid var(--lav); outline-offset: 2px }` + input focus ring |

## 3. Layout review

- (M) Bento `b-lg` cell: 2×2 span + `justify-content: center` leaves large dead zones; either add a mini phone/waveform visual, or demote to 2×1. *Why it hurts:* the premier feature reads emptiest.
- (M) `.band h2` inline `fontSize: 34` breaks the type scale and stays 34px on mobile (too large relative to section h2 clamp). Use the shared clamp.
- (M) Heading wraps: `text-wrap: balance` on h1/h2/h3; fixes "helps." widow and "Calm in two / minutes".
- (L) `html/body { overflow-x: hidden }` is a mask, not a fix — with the mobile nav fix it can likely be removed; keep only if a real offender remains.
- (L) Tablet 834px: bento stays 4-col (breakpoint 820) with ~180px cells — tight but acceptable; consider 2-col between 820–1024.
- OK: container width (1080), spacing rhythm (72px sections), hero grid collapse at 860, waitlist stack at 520 — all verified working in captures.

## 4. Design system review

- Tokens are shared with iOS (`--night/--lav/--cream/--ink`…) — genuinely good; keep as the single source.
- Type: Georgia serif headings + system body is distinctive and fast; scale is mostly clamp-based — codify: h1 clamp(38–62), h2 clamp(28–40), h3 19–20, body 15–17, small 12.5–14; kill inline sizes (page.tsx lines 175, 223, 266).
- Contrast (computed): `--muted #aeb6e0` on `--night` ≈ 9.6:1 ✓; on `--card` ✓; `--lav` on night ≈ 4.9:1 ✓ for large/bold only — fine as used (eyebrows are 700).
- Buttons: primary (cream) / ghost — add a true secondary (lav outline) for mid-emphasis CTAs; add `:active` scale and `:focus-visible` (C5).
- Icons: replace emoji with a 6-icon inline SVG set (mic, moon, book, wind, flame→leaf, life-ring) in the existing lav wells (H, see Exec #6).
- Radius: 13/14/16/20/22/28/46 — near the iOS Radius scale; acceptable spread, document it.
- Shadows: only phone + featured price card — tasteful; keep.

## 5. UX review

- Flow (hero → features → proof → spaces → agentic → safety → pricing → FAQ → waitlist) is right; one gap: **nothing between claim and belief** — add social proof band before pricing (H).
- CTA system: "Join the waitlist" (hero) vs "Get early access" (nav) — same action, two labels; unify (M).
- Waitlist form: has busy state, success copy, error surfacing, honeypot ✓. Add: success **replaces** form (prevents double-join confusion), and inline invalid-email hint (M). Server 10/min rate limit exists; client is fine.
- FAQ `<details>` works; add `name="faq"` for exclusive-open behavior (L). Add 2 more Qs users actually ask: "When does it launch?", "Which languages?" (M).
- Empty/error/loading states: N/A for static page except waitlist (covered) and image `alt`s (present ✓).
- Anchor scrolling: `scroll-behavior: smooth` is unconditioned — wrap in `prefers-reduced-motion: no-preference` (M, a11y-adjacent).

## 6. Missing components

| Component | Why it matters | Recommendation |
|---|---|---|
| Mobile nav menu | C4 — discoverability on the majority device | Styled `<details>` dropdown; zero JS |
| Social proof band | Conversion: trust before pricing | Waitlist counter (backend already stores entries) + founder note; testimonials post-beta |
| FAQPage JSON-LD | Rich results; free SEO | Script tag from the existing FAQ array |
| Skip-to-content link | Keyboard/screen-reader efficiency | Standard visually-hidden link before nav |
| Scrollspy nav highlight | Orientation on long page | IntersectionObserver, few lines |
| Custom 404 | Polish; currently Next default | Calm brand 404 with links home |
| OG image check | Social sharing first impression | `opengraph-image.tsx` exists — verify it renders the current brand, add product shot |
| Language/region hint on pricing | ₹ confuses non-Indian visitors | "Launch pricing · India" caption (or geo) |
| Announcement bar slot | Launch-day "Now on TestFlight" | Simple dismissible bar, hidden by default |
| Command palette / global search / breadcrumbs / pagination | Not applicable | Skip — single-page marketing site; adding them would be cargo-culting |

## 7. Media & video issues

No video exists (none needed pre-launch). Images: 3 WebP, 139 KB total, correct
aspect (640×1391 = 2× display width) — technically excellent. Issues:
- (C3) All three are **stale** vs the shipped app (old rows with photo thumbnails; retired desert-road player art). Regenerate from the current UITest pack.
- (M) Hero image: add `fetchpriority="high"` (it is the LCP element); keep `loading="lazy"` on showcase ✓.
- (L) Consider a 6–10s silent hero loop (screen recording of check-in → plan) post-launch; until then static is fine and faster.

## 8. Accessibility review (WCAG 2.1 AA)

- (C5) Focus visibility — fails 2.4.7 today; fix globally.
- (M) `✦` glyphs in trust chips/pills are read aloud ("four-pointed star") — wrap in `aria-hidden` span.
- (M) Smooth-scroll unconditioned (2.3.3 adjacent) — media-query guard.
- (L) Emoji icons announce as emoji names ("microphone", "SOS button") — SVG replacement (H6) resolves; meanwhile `aria-hidden` the `.icon` divs.
- Passing today: `lang="en"` ✓, single h1 ✓ heading order ✓, nav/header/section/footer landmarks ✓ (add `<main>` wrapper — L), `details/summary` semantics ✓, form label via `aria-label` ✓, contrast ✓, reduced-motion on orb/float/reveal ✓, honeypot `aria-hidden` + `tabIndex=-1` ✓.

## 9. Mobile review (390px verified)

- Hero, trust chips, CTAs, stacked form, bento single-column, pricing stack — all render correctly (captured).
- (C4) Nav menu missing (above).
- (M) Tap targets: nav CTA and FAQ summaries ≥44px ✓; trust chips are decorative ✓; footer links are 14px text — add padding to reach 44px hit area.
- (M) `background-attachment: fixed` is also the top mobile-rendering risk (C1) — iOS Safari is the audience's browser.
- (L) Phone mockup 300px on 390px screen dominates a full viewport — consider 260px on mobile so the next section peeks (scroll affordance).

## 10. Performance review

Already strong: no webfonts, no analytics, 139 KB images, static Next page. Remaining:
- (M) C1 fix also removes fixed-background repaint cost on scroll (mobile GPU win).
- (M) Many `backdrop-filter: blur` layers (cards + bento + spaces + faq) — on low-end Android this is the main scroll cost; acceptable, but prefer solid `--card` fill below 640px.
- (L) `fetchpriority="high"` on hero image (LCP ~image now; est. LCP < 1.5s on 4G after).
- (L) Preload nothing else; page is otherwise optimal. Perceived-perf additions (skeletons etc.) are N/A for static content.

## 11. World-class benchmark gaps (Stripe/Linear/Notion/Vercel/Framer)

1. **Proof density** — those pages earn belief with logos/metrics/testimonials; CereBro has zero proof objects. Pre-launch equivalents: waitlist count, founder letter, "built in public" link, App Store "coming soon" with date.
2. **Product motion** — Linear/Framer show the product moving. One subtle hero loop or hover-tilt on mockups (`prefers-reduced-motion`-guarded) would close most of the gap cheaply.
3. **Micro-interaction polish** — nav scrollspy, button press states, form success morph. Small but it's exactly where "premium" lives.
4. **Icon custody** — no top-tier product ships emoji icons; bespoke SVG is table stakes.
5. **Content depth** — a single page is fine, but /support, /privacy, /terms should share the nav/footer shell so the site feels like one product (they currently do share footer? verify + unify).
What is already at benchmark level: restraint, palette coherence, copy voice ("not another feed to keep up with", "Your calm isn't the product"), performance, and privacy posture.

## 12. Prioritized action plan

- **Phase 1 — Critical (½ day):** C1 background layer · C2 honest pricing (+ mirror app paywall) · C3 regenerate mockups from current app · C4 mobile menu · C5 focus-visible.
- **Phase 2 — UX (1 day):** SVG icon set · CTA label unification · waitlist success-morph + counter · FAQ JSON-LD + 2 new Qs · `text-wrap: balance` · smooth-scroll guard · aria-hidden decorations · skip link · `<main>`.
- **Phase 3 — Premium (1–2 days):** social-proof band (founder note + counter) · scrollspy · bento b-lg visual · pricing region caption · custom 404 · shared shell on subpages · mobile blur→solid fallback.
- **Phase 4 — World-class polish (post-TestFlight):** hero product motion loop · hover-tilt mockups · announcement bar for launch · testimonials from beta cohort · localized pricing.
