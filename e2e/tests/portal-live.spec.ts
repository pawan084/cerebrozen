import { test, expect, type APIRequestContext } from "@playwright/test";

const PORTAL = process.env.PORTAL_URL || "http://portal:3003";
const API = process.env.API_URL || "http://api:8000";

// The portal talking to the real backend (2026-08-12).
//
// portal.spec.ts proves the 36 screens render. This proves the four that are
// WIRED show a real organisation's numbers — which curl cannot, because the
// portal loads its data client-side.
//
// The test provisions its own organisation through POST /admin/organizations,
// so it needs no fixture and no hand-seeded row. That endpoint exists partly
// because of this: before it, the first organisation had to be written by hand
// in psql, which is neither an onboarding path nor something a test can do.

const PASSWORD = "password123";
// The seeded platform admin, from the same env the api seeded with. Hardcoding
// "admin12345" worked only where backend/.env did not override it — and got a
// real account locked out here after five wrong attempts.
const STAFF_EMAIL = process.env.ADMIN_EMAIL || "admin@cerebro.app";
const STAFF_PASSWORD = process.env.ADMIN_PASSWORD || "admin12345";
const unique = () => Math.random().toString(36).slice(2, 10);

async function signUp(request: APIRequestContext, email: string) {
  const res = await request.post(`${API}/auth/signup`, {
    data: { email, password: PASSWORD, name: "Portal test" },
  });
  expect(res.status(), `signup for ${email}`).toBe(201);
  return (await res.json()).access_token as string;
}

async function login(request: APIRequestContext, email: string, password = PASSWORD) {
  const res = await request.post(`${API}/auth/login`, {
    form: { username: email, password },
  });
  expect(res.ok(), `login for ${email}`).toBeTruthy();
  return (await res.json()).access_token as string;
}

