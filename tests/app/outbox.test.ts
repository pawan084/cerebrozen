import { beforeEach, describe, expect, it, vi } from "vitest";

// `api` is the only thing the queue talks to, so it is the only thing mocked.
// FreeLimitError comes from the REAL module — `queueable` decides on
// `instanceof`, and a stand-in class would make that check pass for the wrong
// reason and hide the very confusion the type exists to prevent (the IP rate
// limiter also returns 429).
const apiMock = vi.fn();
vi.mock("../../apps/app/lib/api", async () => {
  const actual = await vi.importActual<typeof import("../../apps/app/lib/api")>(
    "../../apps/app/lib/api",
  );
  return { ...actual, api: (...args: unknown[]) => apiMock(...args) };
});

import { FreeLimitError } from "../../apps/app/lib/api";
import {
  OUTBOX_EVENT,
  __testing,
  flush,
  pendingCount,
  send,
  startOutbox,
} from "../../apps/app/lib/outbox";

const { queueable, QUEUE_KEY } = __testing;

/** The shape `api()` throws: a message with the status riding along. */
const httpError = (status: number) =>
  Object.assign(new Error(`Request failed: ${status}`), { status });

/** What `fetch` rejects with when the network never answered — no status. */
const networkError = () => new TypeError("Failed to fetch");

function queued(): any[] {
  return JSON.parse(window.localStorage.getItem(QUEUE_KEY) ?? "[]");
}

beforeEach(() => {
  window.localStorage.clear();
  apiMock.mockReset();
});

describe("what is worth keeping for later", () => {
  // The predicate is the whole safety property. Queue too little and a check-in
  // tapped on a train is lost; queue too much and the person is told "saved,
  // will sync" about a write the server has already refused — a lie they only
  // discover when the entry never appears.
  it("keeps a write the network never answered", () => {
    expect(queueable(networkError())).toBe(true);
  });

  it.each([500, 502, 503, 504, 408, 429])("keeps a retryable %i", (status) => {
    expect(queueable(httpError(status))).toBe(true);
  });

  it.each([400, 401, 403, 404, 409, 422])("refuses to hide a %i", (status) => {
    expect(queueable(httpError(status))).toBe(false);
  });

  it("refuses the free-tier cap even though it arrives as 429", () => {
    // The one case where the status alone gives the wrong answer: 429 is
    // retryable from the IP limiter and final from the quota. Only the type
    // separates them.
    const capped = new FreeLimitError({ message: "no more today", limit: 20, used: 20 });
    expect(queueable(capped)).toBe(false);
    expect(queueable(httpError(429))).toBe(true);
  });
});

describe("sending", () => {
  it("returns the server's answer and queues nothing when the write lands", async () => {
    apiMock.mockResolvedValue({ id: "srv-1" });
    await expect(send("/moods", { mood: "Good" })).resolves.toEqual({ id: "srv-1" });
    expect(pendingCount()).toBe(0);
  });

  it("queues on a network failure and reports it as queued, not as an error", async () => {
    apiMock.mockRejectedValue(networkError());
    // null rather than a throw: from the person's side the check-in happened,
    // and the screen shows it either way.
    await expect(send("/moods", { mood: "Low" })).resolves.toBeNull();
    expect(pendingCount()).toBe(1);
    expect(queued()[0]).toMatchObject({ path: "/moods", body: { mood: "Low" } });
  });

  it("rethrows a 4xx instead of swallowing it", async () => {
    apiMock.mockRejectedValue(httpError(422));
    await expect(send("/moods", { mood: "?" })).rejects.toThrow(/422/);
    expect(pendingCount()).toBe(0);
  });

  it("mints the idempotency key when queuing, and reuses it on every retry", async () => {
    // The contract with backend/app/services/idempotency.py, and the reason a
    // key minted at SEND time would be a bug: a retry after a crash must reuse
    // the key of an attempt that may already have reached the server, or the
    // replay creates a second check-in.
    apiMock.mockRejectedValueOnce(networkError());
    await send("/moods", { mood: "Okay" });
    const key = queued()[0].key;
    expect(key).toBeTruthy();

    // First drain fails too — the item stays, key unchanged.
    apiMock.mockRejectedValueOnce(httpError(503));
    await flush();
    expect(queued()[0].key).toBe(key);

    // Second drain succeeds, and the header carries that same original key.
    apiMock.mockResolvedValueOnce({ ok: true });
    await flush();
    expect(apiMock).toHaveBeenLastCalledWith(
      "/moods",
      expect.objectContaining({ headers: { "Idempotency-Key": key } }),
    );
  });

  it("sends the key on the first attempt too, not only on retries", async () => {
    apiMock.mockResolvedValue({});
    await send("/journal", { text: "hi" });
    const [, init] = apiMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBeTruthy();
  });
});

