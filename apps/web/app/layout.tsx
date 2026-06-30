import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CereBro — your quiet space for daily mental fitness",
  description:
    "A calm, proactive wellness companion: better sleep, calmer focus, and an AI plan that adapts to how you actually feel.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
