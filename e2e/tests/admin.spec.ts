import { test, expect, Page } from "@playwright/test";

const ADMIN = process.env.ADMIN_URL || "http://admin:3001";

async function login(page: Page) {
  await page.goto(ADMIN, { waitUntil: "networkidle" });
  // Type the seeded creds explicitly — production builds no longer pre-fill them.
  await page.locator('input[type="email"]').fill("admin@cerebro.app");
  await page.locator('input[type="password"]').fill("admin12345");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible({ timeout: 20_000 });
}

function nav(page: Page, label: string) {
  return page.locator(".navitem", { hasText: label });
}

test.describe("Admin dashboard", () => {
  test.beforeEach(async ({ page }) => login(page));

  test("overview shows live stat cards", async ({ page }) => {
    await expect(page.locator(".stat")).toHaveCount(5);
    await expect(page.locator(".stat .n").first()).toBeVisible();
  });

  test("users tab lists the seeded admin account", async ({ page }) => {
    await nav(page, "Users").click();
    await expect(page.getByText("admin@cerebro.app")).toBeVisible();
  });

  test("analytics tab shows first-party aggregates", async ({ page }) => {
    await nav(page, "Analytics").click();
    await expect(page.getByText("Active today")).toBeVisible();
    await expect(page.getByRole("heading", { name: /Activation funnel/ })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Retention/ })).toBeVisible();
  });

  test("user details show counts, never content", async ({ page }) => {
    await nav(page, "Users").click();
    await page
      .locator("tr", { hasText: "admin@cerebro.app" })
      .getByRole("button", { name: "Details" })
      .click();
    await expect(page.getByRole("heading", { name: "Account details" })).toBeVisible();
    await expect(page.getByText(/moods ·/)).toBeVisible();
    await expect(page.getByText(/contents never leave/)).toBeVisible();
  });

  test("content can be created and deleted", async ({ page }) => {
    await nav(page, "Content").click();
    await page.getByRole("button", { name: /new item/i }).click();
    const title = `E2E item ${Date.now()}`;
    await page.locator(".cform input[type=text]").first().fill(title);
    await page.getByRole("button", { name: /create item/i }).click();

    const row = page.locator("tr", { hasText: title });
    await expect(row).toBeVisible();

    await row.getByRole("button", { name: "Delete" }).click();
    await expect(page.locator("tr", { hasText: title })).toHaveCount(0);
  });

  test("nudges can be authored for all active users", async ({ page }) => {
    await nav(page, "Nudges").click();
    const title = `E2E announcement ${Date.now()}`;
    await page.locator(".cform input").first().fill(title);
    await page.locator(".cform input").nth(1).fill("Something gentle is new.");
    await page.getByRole("button", { name: /queue for all active users/i }).click();
    await expect(page.getByText(/Queued for \d+ user/)).toBeVisible();
    await expect(page.locator("tr", { hasText: title }).first()).toBeVisible();
  });

  test("safety review queue renders flagged events", async ({ page }) => {
    await nav(page, "Safety").click();
    await expect(page.getByRole("heading", { name: "Safety review" })).toBeVisible();
  });

  test("waitlist tab renders", async ({ page }) => {
    await nav(page, "Waitlist").click();
    await expect(page.getByText(/signups from the landing/i)).toBeVisible();
  });
});