describe("draining", () => {
  async function queueThree() {
    apiMock.mockRejectedValue(networkError());
    await send("/moods", { n: 1 });
    await send("/moods", { n: 2 });
    await send("/moods", { n: 3 });
    apiMock.mockReset();
  }

  it("sends oldest first — the queue is a journal of what the person did", async () => {
    await queueThree();
    apiMock.mockResolvedValue({});
    expect(await flush()).toBe(3);
    const bodies = apiMock.mock.calls.map(([, i]) =>
      JSON.parse((i as RequestInit).body as string),
    );
    expect(bodies).toEqual([{ n: 1 }, { n: 2 }, { n: 3 }]);
    expect(pendingCount()).toBe(0);
  });

  it("stops at the first failure rather than shuffling the order", async () => {
    await queueThree();
    apiMock.mockResolvedValueOnce({});
    apiMock.mockRejectedValueOnce(httpError(503));
    expect(await flush()).toBe(1);
    // Two left, still in order, and #2 was NOT skipped over in favour of #3.
    expect(queued().map((i) => i.body)).toEqual([{ n: 2 }, { n: 3 }]);
  });

  it("drops an item the server refuses outright and keeps going", async () => {
    // A 4xx will never succeed on retry. Left in place it would block every
    // later write behind it forever — the queue would stop being a queue.
    await queueThree();
    apiMock.mockRejectedValueOnce(httpError(422));
    apiMock.mockResolvedValue({});
    expect(await flush()).toBe(2);
    expect(pendingCount()).toBe(0);
  });

  it("costs no network when there is nothing to send", async () => {
    expect(await flush()).toBe(0);
    expect(apiMock).not.toHaveBeenCalled();
  });
});

describe("the queue as storage", () => {
  it("survives a reload — it is localStorage, not memory", async () => {
    apiMock.mockRejectedValue(networkError());
    await send("/moods", { mood: "Good" });

    // Re-import with a fresh module registry: the same tab, reopened.
    vi.resetModules();
    const reloaded = await import("../../apps/app/lib/outbox");
    expect(reloaded.pendingCount()).toBe(1);
  });

  it("is bounded so a long offline spell cannot fill the storage quota", async () => {
    apiMock.mockRejectedValue(networkError());
    for (let n = 0; n < 55; n++) await send("/moods", { n });
    expect(pendingCount()).toBe(50);
    // The bound drops the OLDEST, so the most recent writes are the ones kept.
    expect(queued()[49].body).toEqual({ n: 54 });
  });

  it("does not throw when storage is full", async () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("QuotaExceededError");
    });
    apiMock.mockRejectedValue(networkError());
    // A blocked store must not take down the app it exists to protect.
    await expect(send("/moods", { mood: "Good" })).resolves.toBeNull();
    setItem.mockRestore();
  });

  it("survives a corrupted queue rather than crashing every write", () => {
    window.localStorage.setItem(QUEUE_KEY, "{not json");
    expect(pendingCount()).toBe(0);
  });
});

describe("telling the rest of the app", () => {
  it("announces the pending count when a write is queued", async () => {
    const seen: any[] = [];
    window.addEventListener(OUTBOX_EVENT, (e) => seen.push((e as CustomEvent).detail));
    apiMock.mockRejectedValue(networkError());
    await send("/moods", { mood: "Low" });
    expect(seen.at(-1)).toEqual({ pending: 1, sent: 0 });
  });

  it("announces what got sent, so a stale screen knows to refetch", async () => {
    apiMock.mockRejectedValue(networkError());
    await send("/moods", { mood: "Low" });
    const seen: any[] = [];
    window.addEventListener(OUTBOX_EVENT, (e) => seen.push((e as CustomEvent).detail));
    apiMock.mockReset();
    apiMock.mockResolvedValue({});
    await flush();
    expect(seen.at(-1)).toEqual({ pending: 0, sent: 1 });
  });

  it("drains when the browser says the network is back, and unhooks on dispose", async () => {
    apiMock.mockRejectedValue(networkError());
    await send("/moods", { mood: "Low" });
    apiMock.mockReset();
    apiMock.mockResolvedValue({});

    const stop = startOutbox(); // flushes once on load
    await vi.waitFor(() => expect(pendingCount()).toBe(0));

    apiMock.mockRejectedValue(networkError());
    await send("/moods", { mood: "Again" });
    apiMock.mockReset();
    apiMock.mockResolvedValue({});

    window.dispatchEvent(new Event("online"));
    await vi.waitFor(() => expect(pendingCount()).toBe(0));

    // After disposal the listener is gone — a mount/unmount cycle must not
    // stack a drain per mount.
    stop();
    apiMock.mockReset();
    window.dispatchEvent(new Event("online"));
    expect(apiMock).not.toHaveBeenCalled();
  });
});
