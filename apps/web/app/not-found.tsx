import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

// Custom 404 — same nav, brand and palette as the rest of the site, and calm copy
// (no "oops", no exclamation marks; house tone). Built from existing classes only.
export default function NotFound() {
  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>

      <nav className="nav">
        <div className="container nav-inner">
          <Link className="brand" href="/">
            <BrandMark size={26} /> CereBro
          </Link>
          <div className="nav-links">
            <Link href="/">Home</Link>
            <Link href="/support">Support</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main" style={{ paddingTop: 96 }}>
        <div className="container">
          <div className="section-head" style={{ marginBottom: 28 }}>
            <p className="eyebrow">404</p>
            <h1 style={{ fontSize: "clamp(32px, 5vw, 52px)", lineHeight: 1.1 }}>
              This page isn&apos;t here
            </h1>
            <p>
              The link may be old, or the page may have moved. Nothing is lost —
              here are the quiet ways back.
            </p>
          </div>
          <div className="hero-cta" style={{ justifyContent: "center" }}>
            <Link className="btn btn-primary" href="/">Back to home</Link>
            <Link className="btn btn-ghost" href="/support">Get support</Link>
          </div>
          <p className="disclaimer">
            CereBro is wellness support, not emergency care. If you are in immediate
            danger, contact your local emergency services right away.
          </p>
        </div>
      </main>

      <SiteFooter />
    </>
  );
}
