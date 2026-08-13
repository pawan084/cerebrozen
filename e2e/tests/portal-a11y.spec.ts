import AxeBuilder from "@axe-core/playwright";
import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

const PORTAL = process.env.PORTAL_URL || "http://portal:3003";
const API = process.env.API_URL || "http://api:8000";

// The portal at a phone-sized viewport, with a keyboard, with reduced motion,
// and under an accessibility scanner (2026-08-13).
//
// All four were previously "verified" by reading the media queries. That is
// enough to know a rule exists and not enough to know what it does: the
// off-canvas drawer below 820px was still in the tab order while off screen,
// which reads to a keyboard or screen-reader user as a navigation menu that
// silently swallows focus.

const PHONE = { width: 390, height: 844 };
const PASSWORD = "password123";
const STAFF_EMAIL = process.env.ADMIN_EMAIL || "admin@cerebro.app";
const STAFF_PASSWORD = process.env.ADMIN_PASSWORD || "admin12345";
const unique = () => Math.random().toString(36).slice(2, 10);

async function provisionedOwner(request: APIRequestContext): Promise<string> {
  const email = `a11y-owner-${unique()}@test.app`;
  const signup = await request.post(`${API}/auth/signup`, {
    data: { email, password: PASSWORD, name: "A11y" },
  });
  expect(signup.status()).toBe(201);
  const staff = await request.post(`${API}/auth/login`, {
    form: { username: STAFF_EMAIL, password: STAFF_PASSWORD },
  });
  expect(staff.ok(), "seeded platform admin could not sign in").toBeTruthy();
  const provisioned = await request.post(`${API}/admin/organizations`, {
    headers: { Authorization: `Bearer ${(await staff.json()).access_token}` },
    data: { name: `A11y Co ${unique()}`, admin_email: email, seats_licensed: 40 },
  });
  expect(provisioned.status(), await provisioned.text()).toBe(201);
  return email;
}

async function signIn(page: Page, email: string) {
  await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
  await page.locator("input[type=email]").fill(email);
  await page.locator("input[type=password]").fill(PASSWORD);
  await page.locator("button[type=submit]").click();
  await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });
}

/** Serious and critical only — the bar this suite is willing to fail CI on. */
async function seriousViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  return results.violations
    .filter((v) => v.impact === "serious" || v.impact === "critical")
    .map((v) => `${v.id} (${v.impact}) × ${v.nodes.length}: ${v.help}`);
}

test.describe("Portal — narrow viewport, keyboard and contrast", () => {
  test("the off-canvas drawer is closed to the keyboard, not merely off screen", async ({
    page,
    request,
  }) => {
    const owner = await provisionedOwner(request);
    await page.setViewportSize(PHONE);
    await signIn(page, owner);
    await page.goto(`${PORTAL}/`, { waitUntil: "networkidle" });

    const nav = page.getByRole("navigation", { name: /Portal sections/i });
    // Two locators for the same link on purpose: one by CSS (which sees the
    // DOM) and one by role (which sees the ACCESSIBILITY TREE, and skips
    // anything hidden from it). The gap between them is the whole point of
    // this test.
    const linkInDom = page.locator('#portal-nav a[href="/members"]');
    const linkToAssistiveTech = nav.getByRole("link", { name: "Members & seats", exact: true });
    // Scoped by class, not by name: once open, the scrim is also labelled
    // "Close navigation" and a name-based lookup matches both.
    const toggle = page.locator("button.mobile-menu");
    const scrim = page.locator("button.backdrop");

    await expect(toggle).toBeVisible();
    // The drawer is rendered...
    await expect(linkInDom).toHaveCount(1);
    // ...and yet reaches neither the eye nor the keyboard nor a screen reader.
    // A translate alone satisfied none of these: the link stayed visible to
    // Playwright, focusable by Tab, and present in the accessibility tree.
    await expect(linkInDom).not.toBeVisible();
    await expect(linkToAssistiveTech).toHaveCount(0);

    // Opened by the toggle, which reports its own state and renames itself.
    await expect(toggle).toHaveAccessibleName("Open navigation");
    await toggle.click();
    await expect(toggle).toHaveAccessibleName("Close navigation");
    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    await expect(linkToAssistiveTech).toBeVisible();
    // Polled, because the drawer slides in over 0.25s and a box measured on
    // the first frame is still off screen (it was -282 here). What is being
    // asserted is where it COMES TO REST — a drawer that opens outside the
    // viewport is the failure this is looking for.
    await expect
      .poll(async () => (await linkToAssistiveTech.boundingBox())?.x ?? -1, { timeout: 5_000 })
      .toBeGreaterThanOrEqual(0);

    // The scrim closes it, and the drawer leaves the tab order again. Clicked
    // near the right edge rather than at its centre: the scrim covers the whole
    // viewport, but the drawer is 284px of a 390px phone, so the centre point
    // lands ON the drawer and the click is intercepted. The exposed strip is
    // where a person's thumb would actually go.
    await scrim.click({ position: { x: 350, y: 500 } });
    await expect(linkInDom).not.toBeVisible();
    await expect(linkToAssistiveTech).toHaveCount(0);
    await expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  test("no portal screen scrolls sideways on a phone", async ({ page, request }) => {
    const owner = await provisionedOwner(request);
    await page.setViewportSize(PHONE);
    await signIn(page, owner);

    // One of each layout: metric grid, wide table, and a form.
    for (const path of ["/", "/members", "/members/invite", "/privacy"]) {
      await page.goto(`${PORTAL}${path}`, { waitUntil: "networkidle" });
      const overflow = await page.evaluate(() => {
        const el = document.documentElement;
        return { scroll: el.scrollWidth, client: el.clientWidth };
      });
      // A wide table is allowed to scroll INSIDE its own `.table-wrap`; the
      // page itself must not.
      expect(overflow.scroll, `${path} scrolls sideways`).toBeLessThanOrEqual(overflow.client + 1);
    }
  });

  test("reduced motion is honoured, not just declared", async ({ browser, request }) => {
    const owner = await provisionedOwner(request);
    const context = await browser.newContext({ reducedMotion: "reduce", viewport: PHONE });
    const page = await context.newPage();
    await signIn(page, owner);
    await page.goto(`${PORTAL}/`, { waitUntil: "networkidle" });

    const durations = await page.evaluate(() => {
      const sidebar = document.querySelector(".sidebar")!;
      const navButton = document.querySelector(".nav-btn")!;
      return {
        sidebar: getComputedStyle(sidebar).transitionDuration,
        button: getComputedStyle(navButton).transitionDuration,
      };
    });
    expect(durations.sidebar).toMatch(/^0s(,\s*0s)*$/);
    expect(durations.button).toMatch(/^0s(,\s*0s)*$/);
    await context.close();
  });

  test("axe finds nothing serious on the sign-in page or the dashboard", async ({
    page,
    request,
  }) => {
    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    expect(await seriousViolations(page), "sign-in").toEqual([]);

    const owner = await provisionedOwner(request);
    await signIn(page, owner);
    await page.goto(`${PORTAL}/`, { waitUntil: "networkidle" });
    await expect(page.getByText(/Live data from your organisation/i)).toBeVisible({
      timeout: 20_000,
    });
    expect(await seriousViolations(page), "dashboard").toEqual([]);

    // And the same page at a phone width, where the layout is a different one.
    await page.setViewportSize(PHONE);
    await page.goto(`${PORTAL}/members`, { waitUntil: "networkidle" });
    expect(await seriousViolations(page), "members on a phone").toEqual([]);
  });
});
