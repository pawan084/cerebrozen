import { test, expect, request as pwRequest, APIRequestContext } from "@playwright/test";

/**
 * The gates, switched ON, driven over real HTTP.
 *
 * The main `api` service deliberately runs with them off — every browser test
 * shares one IP, so real rate limits make the suite flaky, and the verification
 * gate is inert without SMTP. Those are the right defaults, and they left the
 * *enabled* half of three features covered only by unit tests: the bot
 * challenge, the email verification gate, and the account-keyed rate limit.
 *
 * A unit test can prove the service refuses. It cannot prove the wiring — that
 * the decorator is on the route, that the guard runs before the 409, that the
 * middleware forwards the header the key function reads, that the config knob
 * reaches the object that consults it. Everything below goes through the real
 * stack against `api-gated`, which is the same image with the switches thrown.
 *
 * `challenge-stub` stands in for Cloudflare and answers success only for the
 * magic token, so this one instance exercises both the accept and the refuse
 * path — and accounts can still be created to test everything downstream.
 */

const API = process.env.GATED_API_URL || "http://api-gated:8000";
const GOOD_TOKEN = "e2e-good";

function address(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@test.app`;
}

/**
 * A context that presents itself as one specific caller.
 *
 * Every test here runs from the same container, and signup is address-limited
 * at 10/minute — so without this the suite exhausts its own signup budget and
 * the later tests fail with a 429 that has nothing to do with what they assert.
 * Each test takes a distinct address, which is also the honest model: these
 * ARE different callers.
 */
async function ctx(ip: string): Promise<APIRequestContext> {
  return pwRequest.newContext({
    baseURL: API,
    extraHTTPHeaders: { "X-Forwarded-For": ip },
  });
}

/** Signs up through the challenge and returns the access token. */
async function signup(c: APIRequestContext, email: string) {
  const r = await c.post("/auth/signup", {
    data: { email, password: "password123", name: "E2E", challenge_token: GOOD_TOKEN },
  });
  expect(r.status(), `signup ${email}: ${await r.text()}`).toBe(201);
  return (await r.json()).access_token as string;
}

test.describe("bot protection, configured", () => {
  test("a throwaway address is refused before anything else happens", async () => {
    const c = await ctx("203.0.113.11");
    const r = await c.post("/auth/signup", {
      data: {
        email: address("bot").replace("@test.app", "@mailinator.com"),
        password: "password123",
        name: "Bot",
        challenge_token: GOOD_TOKEN,
      },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).detail.code).toBe("throwaway_email");
    await c.dispose();
  });

  test("a privacy relay is NOT refused", async () => {
    // The trap the whole check is shaped around: Sign in with Apple hands us
    // privaterelay.appleid.com, and a list written from "looks like a burner"
    // would turn away the users who care most about privacy.
    const c = await ctx("203.0.113.12");
    const r = await c.post("/auth/signup", {
      data: {
        email: address("relay").replace("@test.app", "@privaterelay.appleid.com"),
        password: "password123",
        name: "Relay",
        challenge_token: GOOD_TOKEN,
      },
    });
    expect(r.status(), await r.text()).toBe(201);
    await c.dispose();
  });

  test("no challenge token is a refusal once a secret is configured", async () => {
    const c = await ctx("203.0.113.13");
    const r = await c.post("/auth/signup", {
      data: { email: address("silent"), password: "password123", name: "Silent" },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).detail.code).toBe("challenge_failed");
    await c.dispose();
  });

  test("a token the provider rejects is a refusal", async () => {
    const c = await ctx("203.0.113.14");
    const r = await c.post("/auth/signup", {
      data: {
        email: address("forged"),
        password: "password123",
        name: "Forged",
        challenge_token: "not-the-magic-token",
      },
    });
    expect(r.status()).toBe(400);
    expect((await r.json()).detail.code).toBe("challenge_failed");
    await c.dispose();
  });

  test("a failed challenge does not reveal whether the address exists", async () => {
    // The guard has to run BEFORE the existence check, or a bot reads
    // 409-vs-400 and learns which addresses are registered. Only expressible
    // with a live challenge, which is why it could not be an e2e test before.
    const c = await ctx("203.0.113.15");
    const taken = address("taken");
    await signup(c, taken);

    const registered = await c.post("/auth/signup", {
      data: { email: taken, password: "password123", name: "X", challenge_token: "bad" },
    });
    const fresh = await c.post("/auth/signup", {
      data: { email: address("fresh"), password: "password123", name: "X", challenge_token: "bad" },
    });

    expect(registered.status()).toBe(400);
    expect(fresh.status()).toBe(400);
    expect(await registered.json()).toEqual(await fresh.json());
    await c.dispose();
  });
});

test.describe("email verification gate, active", () => {
  test("an unverified account is refused a provider-backed feature", async () => {
    const c = await ctx("203.0.113.21");
    const token = await signup(c, address("unverified"));
    const r = await c.post("/plans/generate", {
      headers: { Authorization: `Bearer ${token}` },
      data: {},
    });
    expect(r.status(), await r.text()).toBe(403);
    const detail = (await r.json()).detail;
    expect(detail.code).toBe("email_unverified");
    expect(detail.feature).toBeTruthy();
    await c.dispose();
  });

  test("its own data stays reachable", async () => {
    // Confirming an address is a condition for spending our money, never for
    // reaching your own words or leaving.
    const c = await ctx("203.0.113.22");
    const token = await signup(c, address("reader"));
    const headers = { Authorization: `Bearer ${token}` };
    for (const path of ["/users/me", "/journal", "/moods", "/users/me/export"]) {
      const r = await c.get(path, { headers });
      expect(r.status(), `${path} was walled off`).not.toBe(403);
    }
    await c.dispose();
  });

  test("chat is not walled off, only allowanced", async () => {
    const c = await ctx("203.0.113.23");
    const token = await signup(c, address("talker"));
    const r = await c.post("/chat/messages", {
      headers: { Authorization: `Bearer ${token}` },
      data: { text: "hello there" },
    });
    expect(r.status(), await r.text()).not.toBe(403);
    await c.dispose();
  });
});

test.describe("the daily cap never stands in front of a crisis", () => {
  test("an over-cap account is refused chatter but not an urgent message", async () => {
    // The one that matters most in this file, and it was unreachable end to end
    // until the gated instance existed. `UNVERIFIED_DAILY_MESSAGES=3` on
    // api-gated keeps it to a handful of requests.
    const c = await ctx("203.0.113.31");
    const token = await signup(c, address("atcap"));
    const headers = { Authorization: `Bearer ${token}` };

    let refusedAt = -1;
    for (let i = 0; i < 6; i++) {
      const r = await c.post("/chat/messages", { headers, data: { text: `ordinary ${i}` } });
      if (r.status() === 429) {
        refusedAt = i;
        break;
      }
      expect(r.status(), `message ${i}: ${await r.text()}`).toBe(201);
    }
    expect(refusedAt, "the daily cap never fired — it is not bounding anything").toBeGreaterThan(-1);

    // Same account, same exhausted allowance, a message the keyword floor
    // flags. A billing rule must not be the thing standing between somebody and
    // the sentence they are trying to send.
    const urgent = await c.post("/chat/messages", {
      headers,
      data: { text: "i want to kill myself" },
    });
    expect(
      urgent.status(),
      "a flagged message met the daily cap — safety never blocks",
    ).not.toBe(429);
    await c.dispose();
  });
});

test.describe("rate limiting is keyed on the account, not just the address", () => {
  test("rotating the forwarded address does not buy a fresh bucket", async () => {
    // Addresses are cheap; the account key is what makes that not matter.
    // `/auth/verify/request` is account-limited at 5/minute and sends only to
    // smtp.invalid, so this costs nothing real.
    const c = await ctx("203.0.113.41");
    const token = await signup(c, address("rotator"));

    let refused = false;
    for (let i = 0; i < 8; i++) {
      const r = await c.post("/auth/verify/request", {
        headers: {
          Authorization: `Bearer ${token}`,
          // A different "caller" every time. Under address-only limiting each
          // of these is a first request and the cap never fires.
          "X-Forwarded-For": `198.51.100.${i + 1}`,
        },
      });
      if (r.status() === 429) {
        refused = true;
        break;
      }
    }
    expect(refused, "eight requests from eight addresses on one account were all allowed").toBe(true);
    await c.dispose();
  });

  test("the limiter is actually on for this instance", async () => {
    // Guards the test above from passing for the wrong reason: if limits were
    // off, nothing would ever 429 and the assertion would be vacuous.
    const c = await ctx("203.0.113.42");
    let refused = false;
    for (let i = 0; i < 25; i++) {
      const r = await c.post("/auth/signup", {
        data: { email: address("flood"), password: "password123", name: "F" },
      });
      if (r.status() === 429) {
        refused = true;
        break;
      }
    }
    expect(refused, "signup never rate-limited — RATE_LIMIT_ENABLED is not on").toBe(true);
    await c.dispose();
  });
});
