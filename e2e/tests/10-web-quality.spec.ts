import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

/* Marketing-site quality gate — the "world-class landing" contract.
 *
 * Unlike the tenancy/privacy specs this owns no backend state; it pins the things a
 * prospect's browser (and a crawler, and a screen reader) actually sees on the public
 * site: structured data, share cards, one-h1 heading order, keyboard reachability, no
 * dead internal links. These regress silently — a renamed route or a dropped <h1> ships
 * green everywhere else — so they get their own gate. */

const web = urls.web;
const PAGES = ["/", "/platform", "/solutions", "/security", "/evidence", "/clients", "/about", "/contact"];

test.describe("web · SEO & head", () => {
  test("home emits Organization + WebSite JSON-LD", async ({ page }) => {
    await page.goto(web);
    const blocks = await page.locator('script[type="application/ld+json"]').allTextContents();
    const types = blocks.map((b) => JSON.parse(b)["@type"]);
    expect(types).toContain("Organization");
    expect(types).toContain("WebSite");
  });

  test("home has canonical, OG card, twitter card and theme-color", async ({ page }) => {
    await page.goto(web);
    await expect(page.locator('link[rel="canonical"]')).toHaveCount(1);
    const og = page.locator('meta[property="og:image"]').first();
    await expect(og).toHaveAttribute("content", /opengraph-image/);
    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute("content", "summary_large_image");
    expect(await page.locator('meta[name="theme-color"]').count()).toBeGreaterThan(0);
  });

  test("generated OG image and manifest serve correctly", async ({ request }) => {
    const og = await request.get(`${web}/opengraph-image`);
    expect(og.status()).toBe(200);
    expect(og.headers()["content-type"]).toContain("image/png");

    const mani = await request.get(`${web}/manifest.webmanifest`);
    expect(mani.status()).toBe(200);
    const json = await mani.json();
    expect(json.name).toBeTruthy();
    expect(Array.isArray(json.icons)).toBe(true);
  });

  test("an unknown path returns a branded 404, not a 200", async ({ page }) => {
    const res = await page.goto(`${web}/this-route-does-not-exist`);
    expect(res?.status()).toBe(404);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });
});

test.describe("web · accessibility", () => {
  for (const path of PAGES) {
    test(`${path} has exactly one <h1> and a skip link`, async ({ page }) => {
      await page.goto(`${web}${path}`);
      await expect(page.locator("h1")).toHaveCount(1);
      await expect(page.getByRole("link", { name: /skip to content/i })).toHaveCount(1);
    });

    test(`${path} — every image has an alt attribute`, async ({ page }) => {
      await page.goto(`${web}${path}`);
      const imgs = page.locator("img");
      const n = await imgs.count();
      for (let i = 0; i < n; i++) {
        // alt may be "" (decorative) but the attribute must be present.
        expect(await imgs.nth(i).getAttribute("alt"), `img #${i} on ${path} missing alt`).not.toBeNull();
      }
    });
  }

  test("the active nav link is marked aria-current", async ({ page }) => {
    await page.goto(`${web}/platform`);
    await expect(page.locator('a[aria-current="page"]')).toContainText(/platform/i);
  });

  test("the Sign-in menu opens to a focused item and roves with arrows", async ({ page }) => {
    await page.goto(web);
    await page.getByRole("button", { name: /sign in/i }).focus();
    await page.keyboard.press("Enter");
    await expect(page.locator('[role="menuitem"]:focus')).toHaveCount(1);
    await page.keyboard.press("ArrowDown");
    await expect(page.locator('[role="menuitem"]:focus')).toHaveCount(1);
  });
});

test.describe("web · integrity", () => {
  test("no internal link on the home page 404s", async ({ page, request }) => {
    await page.goto(web);
    const hrefs = await page.locator("a[href]").evaluateAll((els) =>
      els.map((e) => (e as HTMLAnchorElement).getAttribute("href") ?? ""),
    );
    const internal = [
      ...new Set(
        hrefs
          .filter((h) => h.startsWith("/") && !h.startsWith("//"))
          .map((h) => h.split("#")[0])
          .filter(Boolean),
      ),
    ];
    for (const path of internal) {
      const res = await request.get(`${web}${path}`);
      expect(res.status(), `internal link ${path} should not be broken`).toBeLessThan(400);
    }
  });

  test("reduced-motion: scroll-reveal content is visible, not hidden", async ({ browser }) => {
    const ctx = await browser.newContext({ reducedMotion: "reduce" });
    const page = await ctx.newPage();
    await page.goto(web);
    // The h1 lives inside a <Reveal>; under reduced motion it must be fully opaque.
    const opacity = await page.locator("h1").first().evaluate((el) => getComputedStyle(el).opacity);
    expect(Number(opacity)).toBe(1);
    await ctx.close();
  });
});
