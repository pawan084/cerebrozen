import type { Metadata } from "next";
import { Newsreader } from "next/font/google";
import "./globals.css";

// Same load as apps/app and apps/web: self-hosted at build time (no runtime
// request to Google — CSP-safe), exposed as --font-serif with a Georgia
// fallback in globals.css. All three surfaces now share the brand serif.
const serif = Newsreader({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  style: ["normal", "italic"],
  variable: "--font-serif",
  display: "swap",
});

export const metadata: Metadata = {
  title: "CereBro Admin",
  description: "Operations dashboard for CereBro.",
  // An ops dashboard has no business in a search index. The Caddyfile sets
  // X-Robots-Tag for admin.cerebrozen.in too; this covers any other host.
  robots: { index: false, follow: false },
};

// Per-request rendering so the CSP script nonce (middleware.ts) lands on every
// framework inline script — statically prerendered HTML can't carry a fresh
// nonce. All data is client-fetched, so nothing depended on static output.
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={serif.variable}>
      <body>{children}</body>
    </html>
  );
}
