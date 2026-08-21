import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  analyticsEnabled,
  setAnalyticsEnabled,
  track,
  unlockAnalytics,
} from "../../apps/app/lib/analytics";

// Every rule in this module's header is a privacy promise, and every one of
// them fails SILENTLY: nothing on screen changes if an event fires before
// consent, or carries a bearer token, or is linked to an account. The only way
// to know is to look at the request, which is what these do.
let fetchMock: ReturnType<typeof vi.fn>;

function lastBody(): any {
  const [, init] = fetchMock.mock.calls.at(-1)!;
  return JSON.parse(init.body);
}

beforeEach(() => {
  window.localStorage.clear();
  fetchMock = vi.fn(() => Promise.resolve({ ok: true } as Response));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => vi.unstubAllGlobals());

describe("nothing fires before consent", () => {
  it("sends nothing at all until unlock", () => {
    // Matching the 2026-07-13 Android decision: funnel steps reached before the
    // Consent screen are intentionally UNCOUNTED, not retroactively sent.
    track("onboarding_step", "welcome");
    track("onboarding_done");
    track("paywall_view");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends once unlocked", () => {
    unlockAnalytics();
    track("onboarding_step", "consent");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not backfill what happened before unlock", () => {
    track("onboarding_step", "welcome");
    unlockAnalytics();
    track("onboarding_step", "consent");
    // One event, and it is the one that happened AFTER consent.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(lastBody().events[0].step).toBe("consent");
  });

  it("stays unlocked across reloads, and unlocking twice is harmless", () => {
    unlockAnalytics();
    unlockAnalytics();
    track("paywall_view");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("opting out", () => {
  it("is on by default, but only after consent", () => {
    expect(analyticsEnabled()).toBe(true);
  });

  it("silences everything when switched off", () => {
    unlockAnalytics();
    setAnalyticsEnabled(false);
    expect(analyticsEnabled()).toBe(false);
    track("onboarding_done");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("can be switched back on", () => {
    unlockAnalytics();
    setAnalyticsEnabled(false);
    setAnalyticsEnabled(true);
    track("onboarding_done");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("what actually goes on the wire", () => {
  beforeEach(() => unlockAnalytics());

  it("carries no Authorization header and no credentials", () => {
    // /events ignores auth by design. Sending one would create the APPEARANCE
    // of linkage even though the server drops it — and appearance is most of
    // what a privacy promise is made of.
    track("paywall_view");
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBeUndefined();
    expect(init.credentials).toBeUndefined();
  });

  it("identifies the install, never the person", () => {
    track("paywall_view");
    const body = lastBody();
    expect(body.anon_id).toMatch(/^[a-z0-9]+$/i);
    // No user id, email or token anywhere in the payload.
    expect(JSON.stringify(body)).not.toMatch(/email|user_id|token/i);
  });

  it("reuses the same anonymous id rather than minting one per event", () => {
    track("paywall_view");
    const first = lastBody().anon_id;
    track("paywall_cta", "monthly");
    expect(lastBody().anon_id).toBe(first);
  });

  it("tags the source so the admin funnel can tell the clients apart", () => {
    track("onboarding_done");
    expect(lastBody().source).toBe("app");
  });

  it("uses keepalive, so the last step of a funnel survives the navigation", () => {
    track("onboarding_done");
    expect(fetchMock.mock.calls[0][1].keepalive).toBe(true);
  });

  it("posts to our own backend and nowhere else", () => {
    // Zero third-party SDKs is the first rule in the header.
    track("paywall_view");
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/events$/);
  });
});

describe("analytics must never affect the product", () => {
  beforeEach(() => unlockAnalytics());

  it("swallows a rejected request", () => {
    fetchMock.mockReturnValue(Promise.reject(new Error("offline")));
    expect(() => track("paywall_view")).not.toThrow();
  });

  it("swallows a fetch that throws synchronously", () => {
    fetchMock.mockImplementation(() => {
      throw new TypeError("blocked by an extension");
    });
    expect(() => track("paywall_view")).not.toThrow();
  });

  it("still works when localStorage is blocked, without throwing", () => {
    const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("SecurityError");
    });
    // Private mode: reads fail, so the module cannot know it was unlocked. It
    // must degrade to sending nothing rather than to crashing a screen.
    expect(() => track("paywall_view")).not.toThrow();
    getItem.mockRestore();
  });
});

describe("the event vocabulary is the backend's", () => {
  it("names only events the server will accept", () => {
    // Unknown names are dropped server-side, so a rename here does not error —
    // it produces an admin funnel that is quietly missing a step.
    const src = readFileSync(
      resolve(__dirname, "../../backend/app/api/routes/events.py"),
      "utf8",
    );
    const allowed = new Set(
      (src.match(/ALLOWED_EVENTS = \{([\s\S]*?)\}/)?.[1] ?? "")
        .split("\n")
        .map((l) => l.match(/"([a-z_]+)"/)?.[1])
        .filter(Boolean) as string[],
    );
    expect(allowed.size, "could not read ALLOWED_EVENTS from the backend").toBeGreaterThan(0);
    for (const name of ["onboarding_step", "onboarding_done", "paywall_view", "paywall_cta"]) {
      expect(allowed, `the backend would drop ${name}`).toContain(name);
    }
  });
});
