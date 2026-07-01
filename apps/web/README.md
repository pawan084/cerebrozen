# CereBro — marketing site

Next.js 14 (App Router) landing page + waitlist. On-brand with the iOS app
(night-sky gradient, periwinkle orb). No Tailwind — plain CSS in `app/globals.css`.

## Develop

```bash
npm install
cp .env.example .env.local   # set NEXT_PUBLIC_API_URL to your backend
npm run dev                  # http://localhost:3000
```

The waitlist form posts to `${NEXT_PUBLIC_API_URL}/waitlist`.

## Build

```bash
npm run build && npm start
```

## Deploy (Vercel)

1. New Project → import the repo.
2. **Root Directory: `apps/web`** (this is a monorepo).
3. Environment variables:
   - `NEXT_PUBLIC_API_URL` — your deployed backend URL.
   - `NEXT_PUBLIC_APP_STORE_URL` — the App Store listing URL (leave blank
     pre-launch; the download badge falls back to the waitlist).
4. Deploy. `vercel.json` applies sensible security headers.

Framework autodetects as Next.js. The OG image (`app/opengraph-image.tsx`),
favicon (`app/icon.tsx`), `robots.ts`, and `sitemap.ts` are generated at build —
no static assets to manage. Update the domain in `layout.tsx`, `robots.ts`, and
`sitemap.ts` if it isn't `cerebro.app`.
