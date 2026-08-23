"use client";

import { useState } from "react";
import { resendVerification } from "@/lib/api";

/**
 * "Confirm your email" — shown before anything is refused, not after.
 *
 * The server gates the features that cost money to run (voice, plan
 * generation, goal decomposition, assessment, the Oracle) on a confirmed
 * address. Meeting that as a 403 partway through a task is a bad way to learn
 * it exists, so this appears as soon as the profile says the gate applies.
 *
 * It renders on `user.email_verification_required`, which is the server's
 * answer to "will the gate refuse this caller" — NOT whether the address is
 * confirmed. The two differ for everyone the gate exempts: an account older
 * than the rule, a paying subscriber, or a deployment with no mail configured.
 * Rendering the raw flag would nag those people to fix something that is not
 * stopping them.
 *
 * Deliberately a quiet strip rather than a modal or a blocking screen. Nothing
 * here is broken, chat still works, and the one thing this product cannot
 * afford is to put a wall between somebody and the reason they opened it.
 */
export function VerifyEmailNotice({ email }: { email: string }) {
  const [state, setState] = useState<"idle" | "sending" | "sent" | "failed">("idle");

  async function resend() {
    setState("sending");
    try {
      await resendVerification();
      setState("sent");
    } catch {
      // The endpoint is rate limited (5/minute, per account as well as per
      // address), so a rapid second press lands here. Saying so beats a
      // spinner that never resolves.
      setState("failed");
    }
  }

  return (
    <div className="verify-notice" role="status">
      <div className="verify-notice-text">
        <strong>Confirm your email</strong>
        <span>
          We sent a link to {email}. Confirming it unlocks voice, plans and the
          Oracle — everything else works as normal meanwhile.
        </span>
      </div>
      {state === "sent" ? (
        <span className="verify-notice-done">Link sent — check your inbox.</span>
      ) : (
        <button
          type="button"
          className="verify-notice-action"
          onClick={resend}
          disabled={state === "sending"}
        >
          {state === "sending"
            ? "Sending…"
            : state === "failed"
              ? "Try again"
              : "Resend link"}
        </button>
      )}
    </div>
  );
}
