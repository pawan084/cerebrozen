import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

/* The surfaces link to each other correctly (ARCHITECTURE.md §"How the surfaces link").
 *
 * Worth an e2e spec specifically because these hosts are NEXT_PUBLIC_*, i.e. inlined at
 * BUILD time from Docker build args. Nothing at runtime can catch a wrong one, and no unit
 * test can either: the bug is a correct-looking page pointing at the wrong deployment. The
 * only way to know the site's "Sign in" opens THIS stack's app is to build the stack and
 * click it — which is exactly what this does.
 */

test.describe("surface links", () => {
  test("the site's Sign in menu offers both destinations", async ({ page }) => {
    await page.goto(urls.web, { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Sign in" }).click();
    const items = page.getByRole("menuitem");
    await expect(items).toHaveCount(2);
    await expect(items.filter({ hasText: "Employee" })).toBeVisible();
    await expect(items.filter({ hasText: "HR / admin" })).toBeVisible();
  });

  test("Sign in points at THIS stack, not at production", async ({ page }) => {
    // The failure this catches: the ARG defaults are the prod domains, so a stack built
    // without the build args silently walks a visitor off to the live site. That is
    // invisible in the markup and invisible at runtime.
    await page.goto(urls.web, { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Sign in" }).click();
    const hrefs = await page.getByRole("menuitem").evaluateAll((els) =>
      els.map((e) => e.getAttribute("href") ?? ""),
    );
    expect(hrefs).toContain(urls.app);
    expect(hrefs).toContain(urls.admin);
    for (const h of hrefs) {
      expect(h, `Sign in leads to production from a test stack: ${h}`).not.toContain("cerebrozen.in");
    }
  });

  test("the menu shows the host it will actually open", async ({ page }) => {
    // The menu prints the host so a misconfigured build is visible on the page rather
    // than only discovered on the click.
    await page.goto(urls.web, { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Sign in" }).click();
    const employee = page.getByRole("menuitem").filter({ hasText: "Employee" });
    await expect(employee).toContainText(new URL(urls.app).host);
  });

  test("employee sign-in reaches the app's login", async ({ page }) => {
    await page.goto(urls.web, { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.getByRole("menuitem").filter({ hasText: "Employee" }).click();
    await page.waitForLoadState("domcontentloaded");
    expect(page.url()).toContain(new URL(urls.app).host);
    await expect(page.locator('input[name="password"]')).toBeVisible({ timeout: 30_000 });
  });

  test("HR sign-in reaches the admin's login", async ({ page }) => {
    await page.goto(urls.web, { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.getByRole("menuitem").filter({ hasText: "HR / admin" }).click();
    await page.waitForLoadState("domcontentloaded");
    expect(page.url()).toContain(new URL(urls.admin).host);
    await expect(page.locator('input[name="password"]')).toBeVisible({ timeout: 30_000 });
  });

  test("both apps link back to the site rather than forking Privacy and Terms", async ({ page }) => {
    // One set of published terms, one place to change them.
    for (const base of [urls.app, urls.admin]) {
      await page.goto(base, { waitUntil: "domcontentloaded" });
      const foot = page.locator(".login-foot a");
      await expect(foot).toHaveCount(3);
      await expect(foot.nth(0)).toHaveAttribute("href", urls.web);
      await expect(foot.nth(1)).toHaveAttribute("href", `${urls.web}/privacy`);
      await expect(foot.nth(2)).toHaveAttribute("href", `${urls.web}/terms`);
      await expect(page.locator("a.wordmark.home")).toHaveAttribute("href", urls.web);
    }
  });

  test("the pages those footers point at actually exist", async ({ request }) => {
    // A 404'd Privacy link on a login screen is worse than no link.
    for (const path of ["/privacy", "/terms"]) {
      const r = await request.get(`${urls.web}${path}`);
      expect(r.status(), `${path} is broken`).toBe(200);
    }
  });
});


/* Security headers on all three web surfaces.
 *
 * Lives in e2e because a CSP is only real as a RESPONSE HEADER on a running server — a
 * unit test can assert the config object and still ship a site that sends nothing, which
 * is exactly what the marketing site did while hosting a /security page.
 */
test.describe("security headers", () => {
  const surfaces = [
    { name: "web", url: () => urls.web },
    { name: "admin", url: () => urls.admin },
    { name: "app", url: () => urls.app },
  ];

  for (const s of surfaces) {
    test(`${s.name} sends a CSP and the baseline headers`, async ({ request }) => {
      const h = (await request.get(s.url())).headers();
      const csp = h["content-security-policy"];
      expect(csp, `${s.name} sends no CSP at all`).toBeTruthy();
      // Every surface, no exceptions: these are the cheap ones and there is no reason to
      // differ on them.
      expect(csp).toContain("frame-ancestors 'none'");
      expect(csp).toContain("object-src 'none'");
      expect(csp).toContain("base-uri 'self'");
      expect(h["x-frame-options"]).toBe("DENY");
      expect(h["x-content-type-options"]).toBe("nosniff");
      expect(h["referrer-policy"]).toBeTruthy();
      expect(h["permissions-policy"]).toBeTruthy();
    });
  }

  for (const s of [surfaces[1], surfaces[2]]) {
    test(`${s.name} uses a per-request nonce, not unsafe-inline`, async ({ request }) => {
      // The two authenticated tools hold something worth stealing, so script-src is strict.
      const csp = (await request.get(s.url())).headers()["content-security-policy"];
      expect(csp, "script-src must not allow inline").not.toMatch(/script-src[^;]*'unsafe-inline'/);
      expect(csp, "no nonce → the policy is decorative").toMatch(/script-src[^;]*'nonce-[^']+'/);
    });
  }

  test("the app's CSP names the API and engine it actually calls", async ({ request }) => {
    // A browser client that cannot reach its own backend is a dead app; stock
    // default-src 'self' would do exactly that.
    const csp = (await request.get(urls.app)).headers()["content-security-policy"];
    expect(csp).toContain(new URL(urls.platform).origin);
    expect(csp).toContain(new URL(urls.engine).origin);
  });

  test("the marketing site stays static and exposes no API origin", async ({ request }) => {
    /* Deliberately different from the other two: a nonce would force every page dynamic,
       and this site's job is to be fast and indexable. It has no auth, no tokens and no
       user content rendered back, so 'unsafe-inline' costs little here — while the pages
       that carry our security claims still get the headers a CISO checks. */
    const csp = (await request.get(urls.web)).headers()["content-security-policy"];
    expect(csp, "the marketing site should not be paying for a nonce").not.toMatch(/'nonce-/);
    expect(csp).toContain("connect-src 'self'");
    // No API origin should be reachable from the browser at all.
    expect(csp).not.toContain(new URL(urls.platform).origin);
  });
});
