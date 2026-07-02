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

  test("safety review queue renders flagged events", async ({ page }) => {
    await nav(page, "Safety").click();
    await expect(page.getByRole("heading", { name: "Safety review" })).toBeVisible();
  });

  test("waitlist tab renders", async ({ page }) => {
    await nav(page, "Waitlist").click();
    await expect(page.getByText(/signups from the landing/i)).toBeVisible();
  });
});
