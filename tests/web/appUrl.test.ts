import { describe, expect, it } from "vitest";

import { APP_URL, appHref } from "../../apps/web/lib/appUrl";
import { getThemeMode, setThemeMode } from "../../apps/app/lib/theme";

describe("deep links from the landing into the app", () => {
  it("returns the base for the root", () => {
    expect(appHref("/")).toBe(APP_URL);
    expect(appHref()).toBe(APP_URL);
  });

  it("joins a path onto the base", () => {
    expect(appHref("/sleep")).toBe(`${APP_URL}/sleep`);
  });

  it("tolerates a path without its leading slash", () => {
    // Callers write these by hand in JSX; a missing slash producing
    // "http://app.cerebrozen.insleep" would be a broken link that still looks
    // plausible in the source.
    expect(appHref("sleep")).toBe(`${APP_URL}/sleep`);
  });

  it("does not double the slash when the base carries one", () => {
    // NEXT_PUBLIC_APP_URL is set as a Docker build ARG, and a trailing slash in
    // an env var is the single easiest thing to get wrong.
    expect(appHref("/sleep")).not.toContain("//sleep");
  });

  it("keeps a query string intact", () => {
    expect(appHref("/signin?next=/sleep")).toBe(`${APP_URL}/signin?next=/sleep`);
  });

  it("produces an absolute URL, since it crosses an origin", () => {
    expect(appHref("/home")).toMatch(/^https?:\/\//);
  });
});

describe("theme preference", () => {
  // A display preference that belongs to the DEVICE, not the account — which
  // is why lib/api.ts deliberately leaves it out of PERSONAL_KEYS. Getting
  // this wrong relights a dark room at 2am for someone who signed out.
  it("defaults to following the system", () => {
    window.localStorage.clear();
    expect(getThemeMode()).toBe("system");
  });

  it("remembers an explicit choice and stamps the document", () => {
    setThemeMode("night");
    expect(getThemeMode()).toBe("night");
    expect(document.documentElement.dataset.theme).toBe("night");

    setThemeMode("dawn");
    expect(getThemeMode()).toBe("dawn");
    expect(document.documentElement.dataset.theme).toBe("dawn");
  });

  it("clears the stamp when handed back to the system", () => {
    // The pre-paint script in the root layout reads the same key. Leaving
    // data-theme behind would pin the page to a theme the setting says is off.
    setThemeMode("night");
    setThemeMode("system");
    expect(window.localStorage.getItem("theme_mode")).toBeNull();
    expect(document.documentElement.dataset.theme).toBeUndefined();
  });

  it("ignores a junk value rather than rendering an unknown theme", () => {
    window.localStorage.setItem("theme_mode", "midnight-mauve");
    expect(getThemeMode()).toBe("system");
  });
});
