import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CereBroZen Admin",
  robots: { index: false, follow: false },
};

/* Required by the nonce-CSP in proxy.ts, not a preference. Next stamps the nonce onto
   its script tags during SSR, reading it from the request's CSP header — a prerendered
   page has no request, so its tags would ship nonce-less and the browser would block
   every one of them: a blank console. Costs nothing that matters here; an
   authenticated ops tool must not be CDN-cached anyway. */
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
