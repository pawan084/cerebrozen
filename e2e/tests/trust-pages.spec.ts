import { test, expect } from "@playwright/test";

const WEB = process.env.WEB_URL || "http://web:3000";
const APP = process.env.APP_URL || "http://app:3002";

// The trust surface added 2026-08-03: security, refunds, subprocessors, and
// the machine-readable disclosure contact. These pages are the public promises
// the product is held to — a 404 here is a broken promise, so CI walks them.
test.describe("Trust pages", () => {
  test("security page states the floor and the reporting channel", async ({ page }) => {
    await page.goto(`${WEB}/security`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Security", exact: true })).toBeVisible();
    await expect(page.getByText(/salted hashes/i)).toBeVisible();
    await expect(page.getByText(/support@cerebrozen\.in/i).first()).toBeVisible();
  });

  test("security.txt is served and machine-readable", async ({ request }) => {
    const res = await request.get(`${WEB}/.well-known/security.txt`);
    expect(res.ok()).toBeTruthy();
    const body = await res.text();
    expect(body).toContain("Contact: mailto:support@cerebrozen.in");
    expect(body).toContain("Policy:");
  });

  test("refunds page promises no dark patterns", async ({ page }) => {
    await page.goto(`${WEB}/refunds`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /cancellations & refunds/i })).toBeVisible();
    await expect(page.getByText(/cancel anytime/i)).toBeVisible();
  });

  test("subprocessors page lists the AI providers the privacy policy names", async ({ page }) => {
    await page.goto(`${WEB}/subprocessors`, { waitUntil: "networkidle" });
    await expect(page.getByText(/OpenAI \/ Anthropic/)).toBeVisible();
    await expect(page.getByText("Deepgram")).toBeVisible();
    await expect(page.getByText("ElevenLabs")).toBeVisible();
  });

  // Play Console requires a reachable URL where someone can request account and
  // data deletion WITHOUT installing the app (App content -> Data safety), and
  // this page IS the URL pasted into the listing. It was in the sitemap and the
  // footer and reached by no test at all, which is the worst combination: a
  // store-listing dependency that could 404 or lose its email route silently.
  test("the off-app deletion route still offers both ways out", async ({ page }) => {
    await page.goto(`${WEB}/delete-account`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Delete your account" })).toBeVisible();

    // Option 1 names the in-app path. If the app ever moves that screen this
    // sentence goes stale, and someone following it gives up.
    await expect(page.getByText(/Privacy\s*&\s*data/i).first()).toBeVisible();

    // Option 2 is the half that has to work for someone who has already
    // uninstalled — the exact person Play has in mind. A mailto that loses its
    // address is a dead end with no error message.
    const mail = page.locator('a[href^="mailto:privacy@cerebrozen.in"]').first();
    await expect(mail).toBeVisible();
    await expect(mail).toHaveAttribute("href", /subject=/);

    // Both halves of the honesty: what goes, and what stays. A deletion page
    // that only promised the first half would be the kind of claim CLAIMS_MAP
    // exists to stop.
    await expect(page.getByRole("heading", { name: /What gets deleted/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /What does not get deleted/i })).toBeVisible();
    await expect(page.getByText(/30 days/i).first()).toBeVisible();
  });

  test("terms lead with wellness, not medical care", async ({ page }) => {
    // Clause 1 is the one the App Store review and every safety claim rest on.
    // "Companion, never clinician" is the product's whole position; the terms
    // are where it is legally stated, so a rewrite that softened it should
    // break something.
    await page.goto(`${WEB}/terms`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Terms of Use" })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Wellness, not medical care/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /AI limitations/i })).toBeVisible();
    await expect(page.getByRole("link", { name: "refunds policy" }).or(
      page.locator('a[href="/refunds"]')).first()).toBeVisible();
  });

  test("footer links every trust page from the landing", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    for (const [name, path] of [
      ["Security", "/security"],
      ["Terms", "/terms"],
      ["Delete your account", "/delete-account"],
      ["Refunds", "/refunds"],
      ["Subprocessors", "/subprocessors"],
      ["Safety centre", "/safety"],
      ["Accessibility", "/accessibility"],
      ["Sponsored access", "/organizations"],
    ] as const) {
      await expect(page.getByRole("link", { name, exact: true })).toHaveAttribute("href", path);
    }
  });
});

// The three pages ref/landing.html carries that this site did not: safety,
// accessibility and the B2B2C boundary. Each states something the product is
// held to, and each states what is NOT true yet — CI walks both halves, because
// the honest half is the half that quietly rots first.
test.describe("Pages from the ref/ prototype", () => {
  test("safety centre leads with human help and names the limits", async ({ page }) => {
    await page.goto(`${WEB}/safety`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /human help comes first/i })).toBeVisible();
    // The emergency pathway must be on the page, not a click away.
    await expect(page.getByText(/112/).first()).toBeVisible();
    await expect(page.getByText(/14416/).first()).toBeVisible();
    // …and the honesty about what is not in place.
    await expect(page.getByRole("heading", { name: /what is not in place yet/i })).toBeVisible();
  });

  test("accessibility page claims no conformance it has not earned", async ({ page }) => {
    await page.goto(`${WEB}/accessibility`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /a calm interface must also be usable/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /not yet validated/i })).toBeVisible();
    await expect(page.getByText(/do not claim conformance/i)).toBeVisible();
  });

  test("organizations page states the boundary and that it is not yet buyable", async ({ page }) => {
    await page.goto(`${WEB}/organizations`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /fund wellness access/i })).toBeVisible();
    await expect(page.getByText(/not yet available/i)).toBeVisible();
    // The never-shared list is the page's reason to exist.
    await expect(page.getByRole("heading", { name: /must never receive/i })).toBeVisible();
    await expect(page.getByText(/journal entries and personal notes/i)).toBeVisible();
  });
});

// Account creation outside the funnel now carries the 18+ attest the funnel
// has always had — an account must not be creatable without it.
test.describe("Direct signup age gate", () => {
  test("create-account outside the funnel requires the 18+ attest", async ({ page }) => {
    await page.goto(`${APP}/signin`, { waitUntil: "networkidle" });
    await page.getByRole("tab", { name: "Create account" }).click();
    const attest = page.getByText(/I'm 18 or older/);
    await expect(attest).toBeVisible();

    // Filling everything but the checkbox must not create an account — the
    // required checkbox holds the form (browser-native constraint validation).
    await page.getByLabel("Name").fill("E2E Minor Gate");
    await page.getByLabel("Email").fill(`e2e-age-${Date.now()}@test.app`);
    await page.getByLabel("Password").fill("longenoughpass");
    await page.getByRole("button", { name: "Create my account" }).click();
    await expect(page).toHaveURL(/\/signin/);
  });
});

// The web app is installable: manifest present and consistent.
test("web app serves an installable manifest", async ({ request }) => {
  const res = await request.get(`${APP}/manifest.webmanifest`);
  expect(res.ok()).toBeTruthy();
  const manifest = await res.json();
  expect(manifest.name).toContain("CereBro");
  expect(manifest.display).toBe("standalone");
  expect(manifest.icons.length).toBeGreaterThanOrEqual(2);
});
