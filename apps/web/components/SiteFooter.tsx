import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { appHref } from "@/lib/appUrl";

// The five app spaces, in tab order — the same list the landing's "calm spaces"
// section renders. Kept here too so /privacy, /terms and /support are not dead
// ends: before this, every page except the landing offered no way into the app.
const APP_LINKS: [label: string, route: string][] = [
  ["Home", "/home"],
  ["Sleep", "/sleep"],
  ["Talk", "/chat"],
  ["Journal", "/journal"],
  ["You", "/account"],
];

/**
 * One footer for every page on the marketing site.
 *
 * It replaced four hand-copied blocks (landing, privacy, terms, support, 404)
 * that had already drifted — only one of them was ever going to get new links.
 */
export function SiteFooter() {
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-cols">
          <div className="footer-brand">
            <Link className="brand" href="/" style={{ fontSize: 17 }}>
              <BrandMark size={26} /> CereBro
            </Link>
            <p>
              A quiet space for a calmer mind — private by design, and honest about
              what it is.
            </p>
          </div>

          <nav className="footer-col" aria-label="Open the app">
            <strong>Open the app</strong>
            {APP_LINKS.map(([label, route]) => (
              <a href={appHref(route)} key={label}>{label}</a>
            ))}
          </nav>

          <nav className="footer-col" aria-label="Your account">
            <strong>Account</strong>
            <a href={appHref("/signin")}>Sign in</a>
            <a href={appHref("/signup")}>Create an account</a>
            <Link href="/#pricing">Pricing</Link>
            <Link href="/#waitlist">iOS waitlist</Link>
          </nav>

          <nav className="footer-col" aria-label="Trust and support">
            <strong>Trust</strong>
            <Link href="/privacy">Privacy</Link>
            <Link href="/terms">Terms</Link>
            <Link href="/support">Crisis support</Link>
          </nav>
        </div>

        <div className="footer-base">
          <span className="footer-copy">© {new Date().getFullYear()} CereBro</span>
          <span>Wellness support — not therapy, diagnosis, or a crisis service.</span>
        </div>
      </div>
    </footer>
  );
}
