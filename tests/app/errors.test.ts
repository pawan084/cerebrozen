import { afterEach, describe, expect, it, vi } from "vitest";

import {
  addSink,
  capture,
  fingerprintOf,
  frameOf,
  framesOf,
  installGlobalHandlers,
  resetSinks,
  templatePath,
  type ErrorEvent,
} from "../../apps/app/lib/errors";

afterEach(() => {
  resetSinks();
  vi.restoreAllMocks();
});

/** Collects what the reporter decided to send, plus its serialised form —
 *  absence is asserted against the WHOLE payload, because a leak arrives in
 *  the field nobody thought to check. */
function collector() {
  const events: ErrorEvent[] = [];
  resetSinks();
  vi.spyOn(console, "error").mockImplementation(() => {});
  addSink((e) => events.push(e));
  return {
    events,
    payload: () => JSON.stringify(events),
  };
}

/** A realistic browser error: the value that broke it is in the message. */
function thrownWith(secret: string): Error {
  const err = new TypeError(`Cannot parse entry: ${secret}`);
  err.stack = [
    `TypeError: Cannot parse entry: ${secret}`,
    "    at parseEntry (https://app.cerebrozen.in/_next/static/chunks/journal-4f2.js:2:9134)",
    "    at renderJournal (https://app.cerebrozen.in/_next/static/chunks/journal-4f2.js:2:8801)",
  ].join("\n");
  return err;
}

describe("nothing a person wrote reaches an error report", () => {
  it("never sends the message, which is where the value that broke it lives", () => {
    const c = collector();
    capture(thrownWith("I have been feeling hopeless since March"), {
      via: "boundary",
      path: "/journal",
    });
    expect(c.payload()).not.toContain("hopeless");
    expect(c.payload()).not.toContain("Cannot parse entry");
  });

  it("sends the error's name, which carries no content", () => {
    const c = collector();
    capture(thrownWith("x"), { via: "boundary", path: "/journal" });
    expect(c.events[0].kind).toBe("TypeError");
  });

  it.each([
    "someone@example.com",
    "Bearer eyJhbGciOiJIUzI1NiJ9.abc.def",
    "I want to die",
    "+91 98765 43210",
  ])("keeps %s out of the payload entirely", (secret) => {
    const c = collector();
    capture(thrownWith(secret), { via: "boundary", path: "/journal" });
    expect(c.payload()).not.toContain(secret);
  });

  it("reports a thrown non-Error by SHAPE, never by its contents", () => {
    // `throw {entry: "..."}` and `throw "..."` are both legal and both carry
    // exactly the thing that must not travel.
    const c = collector();
    capture({ entry: "a private sentence" }, { via: "window", path: "/journal" });
    capture("a private sentence", { via: "window", path: "/journal" });
    expect(c.payload()).not.toContain("private sentence");
    expect(c.events[0].kind).toBe("Non-Error(object)");
    expect(c.events[1].kind).toBe("Non-Error(string)");
  });
});

describe("a stack frame is a position and nothing else", () => {
  it("reduces a V8 frame to file, line and function", () => {
    expect(
      frameOf("    at parseEntry (https://app.cerebrozen.in/_next/static/chunks/journal-4f2.js:2:9134)"),
    ).toBe("journal-4f2.js:2 in parseEntry");
  });

  it("reduces a Firefox/Safari frame the same way", () => {
    expect(frameOf("parseEntry@https://app.cerebrozen.in/chunks/journal.js:12:5")).toBe(
      "journal.js:12 in parseEntry",
    );
  });

  it("drops a query string off a bundle URL, which can carry a build token", () => {
    expect(frameOf("    at run (https://app.cerebrozen.in/a.js?v=SECRETBUILDID:9:1)")).toBe(
      "a.js:9 in run",
    );
  });

  it("skips the header line, which is the message again", () => {
    expect(frameOf("TypeError: Cannot parse entry: something private")).toBeNull();
  });

  it("keeps the frames in order and caps how many travel", () => {
    const stack = Array.from(
      { length: 30 },
      (_, i) => `    at fn${i} (https://app.cerebrozen.in/a.js:${i}:1)`,
    ).join("\n");
    const frames = framesOf(stack);
    expect(frames).toHaveLength(12);
    expect(frames[0]).toBe("a.js:0 in fn0");
  });

  it("survives an error with no stack at all", () => {
    expect(framesOf(undefined)).toEqual([]);
  });
});

