import { test, expect, request as pwRequest } from "@playwright/test";

/**
 * Security headers, asserted against the running stack (WC-87).
 *
 * These headers are the kind of thing that regresses silently: nothing looks
 * broken when a CSP loosens, and the first sign is an incident. So the point of
 * this file is not that the headers exist — it is that a change which weakens
 * them turns the build red.
 *
 * **What this file can and cannot reach.** The e2e stack runs the four Next.js
 * apps and the API directly; there is no Caddy in front of them. So the CSP
 * (set per-request by each app's `middleware.ts`) and the API's baseline
 * headers are what this file covers.
 *
 * The edge headers in `deploy/Caddyfile` used to be out of reach here and are
 * not any more: the stack runs a `caddy` service on the production config, and
 * `edge-headers.spec.ts` drives it. Keep the two files separate — this one
 * asserts what an app emits, that one asserts what a client finally receives,
 * and the difference between those is where the duplicated
 * `X-Frame-Options: SAMEORIGIN, DENY` was hiding.
 */

const WEB = process.env.WEB_URL || "http://web:3000";
const ADMIN = process.env.ADMIN_URL || "http://admin:3001";
const APP = process.env.APP_URL || "http://app:3002";
const PORTAL = process.env.PORTAL_URL || "http://portal:3003";
const API = process.env.API_URL || "http://api:8000";

/** Directives every one of the four apps must carry, regardless of its extras. */
const REQUIRED = [
  "default-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "worker-src 'self'",
];

const APPS = [
  { name: "landing", url: WEB, path: "/" },
  { name: "admin", url: ADMIN, path: "/" },
  { name: "web app", url: APP, path: "/signin" },
  { name: "portal", url: PORTAL, path: "/" },
];

function directive(csp: string, name: string): string {
  const found = csp
    .split(";")
    .map((d) => d.trim())
    .find((d) => d === name || d.startsWith(`${name} `));
  return found ?? "";
}

for (const app of APPS) {
  test.describe(`${app.name} security headers`, () => {
    test("serves a CSP with every non-negotiable directive", async ({ page }) => {
      const response = await page.goto(`${app.url}${app.path}`, {
        waitUntil: "domcontentloaded",
      });
      const csp = response?.headers()["content-security-policy"] ?? "";
      expect(csp, `${app.name} served no CSP at all`).not.toBe("");
      for (const required of REQUIRED) {
        expect(csp, `${app.name} CSP is missing "${required}"`).toContain(required);
      }
      // frame-ancestors is what actually stops clickjacking on a modern
      // browser; X-Frame-Options is the legacy half of the same job.
      expect(directive(csp, "frame-ancestors")).not.toBe("");
    });

    test("script-src is nonce-based, with no inline or eval escape hatch", async ({
      page,
    }) => {
      // The whole point of the per-request nonce is that it replaces
      // 'unsafe-inline'. If both are present, browsers ignore the nonce and the
      // policy silently degrades to the one it was written to replace — the
      // most likely way this regresses, and it looks fine in a diff.
      const response = await page.goto(`${app.url}${app.path}`, {
        waitUntil: "domcontentloaded",
      });
      const csp = response?.headers()["content-security-policy"] ?? "";
      const scriptSrc = directive(csp, "script-src");

      expect(scriptSrc, `${app.name} has no script-src`).not.toBe("");
      expect(scriptSrc, `${app.name} script-src allows inline scripts`).not.toContain(
        "'unsafe-inline'",
      );
      expect(scriptSrc, `${app.name} script-src allows eval`).not.toContain(
        "'unsafe-eval'",
      );
      expect(scriptSrc, `${app.name} script-src carries no nonce`).toMatch(/'nonce-[^']+'/);
    });

    test("the nonce is minted per request, not once per build", async () => {
      // A constant nonce is exactly as good as 'unsafe-inline' — an attacker
      // who can read one page can reuse it forever. This is the assertion that
      // catches somebody hoisting the nonce to module scope, which is a change
      // no reviewer would flag and no page would visibly break.
      const ctx = await pwRequest.newContext();
      const first = await ctx.get(`${app.url}${app.path}`);
      const second = await ctx.get(`${app.url}${app.path}`);
      const nonceOf = (r: typeof first) =>
        (r.headers()["content-security-policy"] ?? "").match(/'nonce-([^']+)'/)?.[1];

      const a = nonceOf(first);
      const b = nonceOf(second);
      await ctx.dispose();

      expect(a, `${app.name} served no nonce`).toBeTruthy();
      expect(b).toBeTruthy();
      expect(a, `${app.name} reuses one nonce across requests`).not.toBe(b);
    });

    test("the nonce in the header is the one stamped on the markup", async () => {
      // Two nonces that disagree is not a security hole, it is a broken page —
      // every framework script would be blocked. Asserted because the failure
      // arrives as "the app is blank" rather than as anything mentioning CSP.
      const ctx = await pwRequest.newContext();
      const response = await ctx.get(`${app.url}${app.path}`);
      const csp = response.headers()["content-security-policy"] ?? "";
      const headerNonce = csp.match(/'nonce-([^']+)'/)?.[1];
      const body = await response.text();
      await ctx.dispose();

      const stamped = [...body.matchAll(/nonce="([^"]+)"/g)].map((m) => m[1]);
      expect(stamped.length, `${app.name} stamped no nonce on any script`).toBeGreaterThan(0);
      for (const one of new Set(stamped)) {
        expect(one, `${app.name} markup nonce differs from the header's`).toBe(headerNonce);
      }
    });
  });
}

test.describe("API security headers", () => {
  test("every response carries the baseline hardening set", async () => {
    const ctx = await pwRequest.newContext();
    const response = await ctx.get(`${API}/health`);
    const headers = response.headers();
    await ctx.dispose();

    expect(response.status()).toBe(200);
    expect(headers["x-content-type-options"]).toBe("nosniff");
    // DENY, not SAMEORIGIN: the API renders no documents of its own, so there
    // is no frame of ours it would ever legitimately sit inside.
    expect(headers["x-frame-options"]).toBe("DENY");
    expect(headers["referrer-policy"]).toBe("no-referrer");
  });

  test("an error response is hardened too, not just a happy path", async () => {
    // Middleware that runs only on success is a common shape, and a 404 body is
    // as sniffable as a 200 one.
    const ctx = await pwRequest.newContext();
    const response = await ctx.get(`${API}/no-such-route-${Date.now()}`);
    const headers = response.headers();
    await ctx.dispose();

    expect(response.status()).toBe(404);
    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["x-frame-options"]).toBe("DENY");
  });
});
