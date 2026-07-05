"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import AuthPanel from "@/components/AuthPanel";
import { setOnboarded } from "@/lib/api";

export default function SignIn() {
  const router = useRouter();

  function onAuthed() {
    // A returning sign-in means this device is already introduced — skip the
    // funnel on subsequent loads (mirrors iOS: sign-in sets hasOnboarded).
    setOnboarded();
    router.replace("/home");
  }

  return (
    <div className="authwrap">
      <div className="authcard">
        <p className="eyebrow">Private by design</p>
        <h1>Sign in</h1>
        <p className="sub">
          Keep your plan, journal and check-ins in sync across devices.
        </p>
        <AuthPanel initialMode="signIn" onAuthed={onAuthed} />
        <p className="swap">
          New here? <Link href="/onboarding">Start with a 2-minute reset</Link>
        </p>
      </div>
    </div>
  );
}
