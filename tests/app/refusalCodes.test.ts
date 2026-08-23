import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";

/**
 * The three refusals the server can answer with, told apart by CODE.
 *
 * Two of them are 429 and mean opposite things, and a third 429 — slowapi's IP
 * throttle — means a third thing again. Branching on the status alone would
 * offer an upgrade to somebody who merely typed too fast, or tell somebody who
 * has hit a fixed daily ceiling that paying would fix it. Neither is true and
 * one of them is manipulative, which is why the server sends a code and the
 * client reads it.
 *
 * The outbox tests are the ones that were actually load-bearing: the new error
 * types carry no `.status`, so before they were named in `queueable` the queue
 * read them as "the network never answered" and would have retried a 403
 * forever.
 */

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  localStorage.clear();
  localStorage.setItem("cz_access", "test-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function reply(status: number, body: unknown) {
  return {
    ok: false,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

describe("the server's refusal codes", () => {
  it("reads a free-tier cap as an upgrade prompt", async () => {
    const { api, FreeLimitError } = await import("@/lib/api");
    fetchMock.mockResolvedValue(
      reply(429, {
        detail: { code: "free_daily_limit", message: "Daily free limit reached (50 messages).", limit: 50, used: 50, resets_at: "2026-08-24T00:00:00+00:00" },
      }),
    );
    await expect(api("/chat/messages")).rejects.toBeInstanceOf(FreeLimitError);
  });

  it("reads a daily ceiling as its own thing, not an upgrade prompt", async () => {
    // Same status as the cap above. Only the code separates them, and the
    // remedies are opposite: one is "upgrade", this one is "come back
    // tomorrow" — the ceiling is identical on every tier, so an upgrade would
    // be selling a fix that is not for sale.
    const { api, DailyCeilingError, FreeLimitError } = await import("@/lib/api");
    fetchMock.mockResolvedValue(
      reply(429, {
        detail: { code: "daily_ceiling", feature: "voice_tts", message: "You've hit the daily limit for this.", limit: 2000, resets_at: "2026-08-24T00:00:00+00:00" },
      }),
    );
    const err = await api("/voice/tts").catch((e) => e);
    expect(err).toBeInstanceOf(DailyCeilingError);
    expect(err).not.toBeInstanceOf(FreeLimitError);
    expect(err.feature).toBe("voice_tts");
    expect(err.limit).toBe(2000);
  });

  it("reads an unconfirmed address as a recoverable state with a named feature", async () => {
    const { api, VerificationRequiredError } = await import("@/lib/api");
    fetchMock.mockResolvedValue(
      reply(403, {
        detail: { code: "email_unverified", feature: "voice", message: "Confirm your email address to use this." },
      }),
    );
    const err = await api("/voice/tts").catch((e) => e);
    expect(err).toBeInstanceOf(VerificationRequiredError);
    // Named so a screen can say WHICH thing is waiting rather than showing a
    // generic wall.
    expect(err.feature).toBe("voice");
  });

  it("leaves an ordinary throttle as a plain error", async () => {
    // slowapi answers `{"error": …}` with no `detail`, and means "slow down"
    // rather than either of the above.
    const { api, DailyCeilingError, FreeLimitError } = await import("@/lib/api");
    fetchMock.mockResolvedValue(reply(429, { error: "Rate limit exceeded: 10 per 1 minute" }));
    const err = await api("/auth/signup").catch((e) => e);
    expect(err).not.toBeInstanceOf(FreeLimitError);
    expect(err).not.toBeInstanceOf(DailyCeilingError);
    expect(String(err.message)).toContain("Rate limit exceeded");
  });

  it("renders the reset in the reader's own timezone, not as 'midnight'", async () => {
    // Both windows are UTC, so "midnight" is wrong for most of the world — in
    // India these clear at 05:30 local.
    const { DailyCeilingError } = await import("@/lib/api");
    const err = new DailyCeilingError({ resets_at: "2026-08-24T00:00:00+00:00" });
    expect(err.resetText).not.toBe("midnight UTC");
    expect(err.resetText).toMatch(/\d/);
  });

  it("falls back to naming UTC when there is no instant to render", async () => {
    const { DailyCeilingError } = await import("@/lib/api");
    expect(new DailyCeilingError({}).resetText).toBe("midnight UTC");
    expect(new DailyCeilingError({ resets_at: "not-a-date" }).resetText).toBe("midnight UTC");
  });
});

describe("the offline queue and definite refusals", () => {
  it("does not keep a refusal for later", async () => {
    // This is the bug these types introduced and this test pins. None of them
    // carries a `.status`, so `queueable` read them as "the network never
    // answered" and queued them — meaning a 403 would be retried on every
    // drain, forever, while the person was told it was "saved, will sync".
    const { queueable } = await import("@/lib/outbox");
    const { DailyCeilingError, FreeLimitError, VerificationRequiredError } = await import("@/lib/api");

    expect(queueable(new FreeLimitError({}))).toBe(false);
    expect(queueable(new DailyCeilingError({}))).toBe(false);
    expect(queueable(new VerificationRequiredError({}))).toBe(false);
  });

  it("still keeps a genuine network failure", async () => {
    const { queueable } = await import("@/lib/outbox");
    // fetch rejects with a TypeError and no status when nothing answered.
    expect(queueable(new TypeError("Failed to fetch"))).toBe(true);
  });

  it("still keeps a server-side temporary failure", async () => {
    const { queueable } = await import("@/lib/outbox");
    expect(queueable(Object.assign(new Error("boom"), { status: 503 }))).toBe(true);
    expect(queueable(Object.assign(new Error("slow down"), { status: 429 }))).toBe(true);
  });

  it("still drops an ordinary client error", async () => {
    const { queueable } = await import("@/lib/outbox");
    expect(queueable(Object.assign(new Error("bad"), { status: 400 }))).toBe(false);
  });
});
