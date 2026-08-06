import type { Metadata } from "next";
import { Inter, Newsreader } from "next/font/google";
import "./globals.css";
import Shell from "@/components/Shell";

// Both faces are self-hosted at build time by next/font — no runtime request
// to Google, so the portal is CSP-safe in the same way apps/web, apps/admin
// and apps/app are. Newsreader is the brand serif (display headings only);
// Inter carries body text, with the system stack behind it.
const serif = Newsreader({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  style: ["normal", "italic"],
  variable: "--font-serif",
  display: "swap",
});

const sans = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: "CereBro for Organisations",
  description:
    "Organisation administration for sponsored CereBro access — eligibility, programmes and anonymous aggregate reporting.",
  // An administration portal has no business in a search index.
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-GB" className={`${serif.variable} ${sans.variable}`}>
      <body>
        <Shell>{children}</Shell>
      </body>
    </html>
  );
}
