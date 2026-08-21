import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The client holds module state — the access token lives in memory on purpose
// (XSS cannot lift it from storage) — so every test takes a fresh copy of the
// module rather than trying to unwind it.
type Api = typeof import("../../apps/app/lib/api");
async function freshApi(): Promise<Api> {
  vi.resetModules();
  return import("../../apps/app/lib/api");
}

const REFRESH_KEY = "cerebro_app_refresh";

/** A Response as `fetch` would hand it back. */
function response(status: number, body?: unknown, ok?: boolean): Response {
  return {
    ok: ok ?? (status >= 200 && status < 300),
    status,
    json: async () => {
      if (body === undefined) throw new SyntaxError("not json");
      return body;
    },
  } as Response;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  window.localStorage.clear();
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("headers", () => {
  it("sends JSON by default", async () => {
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(200, {}));
    await api("/moods", { method: "POST", body: "{}" });
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Content-Type"]).toBe("application/json");
  });

  it("does NOT stamp JSON over a FormData body", async () => {
    // The browser adds the multipart boundary itself. Stamping
    // "application/json" over a file upload produces a body the server cannot
    // parse — this is how /voice/stt would have failed, silently, on the one
    // request whose body is not JSON.
    const { authedFetch } = await freshApi();
    fetchMock.mockResolvedValue(response(200, {}));
    const form = new FormData();
    form.append("audio", new Blob(["x"]), "clip.webm");
    await authedFetch("/voice/stt", { method: "POST", body: form });
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Content-Type"]).toBeUndefined();
  });

  it("lets the caller's own headers win", async () => {
    // The outbox depends on this: its Idempotency-Key has to survive.
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(200, {}));
    await api("/moods", { method: "POST", headers: { "Idempotency-Key": "k-1" } });
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Idempotency-Key"]).toBe("k-1");
  });
});

describe("what a failure carries back", () => {
  it("attaches the status alongside the message", async () => {
    // The offline queue has to tell "the server refused this" from "the
    // network never answered", and by this point the message is the server's
    // own prose. A message is not a status.
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(503, { detail: "Service unavailable" }));
    await expect(api("/moods")).rejects.toMatchObject({
      status: 503,
      message: "Service unavailable",
    });
  });

  it("surfaces the rate limiter's own key rather than a bare number", async () => {
    // slowapi answers with `error`, not `detail`; without this a throttled
    // person only ever saw "Request failed: 429".
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(429, { error: "Rate limit exceeded" }));
    await expect(api("/chat")).rejects.toThrow("Rate limit exceeded");
  });

  it("falls back to a status line when the body is not JSON at all", async () => {
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(502));
    await expect(api("/moods")).rejects.toMatchObject({
      status: 502,
      message: "Request failed: 502",
    });
  });

  it("reads an object detail without printing [object Object]", async () => {
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(400, { detail: { message: "Pick a mood first" } }));
    await expect(api("/moods")).rejects.toThrow("Pick a mood first");
  });
});

describe("the free-tier cap, which arrives as a 429 like everything else", () => {
  it("is its own type when the server says so", async () => {
    const { api, FreeLimitError } = await freshApi();
    fetchMock.mockResolvedValue(
      response(429, {
        detail: { code: "free_daily_limit", message: "You've used today's free messages.", limit: 20, used: 20 },
      }),
    );
    const err = await api("/chat").catch((e) => e);
    expect(err).toBeInstanceOf(FreeLimitError);
    expect(err.limit).toBe(20);
    expect(err.used).toBe(20);
  });

  it("is NOT raised for the IP rate limiter's 429", async () => {
    // Offering an upgrade to someone who merely typed too fast would be wrong
    // and manipulative. Only detail.code is trusted, never the status alone.
    const { api, FreeLimitError } = await freshApi();
    fetchMock.mockResolvedValue(response(429, { error: "Rate limit exceeded" }));
    const err = await api("/chat").catch((e) => e);
    expect(err).not.toBeInstanceOf(FreeLimitError);
    expect(err.status).toBe(429);
  });

  it("names a reset time in the browser's own timezone, not 'midnight'", async () => {
    const { FreeLimitError } = await freshApi();
    const capped = new FreeLimitError({ resets_at: "2026-08-21T18:30:00Z" });
    expect(capped.resetText).not.toBe("midnight UTC");
    expect(capped.resetText).toMatch(/\d/);
  });

  it("says midnight UTC when it has nothing better, rather than 'Invalid Date'", async () => {
    const { FreeLimitError } = await freshApi();
    expect(new FreeLimitError({}).resetText).toBe("midnight UTC");
    expect(new FreeLimitError({ resets_at: "not a date" }).resetText).toBe("midnight UTC");
  });
});

