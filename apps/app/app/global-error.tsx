"use client";

/**
 * The top-level render boundary (WC-17).
 *
 * Two things happen here, and the second one is the reason this file is not
 * just plumbing.
 *
 * **It records the crash.** Before this the web app had no boundary and no
 * `window` handlers, so a render failure was recorded nowhere at all. The
 * report goes through `lib/errors`, which copies an allow-list — the error's
 * name, the templated route, frame positions — and never the message, because
 * on this product an error message can quote what somebody wrote.
 *
 * **It keeps the support door open.** A crash in a mental-health app must not
 * be a dead end: whoever is holding the phone may be having a much worse
 * evening than the stack trace is. The crisis line is a plain `<a>` to a route
 * that renders without any of the state that just failed, so it works even if
 * the app's data layer is the thing that broke.
 */

import { useEffect } from "react";
import { capture } from "@/lib/errors";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    capture(error, { via: "boundary" });
  }, [error]);

  return (
    <html lang="en">
      <body>
        <div className="authwrap theme-night">
          <div className="authcard">
            <p className="eyebrow">Something broke</p>
            <h1 className="serif-h" style={{ marginTop: 6 }}>
              That is on us, not on you.
            </h1>
            <p className="sub">
              This screen stopped working. Nothing you had already saved is affected — your
              journal, check-ins and plan are on the server, not in this page.
            </p>
            <div style={{ display: "flex", gap: 8, marginTop: 16, flexWrap: "wrap" }}>
              <button className="btn" onClick={() => reset()}>
                Try this screen again
              </button>
              <a className="btn ghost" href="/home">
                Go to Today
              </a>
            </div>
            {/* A plain link, not a router push: the router is part of what may
                have just failed, and this is the one door that has to open. */}
            <p className="footnote" style={{ marginTop: 18 }}>
              If you need a person right now, <a href="/crisis">support is here</a> — it works
              even when this does not.
            </p>
          </div>
        </div>
      </body>
    </html>
  );
}
