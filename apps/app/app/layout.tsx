import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CereBro — Your space",
  description: "Check in, journal privately, and sleep better — CereBro on the web.",
  robots: { index: false },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
