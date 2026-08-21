import { describe, expect, it } from "vitest";

import { currentPath, safeNext } from "../../apps/app/lib/nextPath";

// `?next=` is attacker-controlled: the landing deep-links into app screens, so
// a signed-out visitor carries a return path through sign-in in the URL. An
// open redirect here is a phishing primitive — the victim sees a genuine
// cerebrozen.in sign-in, completes it, and lands on someone else's page.
//
// e2e covers exactly one of these ("refuses an off-origin next and falls back
// to Home"). A redirect allow-list is where one missed shape is the whole bug,
// and enumerating shapes is what a unit test is for.
describe("the ?next= allow-list", () => {
  it.each([
    ["/sleep", "a plain same-origin path"],
    ["/insights/trends", "a nested one"],
    ["/sleep?tab=insights", "one carrying a query"],
    ["/journal#latest", "one carrying a hash"],
  ])("allows %s — %s", (raw) => {
    expect(safeNext(raw)).toBe(raw);
  });

  it.each([
    ["//evil.com", "protocol-relative — a URL wearing a path's clothes"],
    ["//evil.com/path", "the same with a path on the end"],
    ["https://evil.com", "an absolute URL"],
    ["http://evil.com", "the insecure twin"],
    ["\\\\evil.com", "backslashes, which browsers normalise to /"],
    ["/\\evil.com", "one slash and one backslash — normalises to //evil.com"],
    ["javascript:alert(1)", "a script URL"],
    ["data:text/html,<script>", "a data URL"],
    ["evil.com", "no leading slash at all"],
    ["", "empty"],
  ])("refuses %s — %s", (raw) => {
    expect(safeNext(raw)).toBeNull();
  });

  it("refuses null and undefined without throwing", () => {
    // The real caller is `searchParams.get("next")`, which returns null far
    // more often than it returns anything.
    expect(safeNext(null)).toBeNull();
    expect(safeNext(undefined)).toBeNull();
  });

  it.each(["/signin", "/signup", "/onboarding"])(
    "refuses %s, which would only loop",
    (route) => {
      expect(safeNext(route)).toBeNull();
    },
  );

  it("still refuses an auth route dressed up with a query", () => {
    // The loop check runs on the path alone, so a query string must not be a
    // way to smuggle one past it.
    expect(safeNext("/signin?next=/home")).toBeNull();
    expect(safeNext("/onboarding#step2")).toBeNull();
  });

  it("allows a path that merely starts with an auth route's name", () => {
    // The check is exact-match on purpose: /signup-complete is a real
    // destination, not the signup screen, and a startsWith() would have
    // silently sent that person to /home instead.
    expect(safeNext("/signup-complete")).toBe("/signup-complete");
  });
});

describe("where the visitor was trying to go", () => {
  it("reports the current path with its query, for the sign-in link", () => {
    window.history.replaceState({}, "", "/sleep?tab=insights");
    expect(currentPath()).toBe("/sleep?tab=insights");
  });

  it("round-trips through the allow-list", () => {
    // The two halves are used together — currentPath() produces what safeNext()
    // later validates — so a shape one emits and the other rejects would send
    // people to /home for no reason.
    window.history.replaceState({}, "", "/games/imagery?from=home");
    expect(safeNext(currentPath())).toBe("/games/imagery?from=home");
  });
});
