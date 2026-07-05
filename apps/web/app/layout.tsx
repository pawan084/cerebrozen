import type { Metadata, Viewport } from "next";
import { Newsreader } from "next/font/google";
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
  openGraph: {
    title,
    description,
    url: "https://cerebrozen.in",
    siteName: "CereBro",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
  },
  robots: { index: true, follow: true },
};

export const viewport: Viewport = {
  themeColor: "#0e0c22",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={serif.variable}>
      <body>{children}</body>
    </html>
  );
}
