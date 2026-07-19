import type { Metadata, Viewport } from "next";
import { Inter, Poppins, Playfair_Display } from "next/font/google";
import "./globals.css";
import { AppShell } from "@/components/shell";

// Same type family as the marketing site so the product feels of a piece with it:
// Inter body, Poppins headings, Playfair for the reflective serif moments.
const inter = Inter({ variable: "--font-inter", subsets: ["latin"] });
const poppins = Poppins({
  variable: "--font-poppins",
  weight: ["300", "400", "500", "600", "700"],
  subsets: ["latin"],
});
const playfair = Playfair_Display({
  variable: "--font-playfair",
  weight: ["500", "600", "700"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "CereBroZen",
  description: "Your always-on performance coach.",
  robots: { index: false, follow: false },
};

export const viewport: Viewport = {
  // On mobile, resize the layout when the on-screen keyboard appears so the chat
  // composer stays visible instead of being buried (pairs with the app's 100dvh).
  interactiveWidget: "resizes-content",
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#0a0a0a" },
    { media: "(prefers-color-scheme: light)", color: "#faf6ef" },
  ],
};

/* Required by the nonce-CSP in proxy.ts, not a preference. Next stamps the nonce onto its
   script tags during SSR, reading it from the request's CSP header — a prerendered page has
   no request, so its tags would ship nonce-less and the browser would block every one of
   them: a blank app. Costs nothing here; everything is client-fetched behind an auth gate
   and none of it was ever cacheable. */
export const dynamic = "force-dynamic";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      className={`${inter.variable} ${poppins.variable} ${playfair.variable}`}
    >
      <body>
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
