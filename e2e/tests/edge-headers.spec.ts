import { test, expect, request as pwRequest, APIRequestContext } from "@playwright/test";

/**
 * The headers `deploy/Caddyfile` sets, served by Caddy actually running it.
 *
 * This tier used to be unreachable from any test in the repo. The stack talked
 * to the Next apps and the API directly, so nothing exercised the edge, and a
 * dropped `import security_headers` would have passed CI, passed review as one
 * deleted line in a file nobody opens on a feature branch, and shipped — with
 * HSTS quietly missing in production as the only evidence.
 *
 * `scripts/check-edge-headers.mjs` reads that file statically. This runs it. The
 * two answer different questions and neither replaces the other: the script
 * catches a header removed from the source, and this catches a header the
 * source claims to set that never reaches a response — a directive in the wrong
 * block, a snippet that silently fails to apply, an upstream overwriting it.
 *
 * The config here is the production file, mounted read-only, with `local_certs`
 * added at boot so Caddy uses its own CA instead of Let's Encrypt.
 * `e2e/caddy-testable.sh` strips that line back out and diffs against the
 * original, refusing to start if anything else differs — so these tests cannot
 * pass against a config we do not ship.
 */

/** Caddy holds network aliases for each of these, so SNI matches its site blocks. */
const SITES = {
  landing: "https://cerebrozen.in",
  admin: "https://admin.cerebrozen.in",
  app: "https://app.cerebrozen.in",
  api: "https://api.cerebrozen.in",
};

async function edge(): Promise<APIRequestContext> {
  // Caddy's internal CA is not in any trust store, which is the point of it.
  return pwRequest.newContext({ ignoreHTTPSErrors: true });
}

for (const [name, origin] of Object.entries(SITES)) {
  test.describe(`${name} through the edge`, () => {
    test("carries every shared security header", async () => {
      const c = await edge();
      const r = await c.get(`${origin}/`, { maxRedirects: 0 });
      const h = r.headers();
      await c.dispose();

      // A year. Below that, preload lists reject the domain and the header is
      // mostly decorative.
      const maxAge = Number(
        (h["strict-transport-security"] ?? "").match(/max-age=(\d+)/)?.[1] ?? 0,
      );
      expect(maxAge, `${name} HSTS`).toBeGreaterThanOrEqual(31536000);

      // Exactly one value each, not two. Playwright joins duplicates with a
      // comma, which is how this suite found api.cerebrozen.in shipping
      // `X-Frame-Options: SAMEORIGIN, DENY`: the edge was APPENDING to a header
      // the app had already set, and a browser seeing two disagreeing values
      // treats the header as invalid and ignores it — so the strictest site in
      // the estate was the one without framing protection. The `?` prefix in
      // the Caddyfile snippet is the fix, and these assertions are its guard.
      expect(h["x-content-type-options"], `${name} sent it twice`).toBe("nosniff");
      expect(h["x-frame-options"], `${name} framing policy`).toMatch(
        /^(DENY|SAMEORIGIN)$/,
      );
      expect(h["referrer-policy"], `${name} referrer policy`).toMatch(
        /^(no-referrer|strict-origin-when-cross-origin|strict-origin)$/,
      );
      // Denied by default rather than left to the browser's default, which on a
      // wellness product is the difference between "the page cannot ask" and
      // "the page may ask".
      expect(h["permissions-policy"] ?? "").toContain("camera=()");
      expect(h["permissions-policy"] ?? "").toContain("microphone=()");
    });
  });
}

test.describe("the site blocks that differ", () => {
  test("the API keeps its locked-down CSP", async () => {
    // FastAPI renders no documents, so this is defence in depth against
    // content-type confusion — and it is the only CSP Caddy still sets, the
    // Next apps having moved theirs into middleware.
    const c = await edge();
    const r = await c.get(`${SITES.api}/health`);
    const csp = r.headers()["content-security-policy"] ?? "";
    await c.dispose();

    expect(csp).toContain("default-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
  });

  test("the admin console is kept out of search indexes", async () => {
    const c = await edge();
    const r = await c.get(`${SITES.admin}/`, { maxRedirects: 0 });
    const robots = r.headers()["x-robots-tag"] ?? "";
    await c.dispose();
    expect(robots).toContain("noindex");
  });

  test("www redirects to the apex, permanently", async () => {
    // One canonical host, so search engines never split the site's authority.
    const c = await edge();
    const r = await c.get("https://www.cerebrozen.in/pricing", { maxRedirects: 0 });
    await c.dispose();
    expect(r.status()).toBe(301);
    expect(r.headers()["location"]).toBe("https://cerebrozen.in/pricing");
  });
});

test.describe("the edge does not fight the apps", () => {
  test("the per-request nonce CSP survives the proxy intact", async () => {
    // Caddy deliberately sets no CSP for the Next sites — theirs is minted
    // per request with a script nonce. If a CSP were ever re-added at the edge
    // the two would both apply, and the intersection would break the apps in a
    // way that looks like a blank page rather than like a header problem.
    const c = await edge();
    const direct = await c.get("http://web:3000/");
    const proxied = await c.get(`${SITES.landing}/`, { maxRedirects: 0 });

    const directCsp = direct.headers()["content-security-policy"] ?? "";
    const proxiedCsp = proxied.headers()["content-security-policy"] ?? "";
    await c.dispose();

    expect(directCsp, "the app served no CSP of its own").not.toBe("");
    // Exactly one policy, and the app's — same directives, only the nonce
    // differing because it is minted per request.
    expect(proxiedCsp.split(";").length).toBe(directCsp.split(";").length);
    expect(proxiedCsp).toContain("object-src 'none'");
    expect(proxiedCsp).toMatch(/'nonce-[^']+'/);
    expect(proxiedCsp, "the edge added a second, conflicting policy").not.toContain(
      "default-src 'none'",
    );
  });

  test("a static asset keeps its cache policy AND its security headers", async () => {
    // The landing block adds a Cache-Control for /brand/* — a `header @static`
    // matcher sitting beside the imported snippet. Caddy applies matched header
    // directives independently, but that is worth an assertion rather than an
    // assumption: a mistake here strips security headers from a whole path
    // prefix and nothing looks broken.
    const c = await edge();
    const r = await c.get(`${SITES.landing}/brand/`, { maxRedirects: 0 });
    const h = r.headers();
    await c.dispose();

    expect(h["x-content-type-options"], "/brand/* lost its security headers").toBe(
      "nosniff",
    );
    expect(h["strict-transport-security"] ?? "").toContain("max-age=");
  });
});
