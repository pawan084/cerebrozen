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
