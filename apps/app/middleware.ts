// Per-request CSP with a script nonce — drops 'unsafe-inline' for scripts.
//
// Next.js reads the nonce from the Content-Security-Policy REQUEST header we
// forward and stamps it on every framework inline script. That only happens on
// dynamically-rendered pages, so the root layout forces dynamic rendering
// (nothing here used static rendering — all data is client-fetched anyway).
// The response carries the same policy for the browser to enforce; Caddy no
// longer sets a CSP for the Next.js sites (deploy/Caddyfile keeps one for the
// API only).
//
// Duplicated by hand across apps/web, apps/admin, apps/app — per-app Docker
// build contexts, same reason the globals.css token blocks are per-app copies.
import { NextRequest, NextResponse } from "next/server";

// The API origin browsers may call — the same build-time seam lib/api uses.
const API_ORIGIN = (() => {
  try {
    return new URL(process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000").origin;
  } catch {
    return "http://localhost:8000";
  }
})();

export function middleware(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  // `next dev` needs eval (react-refresh) and un-nonced inline bootstraps; the
  // strict nonce policy applies to every production build (Docker dev/e2e/prod).
  const script =
    process.env.NODE_ENV === "production"
      ? `'self' 'nonce-${nonce}'`
      : `'self' 'unsafe-inline' 'unsafe-eval'`;
  const csp = [
    `default-src 'self'`,
    `script-src ${script}`,
    // Explicit so the service worker (apps/app /sw.js) can never be broken by
    // a future script-src change ('strict-dynamic' would ignore 'self').
    `worker-src 'self'`,
    `style-src 'self' 'unsafe-inline'`, // Next injects inline styles (styled-jsx / next/font)
    `img-src 'self' data: blob:`,
    `font-src 'self' data:`,
    `connect-src 'self' ${API_ORIGIN}`,
    // Narration audio streams from the API's public /media mount.
    `media-src 'self' ${API_ORIGIN}`,
    `frame-ancestors 'self'`,
    `object-src 'none'`,
    `base-uri 'self'`,
    `form-action 'self'`,
  ].join("; ");

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);
  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

export const config = {
  // Documents only: static assets don't execute as documents, and router
  // prefetches shouldn't be forced through per-request rendering.
  matcher: [
    {
      source: "/((?!_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
