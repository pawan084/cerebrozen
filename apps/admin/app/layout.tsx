import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CereBro Admin",
  description: "Operations dashboard for CereBro.",
};

// Per-request rendering so the CSP script nonce (middleware.ts) lands on every
// framework inline script — statically prerendered HTML can't carry a fresh
// nonce. All data is client-fetched, so nothing depended on static output.
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
