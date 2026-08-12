// Per-request CSP with a script nonce — drops 'unsafe-inline' for scripts.
//
// The fourth hand-copy of this file. apps/web, apps/admin and apps/app have
// carried it for a while; the portal shipped without one because it was never
// wired into the stack, so nothing built it, served it, or checked it. Wiring
// it in without this would have deployed the least-protected surface in the
// product on the host that shows an employer their organisation's data.
//
// Next.js reads the nonce from the Content-Security-Policy REQUEST header we
// forward and stamps it on every framework inline script. That only happens on
// dynamically-rendered pages, so the root layout forces dynamic rendering.
//
// Duplicated by hand across apps/web, apps/admin, apps/app and apps/portal —
// per-app Docker build contexts, same reason the globals.css token blocks are
// per-app copies. scripts/check-csp-sync.mjs pins the floor across all four.
import { NextRequest, NextResponse } from "next/server";

// The API origin browsers may call. The portal renders no server data yet —
// every screen is still reference layout — but it is an administration client
// whose whole purpose is aggregate reporting, so the seam is declared here in
// the same shape as its three siblings rather than tightened now and forgotten
// when the first fetch lands.
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
    `worker-src 'self'`,
    `style-src 'self' 'unsafe-inline'`, // Next injects inline styles (styled-jsx / next/font)
    // Tighter than apps/admin on purpose: the admin console previews artwork
    // from a third-party CDN and needs https:. Nothing in the portal renders a
    // remote image, so it keeps the stricter policy the other two use.
    `img-src 'self' data: blob:`,
    `font-src 'self' data:`,
    `connect-src 'self' ${API_ORIGIN}`,
    // An organisation portal is a plausible clickjacking target — it carries
    // destructive actions (member removal, campaign send) behind a session.
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