test.describe("Portal ↔ backend", () => {
  test("an administrator sees their own organisation's real totals", async ({ page, request }) => {
    const suffix = unique();
    const ownerEmail = `portal-owner-${suffix}@test.app`;
    const orgName = `E2E Health ${suffix}`;

    // 1. The future administrator has an ordinary CereBro account.
    await signUp(request, ownerEmail);

    // 2. CereBro staff provision the organisation. The seeded platform admin is
    //    the same one admin.spec.ts uses.
    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    const provisioned = await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: orgName, admin_email: ownerEmail, seats_licensed: 500 },
    });
    expect(provisioned.status(), await provisioned.text()).toBe(201);

    // 3. The owner adds a small cohort — deliberately under the threshold of 20,
    //    because the suppressed path is the one that matters.
    const ownerToken = await login(request, ownerEmail);
    const auth = { Authorization: `Bearer ${ownerToken}` };
    const group = await request.post(`${API}/org/groups`, {
      headers: auth,
      data: { name: "Caregiver benefit", rule: "Registered caregivers" },
    });
    expect(group.status()).toBe(201);
    const groupId = (await group.json()).id;

    for (let i = 0; i < 2; i++) {
      const memberEmail = `portal-member-${suffix}-${i}@test.app`;
      await signUp(request, memberEmail);
      const added = await request.post(`${API}/org/members`, {
        headers: auth,
        data: { email: memberEmail, group_id: groupId, external_ref: `EMP-${i}` },
      });
      expect(added.status(), await added.text()).toBe(201);
    }

    // 4. Sign in through the portal UI, as an administrator would.
    await page.goto(`${PORTAL}/`, { waitUntil: "networkidle" });
    expect(page.url(), "a signed-out visitor should land on sign-in").toContain("/signin");

    await page.locator("input[type=email]").fill(ownerEmail);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    // 5. The dashboard is live, and it is THIS organisation.
    await expect(page.getByText(/Live data from your organisation/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(orgName)).toBeVisible();
    await expect(page.getByText(/Sample data/i)).toHaveCount(0);

    // 6. Cohorts: two members is under the threshold, so no participation figure
    //    is reported — and it must read as withheld, never as zero.
    await page.goto(`${PORTAL}/cohorts`, { waitUntil: "networkidle" });
    await expect(page.getByText(/Live data from your organisation/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Caregiver benefit")).toBeVisible();
    await expect(page.getByText(/Too small to report/i)).toBeVisible();
    const cohortText = await page.locator("body").innerText();
    expect(cohortText, "a suppressed cohort must not render as 0 activated").not.toMatch(
      /Caregiver benefit[\s\S]{0,160}\b0 activated/,
    );

    // 7. Seats are listed by the organisation's own reference, with no identity.
    await page.goto(`${PORTAL}/members`, { waitUntil: "networkidle" });
    await expect(page.getByText("EMP-0")).toBeVisible({ timeout: 20_000 });
    const membersText = await page.locator("body").innerText();
    expect(membersText, "the seat list must not expose a member's email").not.toContain("portal-member-");
  });

  test("an ordinary account is told it administers no organisation", async ({ page, request }) => {
    // The 403 path. It is correct, and worth explaining rather than showing as
    // a failed load or bouncing the user back to sign-in.
    const email = `portal-nobody-${unique()}@test.app`;
    await signUp(request, email);

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(email);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();

    await expect(page.getByText(/does not administer an organisation/i)).toBeVisible({
      timeout: 20_000,
    });
  });

  test("clearing the session locks the portal again", async ({ page, request }) => {
    const email = `portal-lock-${unique()}@test.app`;
    await signUp(request, email);

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(email);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    await page.evaluate(() => window.localStorage.removeItem("cerebro_portal_refresh"));
    await page.goto(`${PORTAL}/members`, { waitUntil: "networkidle" });
    expect(page.url()).toContain("/signin");
  });

  test("a cohort created in the portal exists in the backend", async ({ page, request }) => {
    // The write path, end to end: the form posts, the server stores, and the
    // API confirms it independently of what the UI decided to show.
    const suffix = unique();
    const ownerEmail = `portal-writer-${suffix}@test.app`;
    await signUp(request, ownerEmail);
    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    const provisioned = await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: `Writer Co ${suffix}`, admin_email: ownerEmail },
    });
    expect(provisioned.status(), await provisioned.text()).toBe(201);

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(ownerEmail);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    const cohortName = `Night shift ${suffix}`;
    await page.goto(`${PORTAL}/cohorts/new`, { waitUntil: "networkidle" });
    await page.getByLabel("Cohort name").fill(cohortName);
    await page.getByLabel("Eligibility rule").fill("Works 22:00-06:00");
    await page.getByRole("button", { name: /Create cohort/i }).click();
    await expect(page.getByText(/created\./i)).toBeVisible({ timeout: 20_000 });

    // Confirm from the API, not from the page that just claimed success.
    const ownerToken = await login(request, ownerEmail);
    const groups = await request.get(`${API}/org/groups`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
    });
    const names = (await groups.json()).map((g: { name: string }) => g.name);
    expect(names, "the cohort the portal said it created is not in the backend").toContain(cohortName);
  });

  test("a threshold below the floor is clamped, and the portal shows what was stored", async ({
    page,
    request,
  }) => {
    // The server raises anything under 20 to 20. The screen must re-render from
    // the RESPONSE, not from the button that was clicked — otherwise it would
    // display a privacy control that is not in force.
    const suffix = unique();
    const ownerEmail = `portal-thresh-${suffix}@test.app`;
    await signUp(request, ownerEmail);
    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: `Thresh Co ${suffix}`, admin_email: ownerEmail },
    });

    const ownerToken = await login(request, ownerEmail);
    // Ask for 5 through the API directly — the UI only offers legal choices.
    const patched = await request.patch(`${API}/org`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { reporting_threshold: 5 },
    });
    expect(patched.ok()).toBeTruthy();
    expect((await patched.json()).reporting_threshold, "threshold was not clamped").toBe(20);

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(ownerEmail);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    await page.goto(`${PORTAL}/privacy`, { waitUntil: "networkidle" });
    await expect(page.getByRole("button", { name: "20", exact: true })).toHaveAttribute(
      "aria-pressed",
      "true",
      { timeout: 20_000 },
    );
  });
});
