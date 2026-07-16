import type { NextConfig } from "next";

/* Security headers for the marketing site.
 *
 * This site shipped ZERO of them while hosting a 257-line /security page selling our
 * security posture — a claim with no mechanism, which is what CLAUDE.md rule 6 exists to
 * prevent. A prospect's CISO runs securityheaders.io before the demo call, and
 * docs/PRODUCT.md is explicit that buyers "only approve one that survives a security
 * review".
 *
 * ═══ Why this is NOT the nonce CSP the other two apps use ═══
 *
 * apps/admin and apps/app set a per-request nonce from a `proxy.ts`, which forces every
 * page into dynamic rendering — Next stamps the nonce during SSR, and a prerendered page
 * has no request to read it from. That is free for them: both are authenticated tools that
 * must not be cached anyway.
 *
 * It is not free here. Every route on this site is static, and the site's whole job is to
 * be fast and indexable. The Zen reference took the nonce on its marketing site too, and
 * its own layout comment concedes the trade; we don't inherit it.
 *
 * So: a static CSP, and `script-src` keeps 'unsafe-inline'. That is weaker than the other
 * two apps and it is the right trade HERE, for a reason specific to this site: there is
 * nothing to steal. No auth, no tokens, no session, and no user-generated content rendered
 * back — the only input is the demo form, which posts and renders nothing. The XSS an
 * inline script could achieve is defacement, which `frame-ancestors`, HTTPS and the rest of
 * this list already bound, and which is not worth making every page dynamic for.
 *
 * SRI is on anyway, and it earns its place independently: Next hashes its bundles at build
 * time and emits `integrity` attributes, so a compromised CDN cannot serve modified JS.
 *
 * MEASURED, not assumed: SRI does NOT remove the need for 'unsafe-inline'. Upstream's guide
 * says SRI "allows you to maintain static generation while still having a strict CSP", but
 * integrity attributes only cover EXTERNAL <script src> tags — Next's inline RSC bootstrap
 * (`self.__next_f.push(...)`) still needs a nonce or 'unsafe-inline'. Tried `script-src
 * 'self'` with SRI first: the browser blocked every inline bootstrap, hydration died, the
 * Sign in menu stopped opening and the animated stats sat at +0%.
 *
 * `connect-src 'self'` is honest here: the only thing this site's browser code fetches is
 * its own /api/demo route (DemoForm), which posts server-side to the platform. No API
 * origin is exposed to the browser at all — strictly better than the reference, which
 * hands one out.
 */

const isDev = process.env.NODE_ENV === "development";

const csp = [
  "default-src 'self'",
  // 'unsafe-inline' is required for Next's inline RSC bootstrap on a STATIC page — SRI
  // does not cover it (see the header). 'unsafe-eval' is dev-only.
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  // Styles keep 'unsafe-inline' with no nonce: next/font and Tailwind emit inline styles,
  // and per CSP3 a nonce here would *disable* 'unsafe-inline' rather than add to it.
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  // Same-origin only. The demo form posts to our own route handler; nothing else calls out.
  `connect-src 'self'${isDev ? " ws: wss:" : ""}`,
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  // 'none', not 'self': a marketing site has no reason to frame itself, and this is the
  // clickjacking control that actually matters on the pages carrying our claims.
  "frame-ancestors 'none'",
  ...(isDev ? [] : ["upgrade-insecure-requests"]),
].join("; ");

const nextConfig: NextConfig = {
  experimental: {
    // Not for the CSP (it cannot make script-src strict — see the header), but for its own
    // sake: integrity attributes mean a compromised CDN cannot serve modified bundles.
    sri: { algorithm: "sha256" },
  },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          // Belt to the CSP's braces: frame-ancestors supersedes this for modern browsers,
          // but it costs nothing and older ones still read it.
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          // The site links out to the app./admin. subdomains; don't leak which marketing
          // page a prospect was reading when they clicked.
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