describe("401 versus 403 — register D1", () => {
  it("does not treat a 403 as a dead session", async () => {
    // The backend answers 403 for consent-gated routes. Treating it as
    // "unauthorized" silently destroyed the session of anyone opening
    // /patterns with AI memory switched off, and buried that page's own
    // friendly explanation behind the word "unauthorized".
    const { authedFetch, hasSession } = await freshApi();
    window.localStorage.setItem(REFRESH_KEY, "r-1");
    fetchMock
      // A fresh page load has a refresh token but no access token yet, so the
      // FIRST fetch is always the rotation — not the request under test.
      .mockResolvedValueOnce(response(200, { access_token: "a-1", refresh_token: "r-2" }))
      .mockResolvedValueOnce(response(403, { detail: "AI memory is switched off" }, false));

    const res = await authedFetch("/patterns");
    expect(res.status).toBe(403);
    expect(hasSession()).toBe(true); // still signed in
  });

  it("rotates once on a 401, then retries the original request", async () => {
    const { authedFetch } = await freshApi();
    window.localStorage.setItem(REFRESH_KEY, "r-1");
    fetchMock
      // the initial refresh a fresh page load performs
      .mockResolvedValueOnce(response(200, { access_token: "a-1", refresh_token: "r-2" }))
      .mockResolvedValueOnce(response(401, {}, false))
      .mockResolvedValueOnce(response(200, { access_token: "a-2", refresh_token: "r-3" }))
      .mockResolvedValueOnce(response(200, { ok: true }));

    const res = await authedFetch("/moods");
    expect(res.status).toBe(200);
  });

  it("gives up and clears the session when the rotation also fails", async () => {
    const { authedFetch, hasSession } = await freshApi();
    window.localStorage.setItem(REFRESH_KEY, "r-1");
    fetchMock
      .mockResolvedValueOnce(response(200, { access_token: "a-1", refresh_token: "r-2" }))
      .mockResolvedValueOnce(response(401, {}, false))
      .mockResolvedValueOnce(response(401, {}, false));

    await expect(authedFetch("/moods")).rejects.toMatchObject({ status: 401 });
    expect(hasSession()).toBe(false);
  });
});

describe("204", () => {
  it("returns undefined instead of trying to parse an empty body", async () => {
    const { api } = await freshApi();
    fetchMock.mockResolvedValue(response(204));
    await expect(api("/memories/1")).resolves.toBeUndefined();
  });
});

describe("signing out has to take everything personal with it", () => {
  it("clears every personal key, not just the token", async () => {
    // Register D24: clearSession only dropped the refresh token, so the cached
    // safety plan, the journal draft and the onboarding answers stayed
    // readable by whoever used the browser next — and account deletion left
    // them behind too.
    const { clearSession } = await freshApi();
    const personal = [
      REFRESH_KEY,
      "cerebro_app_outbox",
      "cbz-safety-plan",
      "cerebro_app_journal_draft",
      "cerebro_app_onboarding_draft",
      "cerebro_app_ritual",
      "cerebro_app_onboarded",
    ];
    for (const k of personal) window.localStorage.setItem(k, "something private");
    window.localStorage.setItem("cerebro_theme", "dawn");

    clearSession();

    for (const k of personal) expect(window.localStorage.getItem(k)).toBeNull();
    // The theme belongs to the DEVICE, not the account, and is deliberately
    // absent from the list — clearing it would relight a dark room at 2am.
    expect(window.localStorage.getItem("cerebro_theme")).toBe("dawn");
  });

  it("keeps clearing after one key refuses to be removed", async () => {
    const { clearSession } = await freshApi();
    window.localStorage.setItem(REFRESH_KEY, "r-1");
    window.localStorage.setItem("cerebro_app_journal_draft", "private");
    const removeItem = vi
      .spyOn(Storage.prototype, "removeItem")
      .mockImplementationOnce(() => {
        throw new DOMException("SecurityError");
      });
    clearSession();
    removeItem.mockRestore();
    // A blocked store on the first key must not leave the rest of someone's
    // data sitting in a shared browser.
    expect(window.localStorage.getItem("cerebro_app_journal_draft")).toBeNull();
  });

  it("still holds the outbox key the queue actually writes to", () => {
    // Hand-synced across two files by design, and the failure is ugly: on a
    // shared browser the next person's first drain would post the previous
    // person's check-ins into their account. Checked here because neither
    // module can import the other's constant.
    const apiSrc = readFileSync(resolve(__dirname, "../../apps/app/lib/api.ts"), "utf8");
    const outboxSrc = readFileSync(resolve(__dirname, "../../apps/app/lib/outbox.ts"), "utf8");
    const queueKey = outboxSrc.match(/const QUEUE_KEY = "([^"]+)"/)?.[1];
    expect(queueKey, "outbox.ts no longer declares QUEUE_KEY the same way").toBeTruthy();
    expect(
      apiSrc.includes(`"${queueKey}"`),
      `PERSONAL_KEYS in api.ts does not list ${queueKey} — a sign-out would leave the queue behind`,
    ).toBe(true);
  });
});

