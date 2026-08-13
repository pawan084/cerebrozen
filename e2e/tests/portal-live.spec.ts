import { test, expect, type APIRequestContext } from "@playwright/test";

const PORTAL = process.env.PORTAL_URL || "http://portal:3003";
const API = process.env.API_URL || "http://api:8000";
// The member's own client — the far end of a sponsored seat.
const APP = process.env.APP_URL || "http://app:3002";

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

  test("the launch checklist ticks itself when the thing it describes exists", async ({
    page,
    request,
  }) => {
    // Derived, not stored. Six booleans in a table can say "eligibility
    // connected" while the organisation has no seats; this asks the question
    // each time, so nobody can tick a box by editing a row.
    const suffix = unique();
    const ownerEmail = `portal-setup-${suffix}@test.app`;
    await signUp(request, ownerEmail);
    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: `Setup Co ${suffix}`, admin_email: ownerEmail },
    });

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(ownerEmail);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    // A brand-new organisation has no groups, so that step is outstanding.
    // Counting completed steps rather than parsing prose: the phrase appears
    // both as a step and in the "what remains" summary, and a text assertion
    // that has to disambiguate them is fragile in a way this is not.
    await page.goto(`${PORTAL}/setup`, { waitUntil: "networkidle" });
    await expect(page.getByText(/Requirements complete/i).first()).toBeVisible({ timeout: 20_000 });
    const doneBefore = await page.locator(".step.done").count();
    expect(await page.locator("body").innerText()).toMatch(/remains? incomplete/i);

    // Create a group through the API, then reload: the step must tick on its
    // own, because the checklist is derived and not stored.
    const ownerToken = await login(request, ownerEmail);
    const created = await request.post(`${API}/org/groups`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name: `Everyone ${suffix}` },
    });
    expect(created.status()).toBe(201);

    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByText(/Requirements complete/i).first()).toBeVisible({ timeout: 20_000 });
    const doneAfter = await page.locator(".step.done").count();
    expect(doneAfter, "the groups step did not tick after a group was created").toBe(doneBefore + 1);
  });

  test("the audit log shows the administrator's own actions and nobody else's", async ({
    page,
    request,
  }) => {
    // AUD-01 promised "trace every administrative action" while nothing recorded
    // what an org administrator did. This walks the promise: act, then read the
    // trail back through the UI.
    const suffix = unique();
    const ownerEmail = `portal-audit-${suffix}@test.app`;
    await signUp(request, ownerEmail);
    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: `Audit Co ${suffix}`, admin_email: ownerEmail },
    });

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(ownerEmail);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    // Nothing has happened yet.
    await page.goto(`${PORTAL}/audit`, { waitUntil: "networkidle" });
    await expect(page.getByText(/Nothing recorded yet/i)).toBeVisible({ timeout: 20_000 });

    // Do something administrative through the portal itself.
    await page.goto(`${PORTAL}/cohorts/new`, { waitUntil: "networkidle" });
    await page.getByLabel("Cohort name").fill(`Audited group ${suffix}`);
    await page.getByRole("button", { name: /Create cohort/i }).click();
    await expect(page.getByText(/created\./i)).toBeVisible({ timeout: 20_000 });

    await page.goto(`${PORTAL}/audit`, { waitUntil: "networkidle" });
    await expect(page.getByText(/Created an eligibility group/i)).toBeVisible({ timeout: 20_000 });
    const body = await page.locator("body").innerText();
    // Attributed to the administrator who acted...
    expect(body).toContain(ownerEmail);
    // ...and the platform's own provisioning action is not in a customer's trail.
    expect(body, "a CereBro staff action leaked into an organisation's trail").not.toMatch(
      /organization\.provision/i,
    );
  });

  test("an eligibility file with a health column is refused without being uploaded", async ({
    page,
    request,
  }) => {
    const suffix = unique();
    const ownerEmail = `import-owner-${suffix}@test.app`;
    const memberEmail = `import-member-${suffix}@test.app`;
    await signUp(request, ownerEmail);
    await signUp(request, memberEmail);

    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: `Import Co ${suffix}`, admin_email: ownerEmail, seats_licensed: 100 },
    });

    await page.goto(`${PORTAL}/signin`, { waitUntil: "networkidle" });
    await page.locator("input[type=email]").fill(ownerEmail);
    await page.locator("input[type=password]").fill(PASSWORD);
    await page.locator("button[type=submit]").click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    // The claim under test is that the file does not leave the machine, so
    // watch the wire rather than the wording.
    const uploads: string[] = [];
    page.on("request", (r) => {
      if (r.url().includes("/org/members/import")) uploads.push(r.method());
    });

    await page.goto(`${PORTAL}/members/invite`, { waitUntil: "networkidle" });
    await page.locator("input[type=file]").setInputFiles({
      name: "eligibility.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(`email,external_ref,diagnosis\n${memberEmail},EMP-1,F41.1\n`),
    });

    await expect(page.getByText(/Not imported, and not uploaded/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/diagnosis/)).toBeVisible();
    expect(uploads, "a file with a health column was sent to the server").toHaveLength(0);

    // The clean version of the same file goes through, and the row for an
    // address with no CereBro account is reported rather than silently missing.
    const stranger = `import-stranger-${suffix}@test.app`;
    await page.locator("input[type=file]").setInputFiles({
      name: "eligibility.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(
        `email,external_ref\n${memberEmail},EMP-1\n${stranger},EMP-2\n`,
      ),
    });
    await expect(page.getByText(/Import finished/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/1 seat added/i)).toBeVisible();
    await expect(page.getByRole("cell", { name: "EMP-2" })).toBeVisible();

    // The report identifies rows by line and reference — never by address.
    const shown = await page.locator("body").innerText();
    expect(shown, "the import report echoed an email address").not.toContain(stranger);
    expect(shown).not.toContain(memberEmail);

    // And the seat is really there.
    const ownerToken = await login(request, ownerEmail);
    const members = await request.get(`${API}/org/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
    });
    const refs = (await members.json()).map((m: { external_ref: string }) => m.external_ref);
    expect(refs).toEqual(["EMP-1"]);
  });

  // The member's side of the same transaction. It lives in this file rather
  // than app.spec.ts because it needs an organisation, and this is where the
  // provisioning helpers are — but what it checks is what a PERSON sees after
  // their employer buys them a seat.
  test("a sponsored seat reaches the member, and offers nothing to cancel", async ({ page, request }) => {
    const suffix = unique();
    const ownerEmail = `sponsor-owner-${suffix}@test.app`;
    const memberEmail = `sponsored-${suffix}@test.app`;

    await signUp(request, ownerEmail);
    await signUp(request, memberEmail);

    // Signed in as themselves, before anyone pays: the ordinary free account.
    await page.goto(`${APP}/signin`, { waitUntil: "networkidle" });
    await page.locator('input[type="email"]').fill(memberEmail);
    await page.locator('input[type="password"]').fill(PASSWORD);
    // The password submit is "Continue with email" — "Sign in" would also match
    // the tab and the Apple button.
    await page.getByRole("button", { name: "Continue with email" }).click();
    await page.waitForURL((u) => !u.pathname.includes("/signin"), { timeout: 20_000 });

    await page.goto(`${APP}/account`, { waitUntil: "networkidle" });
    await expect(page.getByRole("button", { name: /Upgrade to Premium/i })).toBeVisible({ timeout: 20_000 });

    // Their employer buys the seat.
    const staffToken = await login(request, STAFF_EMAIL, STAFF_PASSWORD);
    await request.post(`${API}/admin/organizations`, {
      headers: { Authorization: `Bearer ${staffToken}` },
      data: { name: `Sponsor Co ${suffix}`, admin_email: ownerEmail, seats_licensed: 10 },
    });
    const ownerToken = await login(request, ownerEmail);
    const added = await request.post(`${API}/org/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { email: memberEmail },
    });
    expect(added.status(), await added.text()).toBe(201);

    // Same account, same session — premium arrives without them doing anything.
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByText(/Premium is provided by your organisation/i)).toBeVisible({ timeout: 20_000 });

    // ...and neither of the other two billing branches is showing. A cancel
    // link here would open Stripe's portal on a customer that does not exist,
    // and an upgrade button would sell them what they already have.
    await expect(page.getByRole("button", { name: /Upgrade to Premium/i })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /Manage or cancel subscription/i })).toHaveCount(0);
  });
});
