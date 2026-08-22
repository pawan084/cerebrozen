"use client";

/**
 * Installs the two handlers a React boundary never sees (WC-17).
 *
 * `global-error.tsx` catches render failures. It does not catch a rejected
 * promise nobody awaited — which on this client is the shape most failures
 * actually take, because every screen loads through `lib/api` — nor a script
 * error thrown outside the tree.
 *
 * Renders nothing. Mounted once in the root layout so it covers the signed-out
 * screens too: onboarding and sign-in are exactly where a first-time user's
 * crash goes unreported, since they never reach an authed layout.
 */

import { useEffect } from "react";
import { installGlobalHandlers } from "@/lib/errors";

export default function ErrorReporter() {
  useEffect(() => installGlobalHandlers(), []);
  return null;
}
