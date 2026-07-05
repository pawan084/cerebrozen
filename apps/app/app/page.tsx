"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { hasOnboarded, hasSession } from "@/lib/api";

export default function Index() {
  const router = useRouter();
  useEffect(() => {
    // Signed in → app. Seen the funnel before → straight to sign-in. First
    // visit → the value-first onboarding funnel.
    if (hasSession()) router.replace("/home");
    else if (hasOnboarded()) router.replace("/signin");
    else router.replace("/onboarding");
  }, [router]);
  return null;
}
