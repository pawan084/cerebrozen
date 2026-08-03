"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import AuthPanel, { type AuthOutcome } from "@/components/AuthPanel";
import { unlockAnalytics } from "@/lib/analytics";
import { hasOnboarded, setOnboarded } from "@/lib/api";
import { safeNext } from "@/lib/nextPath";

export default function SignIn() {
  const router = useRouter();

  function onAuthed(outcome: AuthOutcome) {
    // An authenticated session unlocks anonymous telemetry (DPDP gate —
    // matching iOS/Android, without re-asking a returning user).
    unlockAnalytics();
    // A FRESH account created here never made its privacy choices — this page
    // used to mark the device onboarded and drop new users on /home with the
    // consent step unseen (server all-off defaults protected them, but a
    // choice nobody was shown isn't consent). New accounts — and OTP sessions
    // on a device that never finished the funnel, since the code path signs up
    // new addresses — resume the funnel at its post-signup steps instead.
    if (outcome === "signUp" || (outcome === "otp" && !hasOnboarded())) {
      router.replace("/onboarding");
      return;
    }
    // A returning sign-in means this device is already introduced — skip the
    // funnel on subsequent loads (mirrors iOS: sign-in sets hasOnboarded).
    setOnboarded();
    // Land on whatever sent them here (a landing-page deep link, or the screen
    // their session expired on). `safeNext` rejects anything off-origin —
    // read via `window` rather than useSearchParams so no Suspense boundary is
    // needed for this one value.
    const next = safeNext(new URLSearchParams(window.location.search).get("next"));
    router.replace(next ?? "/home");
  }

  return (
    <div className="authwrap theme-night">
      <div className="authcard">
        <p className="eyebrow">Private by design</p>
        <h1>Sign in</h1>
        <p className="sub">
          Keep your plan, journal and check-ins in sync across devices.
        </p>
        <AuthPanel initialMode="signIn" requireAgeAttest onAuthed={onAuthed} />
        <p className="swap">
          New here? <Link href="/onboarding">Start with a 2-minute reset</Link>
        </p>
        {/* Support stays reachable without an account — signing in is never a
            condition of finding a helpline. */}
        <p className="footnote">
          Need urgent help? <Link href="/support">Support — real people, 24/7</Link>
        </p>
      </div>
    </div>
  );
}
