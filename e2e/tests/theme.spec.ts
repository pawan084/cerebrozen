import { test, expect, Page } from "@playwright/test";

const APP = process.env.APP_URL || "http://app:3002";

// Wave E (WEB_PARITY item 17): Dawn/Night dual theme.
// - System mode follows prefers-color-scheme (light → Dawn vars).
// - The /account picker pins Night or Dawn via data-theme + localStorage,
//   and the nonce'd pre-paint script must re-apply it after reload under
//   the enforced production CSP.
// - Sleep + signed-out surfaces stay Night in every mode.
// Screenshots land in /app/shots for a human visual pass (docker cp).

async function signup(page: Page) {
  const email = `e2e-theme-${Date.now()}@test.app`;
  await page.goto(`${APP}/signin`, { waitUntil: "networkidle" });
  await page.getByRole("tab", { name: "Create account" }).click();
  await page.locator('input[autocomplete="name"]').fill("Theme");
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill("password123");
  await page.getByRole("button", { name: "Create my account" }).click();
  await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }))
    .toBeVisible({ timeout: 20_000 });
  // Dismiss the first-run tour so screenshots show the dashboard.
  await page.getByRole("button", { name: "Skip" }).click().catch(() => {});
}

const bodyBg = (page: Page) =>
  page.evaluate(() => getComputedStyle(document.body).backgroundImage + getComputedStyle(document.body).backgroundColor);

// Perceived lightness of the --night var — Dawn resolves near-white.
const nightVar = (page: Page) =>
  page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue("--night").trim());

test.describe("Dawn theme (System + light OS)", () => {
  test.use({ colorScheme: "light" });

  test("system-light renders Dawn; Sleep and signed-out stay Night; picker + reload persist", async ({ page }) => {
    test.setTimeout(120_000);

    // Signed-out surfaces stay Night even on a light OS.
    await page.goto(`${APP}/crisis`, { waitUntil: "networkidle" });
    await expect(page.locator(".authwrap")).toHaveClass(/theme-night/);
    await page.screenshot({ path: "shots/crisis-night-pinned.png", fullPage: true });

    await signup(page);

    // Dawn vars active at :root under a light OS with no explicit choice.
    expect(await nightVar(page)).toBe("#fafafc");
    await page.screenshot({ path: "shots/home-dawn.png", fullPage: true });

    await page.goto(`${APP}/insights`, { waitUntil: "networkidle" });
    await page.screenshot({ path: "shots/insights-dawn.png", fullPage: true });

    // Sleep pins Night: the wrapper re-scopes --night to the dark value.
    await page.goto(`${APP}/sleep`, { waitUntil: "networkidle" });
    const sleepNight = await page.evaluate(() =>
      getComputedStyle(document.querySelector(".theme-night")!).getPropertyValue("--night").trim());
    expect(sleepNight).toBe("#0e0c22");
    await page.screenshot({ path: "shots/sleep-night-pinned.png", fullPage: true });

    // Picker: pin Night, survive a reload (pre-paint script under real CSP).
    await page.goto(`${APP}/account`, { waitUntil: "networkidle" });
    await page.screenshot({ path: "shots/account-dawn.png", fullPage: true });
    await page.getByRole("button", { name: "Night", exact: true }).click();
    expect(await nightVar(page)).toBe("#0e0c22");
    await page.reload({ waitUntil: "networkidle" });
    expect(await page.evaluate(() => document.documentElement.dataset.theme)).toBe("night");
    expect(await nightVar(page)).toBe("#0e0c22");
    await page.screenshot({ path: "shots/account-pinned-night.png", fullPage: true });

    // Back to System — Dawn again on this light OS.
    await page.getByRole("button", { name: "System", exact: true }).click();
    expect(await nightVar(page)).toBe("#fafafc");
  });
});

test.describe("Night theme (System + dark OS)", () => {
  test.use({ colorScheme: "dark" });

  test("system-dark keeps the original Night palette", async ({ page }) => {
    test.setTimeout(90_000);
    await signup(page);
    expect(await nightVar(page)).toBe("#0e0c22");
    await page.screenshot({ path: "shots/home-night.png", fullPage: true });
  });
});
