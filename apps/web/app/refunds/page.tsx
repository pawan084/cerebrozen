import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = pageMeta({
  title: "Cancellations & refunds",
  description: "How cancelling CereBro Premium works, and how refunds are handled.",
  path: "/refunds",
});

// Honest about the current state: paid billing ships with the store apps.
// This page exists BEFORE billing so the promise is written down first and the
// implementation has something to be held to — not the other way round.
export default function Refunds() {
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
            <Link href="/#pricing">Pricing</Link>
            <Link href="/terms">Terms</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">No dark patterns</p>
          <h1>Cancellations &amp; refunds</h1>

          <h2>Cancelling</h2>
          <ul>
            <li><strong>Cancel anytime, in one place.</strong> Your subscription keeps working until the end of the period you&apos;ve paid for, then simply doesn&apos;t renew. No retention screens, no phone calls.</li>
            <li><strong>Your data is never held hostage.</strong> Cancelling changes what&apos;s unlocked, not what&apos;s yours — export or delete everything from inside the app on any tier, free included.</li>
          </ul>

          <h2>Refunds</h2>
          <ul>
            <li><strong>Purchases made through an app store</strong> (App Store / Google Play, once the store apps launch) are refunded through that store&apos;s own process — the store, not CereBro, holds the payment. We&apos;ll link the exact steps here at launch.</li>
            <li><strong>If a billing mistake is ours</strong> — a double charge, a charge after cancellation — write to <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a> and we&apos;ll put it right. That&apos;s a promise about our behaviour, not a legal grey area.</li>
            <li><strong>Web billing isn&apos;t live yet.</strong> Today the browser app&apos;s free tier needs no payment details at all; this section will state the web refund window plainly before web payments launch.</li>
          </ul>

          <h2>The plain-words summary</h2>
          <p>
            You should never have to be clever to stop paying us. If anything about
            cancelling or a charge feels wrong, email{" "}
            <a href="mailto:support@cerebrozen.in">support@cerebrozen.in</a> — a
            person reads it.
          </p>

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
