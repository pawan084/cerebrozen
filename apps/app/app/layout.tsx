import type { Metadata } from "next";
import { Newsreader } from "next/font/google";
import { headers } from "next/headers";
import "./globals.css";

// Self-hosted at build time (no runtime request to Google — CSP-safe). Exposed
// as --font-serif; globals.css falls back to Georgia if it fails to load.
const serif = Newsreader({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  style: ["normal", "italic"],
  variable: "--font-serif",
  display: "swap",
});

const title = "CereBro — Your space";
const description = "Check in, journal privately, and sleep better — CereBro on the web.";

export const metadata: Metadata = {
  title,
  description,
  robots: { index: false },
  icons: { icon: "/brand/cerebro-mark.png", apple: "/apple-touch-icon.png" },
  openGraph: {
    title,
    description,
    siteName: "CereBro",
    type: "website",
    // Share image lives on the landing origin — one canonical copy of the banner.
    images: [{ url: "https://cerebrozen.in/brand/banner-social.jpg", width: 1200, height: 628, alt: title }],
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
    images: ["https://cerebrozen.in/brand/banner-social.jpg"],
  },
};

// Per-request rendering so the CSP script nonce (middleware.ts) lands on every
// framework inline script — statically prerendered HTML can't carry a fresh
// nonce. All data is client-fetched, so nothing depended on static output.
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  // Pre-paint theme apply (Night/Dawn/System — lib/theme.ts owns the key):
  // runs before hydration so a Dawn user never flashes Night. Inline script
  // needs the per-request CSP nonce the middleware forwards as x-nonce.
  const nonce = headers().get("x-nonce") ?? undefined;
  const themeBoot =
    `try{var t=localStorage.getItem("theme_mode");` +
    `if(t==="night"||t==="dawn")document.documentElement.dataset.theme=t}catch(e){}`;
  return (
    <html lang="en" className={serif.variable}>
      <head>
        <script nonce={nonce} dangerouslySetInnerHTML={{ __html: themeBoot }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
