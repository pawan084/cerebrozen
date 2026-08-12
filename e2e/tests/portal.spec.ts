import { test, expect } from "@playwright/test";

const PORTAL = process.env.PORTAL_URL || "http://portal:3003";

// The organisation portal (ref/portal.html), wired into the stack 2026-08-12.
//
// It existed as source for a while with no image, no compose service and no CI
// step, so nothing built it and nothing would have reported it broken. These
// tests are the other half of wiring it in: a service nobody checks is only
// marginally better than a service nobody runs.
//
// Deliberately a smoke suite over every built route rather than deep assertions
// on a few. Only 10 of the reference's 36 screens exist so far (docs/TODO.md),
// and the failure this guards is a route that 500s or renders blank after a
// refactor — not the wording of a screen still being designed.
test.describe("Organisation portal", () => {
  const routes = [
    ["/", "Support people without watching them."],
    ["/members", "Organisation portal"],
    ["/cohorts", "Organisation portal"],
    ["/cohorts/new", "Organisation portal"],
    ["/programmes", "Organisation portal"],
    ["/campaigns", "Organisation portal"],
    ["/engagement", "Organisation portal"],
    ["/preview", "Organisation portal"],
    ["/privacy", "Privacy guardrails by design."],
    ["/roles", "Organisation portal"],
  ] as const;

  for (const [path, expected] of routes) {
    test(`${path} renders`, async ({ page }) => {
      const response = await page.goto(`${PORTAL}${path}`, { waitUntil: "networkidle" });
      expect(response?.status(), `${path} did not return 200`).toBe(200);
      await expect(page.getByText(expected).first()).toBeVisible();
    });
  }

  test("every nav destination resolves", async ({ page }) => {
    // The portal's own sidebar is the contract: a link in it that 404s is the
    // exact defect that hid in the Android graph for a whole redesign.
    await page.goto(PORTAL, { waitUntil: "networkidle" });
    const hrefs = await page.locator("nav a[href^='/']").evaluateAll((els) =>
      Array.from(new Set(els.map((e) => (e as HTMLAnchorElement).getAttribute("href")!))),
    );
    expect(hrefs.length, "found no sidebar links — has the Shell changed?").toBeGreaterThan(4);
    for (const href of hrefs) {
      const res = await page.request.get(`${PORTAL}${href}`);
      expect(res.status(), `sidebar links to ${href}, which does not resolve`).toBe(200);
    }
  });

  test("carries the same CSP floor as the other three apps", async ({ page }) => {
    // The portal shipped without a middleware.ts because it was never served.
    // Wiring it in without one would have put the least-protected surface in
    // the product on the host that shows an employer their organisation's data.
    const response = await page.goto(PORTAL, { waitUntil: "domcontentloaded" });
    const csp = response?.headers()["content-security-policy"] ?? "";
    for (const directive of [
      "default-src 'self'",
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors",
      "worker-src 'self'",
    ]) {
      expect(csp, `CSP is missing ${directive}`).toContain(directive);
    }
    // Production builds must use a nonce, never blanket inline script.
    expect(csp).toContain("'nonce-");
    expect(csp).not.toContain("'unsafe-eval'");
  });

  test("is not indexable", async ({ page }) => {
    // An administration console has no business in a search index; the layout
    // sets robots noindex and this keeps it set.
    await page.goto(PORTAL, { waitUntil: "domcontentloaded" });
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute(
      "content",
      /noindex/,
    );
  });
});
