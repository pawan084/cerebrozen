import { test, expect } from "@playwright/test";

const PORTAL = process.env.PORTAL_URL || "http://portal:3003";

// The organisation portal (ref/portal.html), wired into the stack 2026-08-12 and
// completed to all 36 reference routes the same day.
//
// It existed as source for a while with no image, no compose service and no CI
// step, so nothing built it and nothing would have reported it broken. These
// tests are the other half of that: a service nobody checks is only marginally
// better than a service nobody runs.
//
// Every route is walked rather than a sample, because the portal is still a
// design surface over fixed data — the failure this guards is a route that 500s
// or renders blank after a refactor, and that can happen to any of the 36.
//
// Four routes are GUARDED since the portal was connected to /org: signed out,
// they redirect to sign-in. They are listed separately rather than left in the
// walk, because a redirect made the walk pass on the SIGN-IN page's heading —
// green for a route that never rendered.
const GUARDED = [
  "/",
  "/members",
  "/cohorts",
  "/programmes",
  // Wired to /org as forms on 2026-08-12, so these guard too.
  "/privacy",
  "/settings",
  "/members/invite",
  "/cohorts/new",
  // Wired 2026-08-13.
  "/setup",
  "/members/group",
  "/roles/admins",
];

const ROUTES = [
  "/programmes/detail",
  "/programmes/pathway",
  "/campaigns",
  "/campaigns/new",
  "/referrals",
  "/referrals/provider",
  "/preview",
  "/engagement",
  "/outcomes",
  "/reports",
  "/privacy/data-map",
  "/safety",
  "/safety/runbook",
  "/evidence",
  "/security",
  "/integrations",
  "/integrations/detail",
  "/roles",
  "/audit",
  "/billing",
  "/billing/contract",
  "/support",
  "/notifications",
  "/signin",
  "/signin/verify",
];

test.describe("Organisation portal", () => {
  test("every unguarded reference route renders", async ({ page }) => {
    for (const path of ROUTES) {
      const response = await page.goto(`${PORTAL}${path}`, { waitUntil: "domcontentloaded" });
      expect(response?.status(), `${path} did not return 200`).toBe(200);
      await expect(page.locator("h1"), `${path} has no heading`).toBeVisible();
      expect(page.url(), `${path} redirected — is it guarded now?`).toContain(path);
    }
  });

  test("the wired routes are guarded when signed out", async ({ page }) => {
    // These four read the real /org API. Signed out they must go to sign-in,
    // not render an empty shell that looks like an organisation with no data.
    for (const path of GUARDED) {
      await page.goto(`${PORTAL}${path}`, { waitUntil: "networkidle" });
      expect(page.url(), `${path} did not redirect a signed-out visitor`).toContain("/signin");
    }
  });

  test("no page renders a literal unicode escape", async ({ page }) => {
    // These pages were generated, and the generator first emitted \\uXXXX into
    // JSX *text*, where backslash-u is not an escape — so "anyone\\u2019s safety"
    // rendered verbatim on 21 pages. Cheap to check, and invisible in a diff.
    for (const path of [...ROUTES, "/signin"]) {
      await page.goto(`${PORTAL}${path}`, { waitUntil: "domcontentloaded" });
      const body = (await page.locator("body").innerText()) ?? "";
      expect(body, `${path} renders a literal \\uXXXX escape`).not.toMatch(/\\u[0-9a-f]{4}/i);
    }
  });

  test("every sidebar destination resolves", async ({ page }) => {
    // The portal's own sidebar is the contract: a link in it that 404s is the
    // exact defect that hid in the Android graph for a whole redesign.
    await page.goto(PORTAL, { waitUntil: "networkidle" });
    const hrefs = await page.locator("nav a[href^='/']").evaluateAll((els) =>
      Array.from(new Set(els.map((e) => (e as HTMLAnchorElement).getAttribute("href")!))),
    );
    expect(hrefs.length, "found no sidebar links — has the Shell changed?").toBeGreaterThan(15);
    for (const href of hrefs) {
      const res = await page.request.get(`${PORTAL}${href}`);
      expect(res.status(), `sidebar links to ${href}, which does not resolve`).toBe(200);
    }
  });

  test("the sign-in screen is a real form, and claims nothing it lacks", async ({ page }) => {
    // This screen shipped deliberately INERT on 2026-08-12 — it rendered the
    // access flow and authenticated nobody, because there was no backend behind
    // it. The backend now exists (/org), so the form is real and this test
    // asserts the new truth rather than the old one.
    //
    // What is still absent stays absent: there is no identity provider and no
    // demo tenant, so "Continue with SSO" and "Open demo workspace" were not
    // ported. Both would be the same lie in a new place.
    const response = await page.goto(`${PORTAL}/signin`, { waitUntil: "domcontentloaded" });
    expect(response?.headers()["set-cookie"], "sign-in set a cookie").toBeUndefined();

    await expect(page.locator("input[type=email]")).toBeEnabled();
    await expect(page.locator("input[type=password]")).toBeEnabled();
    await expect(page.locator("button[type=submit]")).toBeVisible();

    const body = await page.locator("body").innerText();
    expect(body, "SSO is not built — offering it would be a dead control").not.toMatch(/Continue with SSO/i);
    expect(body, "there is no demo tenant to open").not.toMatch(/demo workspace/i);
  });

  test("safety operations never names a member", async ({ page }) => {
    // The portal's reason to exist. SAF-01/SAF-02 are about whether the safety
    // machinery works — not about who is in trouble.
    await page.goto(`${PORTAL}/safety`, { waitUntil: "domcontentloaded" });
    await expect(page.getByText(/No member appears on this page, ever/i)).toBeVisible();
    await page.goto(`${PORTAL}/safety/runbook`, { waitUntil: "domcontentloaded" });
    await expect(page.getByText(/never told that a member was flagged/i)).toBeVisible();
  });

  test("carries the same CSP floor as the other three apps", async ({ page }) => {
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
    expect(csp).toContain("'nonce-");
    expect(csp).not.toContain("'unsafe-eval'");
  });

  test("is not indexable", async ({ page }) => {
    await page.goto(PORTAL, { waitUntil: "domcontentloaded" });
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  });
});
