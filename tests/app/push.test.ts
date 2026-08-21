import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiMock = vi.fn();
vi.mock("@/lib/api", () => ({ api: (...args: unknown[]) => apiMock(...args) }));

import {
  getPushStatus,
  isSubscribed,
  pushSupported,
  subscribePush,
  unsubscribePush,
} from "../../apps/app/lib/push";

beforeEach(() => {
  apiMock.mockReset();
  apiMock.mockResolvedValue({});
});

afterEach(() => vi.unstubAllGlobals());

describe("whether push is usable here at all", () => {
  it("is false without a service worker", () => {
    vi.stubGlobal("navigator", {});
    expect(pushSupported()).toBe(false);
  });

  it("is false without PushManager", () => {
    vi.stubGlobal("navigator", { serviceWorker: {} });
    const w: any = globalThis.window;
    const had = "PushManager" in w;
    if (had) delete w.PushManager;
    expect(pushSupported()).toBe(false);
  });

  it("is true when the browser has the whole set", () => {
    vi.stubGlobal("navigator", { serviceWorker: {} });
    (globalThis.window as any).PushManager = class {};
    (globalThis.window as any).Notification = class {};
    expect(pushSupported()).toBe(true);
  });
});

describe("availability comes from the server", () => {
  it("reads the status endpoint rather than an env var", async () => {
    // No NEXT_PUBLIC_ key: the server reports whether VAPID is configured and
    // hands out the application server key, so a build cannot drift from it.
    apiMock.mockResolvedValue({ enabled: true, public_key: "BPk", subscriptions: 2 });
    await expect(getPushStatus()).resolves.toEqual({
      enabled: true, public_key: "BPk", subscriptions: 2,
    });
    expect(apiMock).toHaveBeenCalledWith("/users/me/push-subscriptions");
  });
});

describe("whether THIS browser is subscribed", () => {
  it("is false on a browser that cannot do push, without touching navigator", async () => {
    vi.stubGlobal("navigator", {});
    await expect(isSubscribed()).resolves.toBe(false);
  });

  it("is false when there is no registration yet", async () => {
    (globalThis.window as any).PushManager = class {};
    (globalThis.window as any).Notification = class {};
    vi.stubGlobal("navigator", {
      serviceWorker: { getRegistration: async () => undefined },
    });
    await expect(isSubscribed()).resolves.toBe(false);
  });

  it("is false when registered but not subscribed", async () => {
    (globalThis.window as any).PushManager = class {};
    (globalThis.window as any).Notification = class {};
    vi.stubGlobal("navigator", {
      serviceWorker: {
        getRegistration: async () => ({ pushManager: { getSubscription: async () => null } }),
      },
    });
    await expect(isSubscribed()).resolves.toBe(false);
  });

  it("is true when a live subscription exists", async () => {
    (globalThis.window as any).PushManager = class {};
    (globalThis.window as any).Notification = class {};
    vi.stubGlobal("navigator", {
      serviceWorker: {
        getRegistration: async () => ({
          pushManager: { getSubscription: async () => ({ endpoint: "https://push/1" }) },
        }),
      },
    });
    await expect(isSubscribed()).resolves.toBe(true);
  });
});

