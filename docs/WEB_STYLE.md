# Web copy & design rules

The rules the 2026-08-03 world-class reviews kept applying, written down so
the next surface doesn't relearn them. These are review-blocking, same as the
motion contract in `apps/web/app/globals.css`.

## Copy

- **"Arrives with", never "lives in".** An unreleased app is a future, not a
  place. "This one lives in the iOS app" pointed users at a store listing
  that doesn't exist — three clients said it five ways before 2026-08-03.
- **Truth before invitation.** An empty state names the *actual* reason it is
  empty before inviting action. "Check in and this fills in" is a lie when a
  consent switch is what's off (Trends). "You're in!" is a lie when the
  request 429'd (waitlist).
- **Success only on 2xx.** Never parse an error body into a success message.
- **A choice that changes nothing is a fake.** Don't render pickers whose
  value is discarded (the web reminder-time chips). On/off is honest; a time
  picker returns only when a server hour exists for it to set.
- **Numbers only from mechanisms.** No user counts, ratings, or outcomes
  until they exist. Schema.org markup states price and platform — never
  aggregateRating.
- **Crisis resources come from the platform, never the model.** LLM prompts
  forbid naming hotlines; `crisis.reply_suffix`/the SSE banner carry the
  region-correct lines (Tele-MANAS first in India).
- **Every trust claim has a mechanism** and, where doubtable, a row in
  `docs/CLAIMS_MAP.md`. This includes /security page claims.

## Design

- **Blur only where content moves beneath.** `backdrop-filter` on the sticky
  nav earns its raster cost; on static cards over a smooth gradient it is an
  invisible effect with a visible jank bill.
- **Motion contract** (globals.css): every animation guarded for
  reduced-motion, static fallback is the finished state.
- **One canonical host** (`cerebrozen.in`); www redirects. Admin is
  noindex at both the metadata and the proxy layer.
- **Legal prose uses `.legal`** — prose column, list rhythm, scrollable
  tables. Don't hand-style policy pages.
- **A11y floor:** skip-link on every shell, focus moves to the new page's h1
  on client-side nav, chat streams are `aria-live=polite`, translated notice
  content carries its `lang`.

## Auth

- Account creation always passes an 18+ moment — the funnel's gate at step 1,
  or the attest checkbox in standalone AuthPanel (`requireAgeAttest`).
- Fresh accounts route through the funnel's consent step before landing on
  Home. A consent screen nobody saw is not consent, even with all-off
  server defaults protecting them.
- Access tokens live in memory only, on every surface, admin included.