describe("the sign-in messages — register D22", () => {
  // Every non-OK response used to map to "Invalid email or password.", so an
  // outage or a rate limit told people their credentials were wrong and
  // invited a pointless password reset. Only 400/401 mean that.
  it.each([400, 401])("blames the credentials for %i, and only then", async (status) => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(status, {}));
    await expect(signIn("a@b.c", "nope")).rejects.toThrow("Invalid email or password.");
  });

  it("explains a rate limit as a rate limit", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(429, {}));
    await expect(signIn("a@b.c", "x")).rejects.toThrow(/wait a minute/);
  });

  it("reassures rather than accuses on a 5xx", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(503, {}));
    await expect(signIn("a@b.c", "right")).rejects.toThrow(/Nothing is wrong with your account/);
  });

  it("prefers the server's detail for anything else", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(418, { detail: "This account is disabled." }));
    await expect(signIn("a@b.c", "x")).rejects.toThrow("This account is disabled.");
  });

  it("still says something when the error body is not JSON", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(418));
    await expect(signIn("a@b.c", "x")).rejects.toThrow(/Couldn't sign in just now/);
  });

  it("posts form-encoded with `username`, as OAuth2 requires", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "a", refresh_token: "r" }));
    await signIn("a@b.c", "secret");
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Content-Type"]).toBe("application/x-www-form-urlencoded");
    expect(String(init.body)).toContain("username=a%40b.c");
  });

  it("stores the session on success", async () => {
    const { signIn, hasSession } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "a", refresh_token: "r-1" }));
    await signIn("a@b.c", "secret");
    expect(hasSession()).toBe(true);
  });
});

describe("the other ways in", () => {
  it("signs up and keeps the session", async () => {
    const { signUp, hasSession } = await freshApi();
    fetchMock.mockResolvedValue(response(201, { access_token: "a", refresh_token: "r-1" }));
    await signUp("new@b.c", "password123", "New");
    expect(hasSession()).toBe(true);
  });

  it("surfaces the server's reason for refusing a sign-up", async () => {
    // "Email already registered" has to reach the person; a generic failure
    // would send them to the reset flow for an account they do not have.
    const { signUp } = await freshApi();
    fetchMock.mockResolvedValue(response(400, { detail: "Email already registered." }));
    await expect(signUp("a@b.c", "x", "N")).rejects.toThrow("Email already registered.");
  });

  it.each([
    ["signInApple", "Apple sign-in failed. Try email instead."],
    ["signInGoogle", "Google sign-in failed. Try email instead."],
  ])("%s points back at email when the provider fails", async (fn, message) => {
    // The providers are inert until the owner configures them, so this is the
    // path most users would actually hit — it must name a way forward.
    const mod: any = await freshApi();
    fetchMock.mockResolvedValue(response(401, {}));
    await expect(mod[fn]("token")).rejects.toThrow(message);
  });

  it.each(["signInApple", "signInGoogle"])("%s stores the session when it works", async (fn) => {
    const mod: any = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "a", refresh_token: "r-1" }));
    await mod[fn]("token", "Name");
    expect(mod.hasSession()).toBe(true);
  });

  it("asks for a one-time code, and says so plainly when it cannot", async () => {
    const { requestOtp } = await freshApi();
    fetchMock.mockResolvedValue(response(429, {}));
    await expect(requestOtp("a@b.c")).rejects.toThrow(/Couldn't send a code/);
  });

  it("distinguishes an invalid code from a failure to send one", async () => {
    const { verifyOtp } = await freshApi();
    fetchMock.mockResolvedValue(response(400, {}));
    await expect(verifyOtp("a@b.c", "000000")).rejects.toThrow("Invalid or expired code.");
  });

  it("keeps the session after a verified code", async () => {
    const { verifyOtp, hasSession } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "a", refresh_token: "r-1" }));
    await verifyOtp("a@b.c", "123456");
    expect(hasSession()).toBe(true);
  });
});

describe("onboarding state", () => {
  it("remembers that onboarding finished, and can be reset", async () => {
    const { setOnboarded, hasOnboarded, resetOnboarding } = await freshApi();
    expect(hasOnboarded()).toBe(false);
    setOnboarded();
    expect(hasOnboarded()).toBe(true);
    resetOnboarding();
    expect(hasOnboarded()).toBe(false);
  });

  it("is cleared by a sign-out, since it belongs to the person", async () => {
    const { setOnboarded, hasOnboarded, clearSession } = await freshApi();
    setOnboarded();
    clearSession();
    expect(hasOnboarded()).toBe(false);
  });
});
