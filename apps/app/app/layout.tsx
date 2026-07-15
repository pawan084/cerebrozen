import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CereBroZen",
  description: "Your always-on performance coach.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
