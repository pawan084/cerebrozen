import type { Metadata, Viewport } from "next";
import { Newsreader } from "next/font/google";
import "./globals.css";

// Self-hosted at build time (no runtime request to Google — CSP-safe). Exposed
// as --font-serif; globals.css falls back to Georgia if it fails to load.
// Upright only: the serif is used for display type (h1–h3, the wordmark, prices)
// and nothing on the site is set in italic, so shipping the italic faces was
// dead weight in the build.
const serif = Newsreader({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-serif",
  display: "swap",
});

const title = "CereBro — your quiet space for daily mental fitness";
const description =
  "A calm, proactive wellness companion: better sleep, calmer focus, layered soundscapes, a private journal, and an AI plan that adapts to how you actually feel.";

export const metadata: Metadata = {
  metadataBase: new URL("https://cerebrozen.in"),
  title,
  description,
  // `keywords` removed 2026-08-15 (audit E14): no major engine has used the meta
  // keywords tag since roughly 2009. It was ten generic terms of pure payload.
  //
  // Home's canonical. Every other route sets its own (child metadata wins), so
  // add one to any new page you create.
  alternates: { canonical: "/" },
  openGraph: {
    title,
    description,
    // Home's og:url. Next merges metadata SHALLOWLY, so a child page that sets
    // only title/description/canonical inherits this whole object — which is how
    // /privacy, /terms and /support each shared as the homepage (audit E13).
    // Every page that overrides metadata now passes `openGraph` too; the helper
    // in `lib/pageMeta.ts` exists so that is one argument rather than a habit
    // someone has to remember.
    url: "https://cerebrozen.in",
    siteName: "CereBro",
    type: "website",
    // 1200x630 is the documented recommendation and now the file's real size —
    // it was declared 628 and *was* 628 (audit E15). The asset was rescaled
    // rather than the number edited: declaring a size the file does not have
    // would have been the worse repair.
    images: [{ url: "/brand/banner-social.jpg", width: 1200, height: 630, alt: title }],
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
    images: ["/brand/banner-social.jpg"],
  },
  // `icon` is declared explicitly, not left to the app/icon.png file
  // convention: setting `icons` at all overrides that convention, so with only
  // `apple` here the favicon was served but never linked. (It used to be a
  // generated app/icon.tsx route, which was dropped — see that file's removal:
  // Next 14's bundled @vercel/og calls fileURLToPath on a Windows path and
  // throws "Invalid URL", breaking `next build` on Windows.)
  icons: { icon: "/icon.png", apple: "/apple-touch-icon.png" },
  robots: { index: true, follow: true },
};

// Light Dawn is the default appearance, Night the opt-in one — so the browser
// chrome follows --surface in each. (The old single value was the retired
// indigo ground and tinted the address bar a colour no longer on the page.)
export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f8f4ee" },
    { media: "(prefers-color-scheme: dark)", color: "#171019" },
  ],
};

// Per-request rendering so the CSP script nonce (middleware.ts) lands on every
// framework inline script — statically prerendered HTML can't carry a fresh
// nonce. Deliberate trade-off: the landing gives up static optimization for a
// no-'unsafe-inline' script policy (small SSR pages behind Caddy gzip).
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={serif.variable}>
      <body>{children}</body>
    </html>
  );
}
