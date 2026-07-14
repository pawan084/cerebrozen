# CereBroZen — cerebrozen.in

Marketing website for **CereBroZen**, an enterprise AI performance-coaching platform. Built with Next.js (App Router), TypeScript, and Tailwind CSS v4.

## Pages

- `/` — full landing page (hero, stats, testimonials, clients, problem/solution, platform, results, audiences, features, case study, security, CTA)
- `/platform` — product deep-dive and rollout steps
- `/solutions` — workforce performance, leadership development, well-being
- `/security` — trust, privacy, and compliance details
- `/clients` — client stories
- `/about` — mission and values
- `/contact` — demo request form
- `/privacy`, `/terms` — legal placeholders

## Develop

```bash
npm install
npm run dev      # http://localhost:3000
npm run build    # production build
```

## Before launch — replace placeholder content

All marketing copy is original, but several items are **illustrative placeholders** that must be replaced with real data before going live:

- Client names, testimonials, case-study figures, and all metrics/statistics are fictional examples.
- Compliance claims (SOC 2 / ISO 27001 / GDPR / DPDP) are phrased as "architected for" — only state certifications you actually hold.
- `/privacy` and `/terms` need counsel-reviewed language.
- The demo form (`src/components/DemoForm.tsx`) currently shows a success state only — wire it to your CRM, email provider, or an API route.
- Social links in the footer point to generic LinkedIn/YouTube homepages.
- Photos in `public/` (`hero.jpg`, `quotes-bg.jpg`, `cta-bg.jpg`, `person-*.jpg`) are stock photos from Unsplash (free for commercial use under the Unsplash License, no attribution required) — swap in your own brand photography when available.

## Structure

- `src/lib/site.ts` — site name, domain, nav links, contact email
- `src/components/` — shared UI (Navbar, Footer, Reveal, Counter, carousel, form, icons)
- `src/components/home/` — landing-page sections
- `src/app/` — routes

## Deploying to cerebrozen.in

1. Deploy anywhere that supports Next.js (Vercel, Netlify, or a Node server via `npm run build && npm start`).
2. Register `cerebrozen.in` with an Indian registrar (e.g. GoDaddy, Hostinger, BigRock) — `.in` domains are managed by NIXI.
3. Point the domain's DNS (A/CNAME records) at your host and add it as a custom domain in the hosting dashboard.