describe("an id in a URL is not an identifier in a report", () => {
  it.each([
    ["/journal/2f1c9b0e-7a53-4a1e-9c8f-0d2b6a4e11aa", "/journal/{id}"],
    ["/journal/1421", "/journal/{id}"],
    ["/account/privacy", "/account/privacy"],
    ["/sleep/ritual", "/sleep/ritual"],
    ["/", "/"],
  ])("templates %s", (raw, expected) => {
    expect(templatePath(raw)).toBe(expected);
  });

  it("puts the templated route in the event, not the URL it came from", () => {
    const c = collector();
    capture(thrownWith("x"), {
      via: "boundary",
      path: "/journal/2f1c9b0e-7a53-4a1e-9c8f-0d2b6a4e11aa",
    });
    expect(c.events[0].where).toBe("route /journal/{id}");
    expect(c.payload()).not.toContain("2f1c9b0e");
  });
});

describe("fingerprints make one bug one number", () => {
  it("groups the same fault however the message varies", () => {
    // The reason the message is excluded from the key as well as the payload:
    // otherwise 400 failures of one bug arrive as 400 distinct bugs.
    const a = capture(thrownWith("entry one"), { via: "boundary", path: "/journal" });
    const b = capture(thrownWith("entry two"), { via: "boundary", path: "/journal" });
    expect(a.fingerprint).toBe(b.fingerprint);
  });

  it("separates the same fault on a different route", () => {
    const a = capture(thrownWith("x"), { via: "boundary", path: "/journal" });
    const b = capture(thrownWith("x"), { via: "boundary", path: "/sleep" });
    expect(a.fingerprint).not.toBe(b.fingerprint);
  });

  it("matches the backend's inputs: kind, route and innermost frame", () => {
    expect(fingerprintOf("TypeError", "route /journal", "a.js:1 in f")).toBe(
      fingerprintOf("TypeError", "route /journal", "a.js:1 in f"),
    );
    expect(fingerprintOf("TypeError", "route /journal", "a.js:1 in f")).not.toBe(
      fingerprintOf("RangeError", "route /journal", "a.js:1 in f"),
    );
  });
});

describe("the reporter cannot make things worse", () => {
  it("survives a sink that throws", () => {
    const c = collector();
    addSink(() => {
      throw new Error("the tracker is down");
    });
    const healthy: ErrorEvent[] = [];
    addSink((e) => healthy.push(e));
    expect(() => capture(thrownWith("x"), { via: "boundary", path: "/" })).not.toThrow();
    expect(healthy).toHaveLength(1);
    expect(c.events).toHaveLength(1);
  });

  it("works with only the default console sink", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    const event = capture(thrownWith("x"), { via: "window", path: "/home" });
    expect(event.fingerprint).toBeTruthy();
    expect(spy).toHaveBeenCalled();
  });
});

describe("the failures a render boundary never sees", () => {
  it("catches a rejected promise nobody awaited", () => {
    const c = collector();
    const teardown = installGlobalHandlers(window);
    const event = new Event("unhandledrejection") as Event & { reason?: unknown };
    event.reason = thrownWith("a private sentence");
    window.dispatchEvent(event);
    teardown();

    expect(c.events).toHaveLength(1);
    expect(c.events[0].via).toBe("unhandledrejection");
    expect(c.payload()).not.toContain("private sentence");
  });

  it("catches a script error thrown outside the tree", () => {
    const c = collector();
    const teardown = installGlobalHandlers(window);
    const event = new Event("error") as Event & { error?: unknown };
    event.error = thrownWith("x");
    window.dispatchEvent(event);
    teardown();

    expect(c.events[0].via).toBe("window");
  });

  it("takes its listeners with it, so a remount cannot double-report", () => {
    // Asserted against a fake target rather than by dispatching into the real
    // window: an `error` event nobody handles is escalated by jsdom into an
    // uncaught exception, which fails the RUN rather than the test.
    //
    // The reference identity is the point. `removeEventListener` silently does
    // nothing when handed a fresh closure, so the bug this guards is a teardown
    // that looks correct and removes nothing — every remount then stacking
    // another reporter until one crash is reported five times.
    const added: Array<[string, EventListener]> = [];
    const removed: Array<[string, EventListener]> = [];
    const fake = {
      addEventListener: (type: string, fn: EventListener) => added.push([type, fn]),
      removeEventListener: (type: string, fn: EventListener) => removed.push([type, fn]),
    } as unknown as Window;

    const teardown = installGlobalHandlers(fake);
    expect(added.map(([type]) => type).sort()).toEqual(["error", "unhandledrejection"]);

    teardown();
    expect(removed).toHaveLength(2);
    for (const [type, fn] of removed) {
      const match = added.find(([t]) => t === type);
      expect(match?.[1]).toBe(fn);
    }
  });
});
