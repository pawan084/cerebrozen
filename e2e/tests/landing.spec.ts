import { test, expect } from "@playwright/test";

const WEB = process.env.WEB_URL || "http://web:3000";

test.describe("Landing site", () => {
  test("renders hero, features, and pricing", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /calmer mind/i })).toBeVisible();
    await expect(page.getByText("becomes agentic")).toBeVisible();
    await expect(page.getByText("Most popular")).toBeVisible();
    await expect(page.getByText("₹499")).toBeVisible();
    await expect(page.getByText(/wellness support, not emergency/i)).toBeVisible();
  });

  test("accepts a waitlist signup via the live API", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    const email = `e2e-${Date.now()}@test.app`;
    await page.getByPlaceholder("you@email.com").fill(email);
    await page.getByRole("button", { name: /join the waitlist/i }).click();
    await expect(page.locator(".wl-msg")).toContainText(/in|list/i);
  });
});
