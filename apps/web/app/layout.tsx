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
  keywords: [
    "mental wellness", "calm", "sleep", "meditation", "journal", "anxiety",
    "stress", "mindfulness", "AI companion", "breathing",
  ],
  // Home's canonical. Every other route sets its own (child metadata wins), so
  // add one to any new page you create.
  alternates: { canonical: "/" },
  openGraph: {
    title,
    description,
    url: "https://cerebrozen.in",
    siteName: "CereBro",
    type: "website",
    images: [{ url: "/brand/banner-social.jpg", width: 1200, height: 628, alt: title }],
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
    images: ["/brand/banner-social.jpg"],
  },
  icons: { apple: "/apple-touch-icon.png" },
  robots: { index: true, follow: true },
};

export const viewport: Viewport = {
  themeColor: "#0e0c22",
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
