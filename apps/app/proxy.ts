import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/* Nonce-based Content-Security-Policy for the employee app.
   (Next 16 renamed the `middleware` file convention to `proxy` — same mechanism.)

   The point is `script-src`: no 'unsafe-inline', so an injected <script> cannot run
   unless it carries this request's unguessable nonce. Next attaches the nonce to its
   own framework/bundle tags automatically during SSR, so nothing here is per-tag.

   THIS is the app that holds the coaching content — journals, transcripts, check-ins —
   and it was the one WITHOUT a CSP. The admin got one because docs/TODO.md said "Admin:
   nonce-CSP" and under-specified it; the Zen reference ships one on all three of its web
   apps, which is what surfaced the gap. The more sensitive surface went unprotected for
   longer than the less sensitive one, purely because a checklist named the wrong app.

   Two policies below are deliberate departures from the framework's stock example, and
   both would otherwise break this app:

   1. `connect-src` must name the platform API and the engine. Everything here is
      client-fetched — the coach's SSE stream included — and stock `default-src 'self'`
      would block every request and render an empty shell. The origins come from the same
      env the client bundle is built against, so they cannot drift.

   2. `style-src` allows 'unsafe-inline' and carries NO nonce — on purpose. The app styles
      with `style={{ ... }}` attributes throughout, and a nonce covers <style> elements but
      never style attributes; per CSP3 a nonce in style-src would also *disable*
      'unsafe-inline' and break every one of them. The standard trade: XSS defence lives in
      script-src, and inline styles are a far smaller exposure than inline scripts. */

/** Origin (scheme://host:port) of a URL — connect-src matches origins, not paths.
 *  Prod points API and engine at the same host (…/engine is a path), hence the dedupe. */
function originOf(url: string): string | null {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

function apiOrigins(): string[] {
  // Same defaults as lib/api.ts and lib/coach.ts — if those fallbacks change, change these.
  const urls = [
    process.env.NEXT_PUBLIC_API_URL || "http://localhost:8100",
    process.env.NEXT_PUBLIC_ENGINE_URL || "http://localhost:8000",
  ];
  return [...new Set(urls.map(originOf).filter((o): o is string => o !== null))];
}

export function buildCsp(nonce: string, isDev: boolean): string {
  const connect = ["'self'", ...apiOrigins()];
  // Next's dev server pushes HMR over a websocket; without this dev is unusable.
  if (isDev) connect.push("ws:", "wss:");

  const policy = [
    "default-src 'self'",
    // 'strict-dynamic': scripts the nonce'd bundle loads inherit trust, so the
    // chunk graph works without whitelisting each file.
    // 'unsafe-eval' is dev-only — React uses eval to rebuild server error stacks.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ""}`,
    "style-src 'self' 'unsafe-inline'", // see (2) above — no nonce, deliberately
    "img-src 'self' blob: data:",
    "font-src 'self'",
    `connect-src ${connect.join(" ")}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ];
  // Only in deployed envs: it would rewrite the http://localhost API origins a dev
  // stack talks to, and there is no TLS there to upgrade to.
  if (!isDev) policy.push("upgrade-insecure-requests");
  return policy.join("; ");
}

export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const csp = buildCsp(nonce, process.env.NODE_ENV === "development");

  // The nonce rides the REQUEST headers so the renderer can read it back out; Next
  // parses the CSP header it finds there to learn which nonce to stamp on its tags.
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

export const config = {
  matcher: [
    {
      // Static assets and API routes don't render a document, so they need no policy.
      source: "/((?!api|_next/static|_next/image|favicon.ico).*)",
      // Prefetches aren't documents either — skip them so they stay cheap.
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
