import type { Metadata } from "next";
import { pageMeta } from "@/lib/pageMeta";
import Link from "next/link";
import { BrandMark } from "@/components/BrandMark";
import { SiteFooter } from "@/components/SiteFooter";

export const metadata: Metadata = pageMeta({
  title: "Delete your account",
  description: "How to permanently delete your CereBro account and all associated data — from inside the app, or by email without installing it.",
  path: "/delete-account",
});

// Google Play requires a publicly reachable URL where a user can request account
// and data deletion WITHOUT installing the app (Play Console → App content →
// Data safety). The in-app path has always existed (You → Privacy & data →
// Delete account, which calls DELETE /users/me); this page is the off-app route
// to the same irreversible deletion, and the URL pasted into the Play listing.
export default function DeleteAccount() {
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
            <Link href="/privacy">Privacy</Link>
            <Link href="/support">Support</Link>
          </div>
        </div>
      </nav>

      <main className="section" id="main">
        <div className="container legal">
          <p className="eyebrow">Your data, your call</p>
          <h1>Delete your account</h1>
          <p className="muted">Applies to the CereBro app (com.cerebrozen.app) and cerebrozen.in</p>

          <p>
            You can permanently delete your CereBro account and everything stored with it.
            Deletion is immediate and irreversible — we cannot restore an account once it is
            gone, so please export anything you want to keep first.
          </p>

          <h2>Option 1 — from inside the app (fastest)</h2>
          <ul>
            <li>Open CereBro and go to <strong>You → Privacy &amp; data → Delete account</strong>.</li>
            <li>Confirm. The account and its data are erased right away.</li>
          </ul>
          <p>
            The same screen offers <strong>Export my data</strong> if you want a copy before you go.
          </p>

          <h2>Option 2 — by email (no app needed)</h2>
          <p>
            If you have uninstalled the app or cannot sign in, write to{" "}
            <a href="mailto:privacy@cerebrozen.in?subject=Delete%20my%20CereBro%20account">
              privacy@cerebrozen.in
            </a>{" "}
            from the email address on the account, with the subject
            &quot;Delete my CereBro account&quot;.
          </p>
          <ul>
            <li>We verify that the request comes from the account holder before erasing anything — that check protects you from someone else deleting your account.</li>
            <li>We action verified requests within <strong>30 days</strong>, and in practice much sooner.</li>
            <li>You will get a confirmation once it is done.</li>
          </ul>

          <h2>What gets deleted</h2>
          <p>Everything tied to your account is erased, not merely hidden or deactivated:</p>
          <ul>
            <li>Account details — email, name, and your hashed password</li>
            <li>Mood check-ins, journal entries, and conversations</li>
            <li>Sleep logs and any sleep data read from Health Connect</li>
            <li>Habits, plans, reminders, and streaks</li>
            <li>Saved preferences, trusted contact, and safety plan</li>
          </ul>

          <h2>What does not get deleted, and why</h2>
          <ul>
            <li>
              <strong>Anonymous usage counts.</strong> These are never linked to your account, so
              there is no &quot;you&quot; in them to remove. You can switch collection off at any
              time in the app.
            </li>
            <li>
              <strong>Records the law requires us to keep.</strong> Payment and tax records, where
              any exist, are retained for the statutory period and for nothing else.
            </li>
          </ul>

          <h2>Sleep data from Health Connect</h2>
          <p>
            If you connected Health Connect, CereBro only ever read sleep data from it — we never
            wrote anything back. Deleting your account removes our copy. The original data stays in
            Health Connect on your device, where you control it independently through Android
            settings.
          </p>

          <h2>Questions</h2>
          <p>
            Write to <a href="mailto:privacy@cerebrozen.in">privacy@cerebrozen.in</a>, or see the{" "}
            <Link href="/privacy">Privacy Policy</Link> for the full picture of what we store and
            for how long.
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
