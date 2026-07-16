import type { Metadata } from "next";
import "./globals.css";
import { AppShell } from "@/components/shell";

export const metadata: Metadata = {
  title: "CereBroZen",
  description: "Your always-on performance coach.",
  robots: { index: false, follow: false },
};

/* Required by the nonce-CSP in proxy.ts, not a preference. Next stamps the nonce onto its
   script tags during SSR, reading it from the request's CSP header — a prerendered page has
   no request, so its tags would ship nonce-less and the browser would block every one of
   them: a blank app. Costs nothing here; everything is client-fetched behind an auth gate
   and none of it was ever cacheable. */
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
