"use client";

// Creating an account IS the funnel now: value-first steps, then "Save your
// space". Kept as a route so existing /signup links land somewhere sensible.
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function SignUp() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/onboarding");
  }, [router]);
  return null;
}
