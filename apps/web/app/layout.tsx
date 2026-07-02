import type { Metadata, Viewport } from "next";
import "./globals.css";

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
  themeColor: "#080b22",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