describe("subscribing", () => {
  // A real VAPID public key is 65 raw bytes = 87 base64url chars, which pads
  // to 88. Length matters: a string whose length % 4 == 1 needs three "="
  // and atob rejects it outright, so a made-up key of the wrong size fails
  // for a reason that has nothing to do with the code under test.
  const VAPID = "B" + "Qk-3_x".repeat(14) + "AA";

  function browser(permission: "granted" | "denied") {
    const subscribe = vi.fn(async (opts: any) => ({
      endpoint: "https://push.example/abc",
      toJSON: () => ({ keys: { p256dh: "p-key", auth: "a-key" } }),
      _opts: opts,
    }));
    vi.stubGlobal("Notification", { requestPermission: async () => permission });
    vi.stubGlobal("navigator", {
      serviceWorker: {
        register: vi.fn(async () => ({ pushManager: { subscribe } })),
        ready: Promise.resolve({}),
      },
    });
    return { subscribe };
  }

  it("refuses honestly when the person declines", async () => {
    browser("denied");
    // The toggle has to say what happened. A silent no-op would leave someone
    // believing notifications are on.
    await expect(subscribePush(VAPID)).rejects.toThrow("Notifications were declined in the browser.");
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("stores the subscription server-side once granted", async () => {
    browser("granted");
    await subscribePush(VAPID);
    const [path, init] = apiMock.mock.calls[0];
    expect(path).toBe("/users/me/push-subscriptions");
    expect(JSON.parse(init.body)).toEqual({
      endpoint: "https://push.example/abc",
      p256dh: "p-key",
      auth: "a-key",
    });
  });

  it("converts the base64url VAPID key into the bytes the API demands", async () => {
    // applicationServerKey wants raw bytes and VAPID keys travel base64url; a
    // key passed through as a string fails inside the browser with an opaque
    // error, long after the toggle has already flipped.
    const b = browser("granted");
    await subscribePush(VAPID);
    const opts = b.subscribe.mock.calls[0][0];
    expect(opts.applicationServerKey).toBeInstanceOf(Uint8Array);
    expect((opts.applicationServerKey as Uint8Array).length).toBeGreaterThan(0);
    expect(opts.userVisibleOnly).toBe(true);
  });

  it("sends empty strings rather than undefined when the keys are missing", async () => {
    vi.stubGlobal("Notification", { requestPermission: async () => "granted" });
    vi.stubGlobal("navigator", {
      serviceWorker: {
        register: async () => ({
          pushManager: {
            subscribe: async () => ({ endpoint: "https://push/2", toJSON: () => ({}) }),
          },
        }),
        ready: Promise.resolve({}),
      },
    });
    await subscribePush(VAPID);
    expect(JSON.parse(apiMock.mock.calls[0][1].body)).toEqual({
      endpoint: "https://push/2", p256dh: "", auth: "",
    });
  });
});

describe("unsubscribing", () => {
  function subscribed(unsubscribe = vi.fn(async () => true)) {
    vi.stubGlobal("navigator", {
      serviceWorker: {
        getRegistration: async () => ({
          pushManager: {
            getSubscription: async () => ({ endpoint: "https://push.example/a b", unsubscribe }),
          },
        }),
      },
    });
    return unsubscribe;
  }

  it("does nothing when there is nothing to drop", async () => {
    vi.stubGlobal("navigator", {
      serviceWorker: { getRegistration: async () => ({ pushManager: { getSubscription: async () => null } }) },
    });
    await expect(unsubscribePush()).resolves.toBeUndefined();
    expect(apiMock).not.toHaveBeenCalled();
  });

  it("tells the server, then drops it locally", async () => {
    const unsub = subscribed();
    await unsubscribePush();
    expect(apiMock.mock.calls[0][0]).toContain("/users/me/push-subscriptions?endpoint=");
    expect(apiMock.mock.calls[0][1].method).toBe("DELETE");
    expect(unsub).toHaveBeenCalled();
  });

  it("percent-encodes the endpoint it puts in the query", async () => {
    subscribed();
    await unsubscribePush();
    // An endpoint carrying a raw space or & would truncate the parameter and
    // delete nothing, or the wrong row.
    expect(apiMock.mock.calls[0][0]).toContain(encodeURIComponent("https://push.example/a b"));
  });

  it("still unsubscribes locally when the server call fails", async () => {
    // The `finally`. Otherwise a person who switched notifications off keeps
    // receiving them, which is the failure they would most reasonably call a
    // betrayal.
    const unsub = subscribed();
    apiMock.mockRejectedValue(Object.assign(new Error("gone"), { status: 500 }));
    await expect(unsubscribePush()).rejects.toThrow("gone");
    expect(unsub).toHaveBeenCalled();
  });
});
