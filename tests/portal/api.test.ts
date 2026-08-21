import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Module state holds the access token in memory on purpose, so each case takes
// a fresh copy of the module.
type PortalApi = typeof import("../../apps/portal/lib/api");
async function freshApi(): Promise<PortalApi> {
  vi.resetModules();
  return import("../../apps/portal/lib/api");
}

const PORTAL_REFRESH_KEY = "cerebro_portal_refresh";

function response(status: number, body?: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
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

afterEach(() => vi.unstubAllGlobals());

describe("the portal session is not the member session", () => {
  // The reason this client exists separately at all. An administrator is very
  // likely to be a member too; sharing a storage key would mean signing out of
  // the portal signed you out of your own wellbeing account in the next tab —
  // or, worse, that opening the portal silently adopted your member session.
  it("uses a different storage key from apps/app", () => {
    const portalSrc = readFileSync(resolve(__dirname, "../../apps/portal/lib/api.ts"), "utf8");
    const appSrc = readFileSync(resolve(__dirname, "../../apps/app/lib/api.ts"), "utf8");
    const portalKey = portalSrc.match(/const REFRESH_KEY = "([^"]+)"/)?.[1];
    const appKey = appSrc.match(/const REFRESH_KEY = "([^"]+)"/)?.[1];
    expect(portalKey).toBeTruthy();
    expect(appKey).toBeTruthy();
    expect(portalKey, "the portal and the member app share a refresh key").not.toBe(appKey);
  });

  it("leaves the member session alone when the portal signs out", async () => {
    const { signOut } = await freshApi();
    window.localStorage.setItem("cerebro_app_refresh", "members-token");
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "portal-token");
    fetchMock.mockResolvedValue(response(200, {}));

    await signOut();

    expect(window.localStorage.getItem(PORTAL_REFRESH_KEY)).toBeNull();
    expect(window.localStorage.getItem("cerebro_app_refresh")).toBe("members-token");
  });
});

describe("signing in", () => {
  it("posts form-encoded with `username`, as OAuth2 requires", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "a", refresh_token: "r" }));
    await signIn("admin@acme.test", "secret");
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Content-Type"]).toBe("application/x-www-form-urlencoded");
    expect(String(init.body)).toContain("username=admin%40acme.test");
  });

  it("stores the session on success", async () => {
    const { signIn, hasSession } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "a", refresh_token: "r-1" }));
    await signIn("admin@acme.test", "secret");
    expect(hasSession()).toBe(true);
    expect(window.localStorage.getItem(PORTAL_REFRESH_KEY)).toBe("r-1");
  });

  it.each([400, 401])("says the credentials are wrong for %i, and only then", async (status) => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(status, {}));
    await expect(signIn("a@b.c", "nope")).rejects.toThrow("Invalid email or password.");
  });

  it("does not blame the password for a 500", async () => {
    // The apps/app twin of this was register D22: every non-OK response mapped
    // to "Invalid email or password.", so an outage told people their password
    // was wrong and invited a pointless reset.
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(503, {}));
    const err = await signIn("a@b.c", "right").catch((e) => e);
    expect(err.message).toContain("Nothing is wrong with your account");
  });

  it("explains a rate limit as a rate limit", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(429, {}));
    await expect(signIn("a@b.c", "x")).rejects.toThrow(/wait a minute/);
  });

  it("prefers the server's own detail for anything else", async () => {
    const { signIn } = await freshApi();
    fetchMock.mockResolvedValue(response(418, { detail: "This account is disabled." }));
    await expect(signIn("a@b.c", "x")).rejects.toThrow("This account is disabled.");
  });
});

describe("403 is an answer, not an expiry", () => {
  it("raises NotAnOrgAdminError and keeps the session", async () => {
    // The honest answer for a signed-in user who administers no organisation,
    // or an analyst attempting a write. Treating it as expiry would sign them
    // out instead of explaining — the same mistake register D1 records on the
    // member client.
    const { api, NotAnOrgAdminError, hasSession } = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock
      .mockResolvedValueOnce(response(200, { access_token: "a", refresh_token: "r-2" }))
      .mockResolvedValueOnce(response(403, { detail: "You administer no organisation." }));

    const err: any = await api("/org/summary").catch((e) => e);
    expect(err).toBeInstanceOf(NotAnOrgAdminError);
    expect(err.message).toBe("You administer no organisation.");
    expect(hasSession()).toBe(true);
  });

  it("still carries a status line when the 403 body is not JSON", async () => {
    const { api, NotAnOrgAdminError } = await freshApi();
    fetchMock.mockResolvedValue(response(403));
    const err: any = await api("/org/summary").catch((e) => e);
    expect(err).toBeInstanceOf(NotAnOrgAdminError);
    expect(err.message).toBe("Request failed: 403");
  });
});

describe("401 rotates once, then gives up", () => {
  it("retries the original request after a successful rotation", async () => {
    const { api } = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock
      .mockResolvedValueOnce(response(200, { access_token: "a-1", refresh_token: "r-2" })) // load refresh
      .mockResolvedValueOnce(response(401, {}))                                            // the call
      .mockResolvedValueOnce(response(200, { access_token: "a-2", refresh_token: "r-3" })) // rotation
      .mockResolvedValueOnce(response(200, { ok: true }));                                 // retry
    await expect(api("/org/summary")).resolves.toEqual({ ok: true });
  });

  it("clears the session when the rotation also fails", async () => {
    const { api, hasSession } = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock
      .mockResolvedValueOnce(response(200, { access_token: "a-1", refresh_token: "r-2" }))
      .mockResolvedValueOnce(response(401, {}))
      .mockResolvedValueOnce(response(401, {}));
    await expect(api("/org/summary")).rejects.toThrow("unauthorized");
    expect(hasSession()).toBe(false);
  });

  it("rotates ONCE even when several widgets load at the same time", async () => {
    // Three widgets loading at once must not race three refreshes, two of
    // which would then be using a rotated-away token — and a rotated refresh
    // token is single-use, so the losers would sign the operator out.
    const { api } = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/auth/refresh")
        ? response(200, { access_token: "a-1", refresh_token: "r-2" })
        : response(200, { ok: true }),
    );

    await Promise.all([api("/org/summary"), api("/org/seats"), api("/org/reports")]);

    const refreshes = fetchMock.mock.calls.filter(([u]) => String(u).endsWith("/auth/refresh"));
    expect(refreshes, "the portal rotated its refresh token more than once").toHaveLength(1);
  });
});

describe("ordinary calls", () => {
  async function signedIn() {
    const mod = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock.mockResolvedValueOnce(response(200, { access_token: "a-1", refresh_token: "r-2" }));
    return mod;
  }

  it("attaches the bearer token", async () => {
    const { api } = await signedIn();
    fetchMock.mockResolvedValueOnce(response(200, {}));
    await api("/org/summary");
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe("Bearer a-1");
  });

  it("returns undefined for a 204 rather than parsing an empty body", async () => {
    const { api } = await signedIn();
    fetchMock.mockResolvedValueOnce(response(204));
    await expect(api("/org/members/1")).resolves.toBeUndefined();
  });

  it("surfaces the server's detail on a plain failure", async () => {
    const { api } = await signedIn();
    fetchMock.mockResolvedValueOnce(response(400, { detail: "Seats exceeded." }));
    await expect(api("/org/seats")).rejects.toThrow("Seats exceeded.");
  });

  it("sends no cookies, so there is no CSRF surface", async () => {
    const { api } = await signedIn();
    fetchMock.mockResolvedValueOnce(response(200, {}));
    await api("/org/summary");
    expect(fetchMock.mock.calls[1][1].credentials).toBeUndefined();
  });
});

describe("signing out", () => {
  it("drops the local session even when revoking server-side fails", async () => {
    // Best effort on the server; the local session goes either way. Otherwise
    // an operator on a shared machine cannot get out.
    const { signOut, hasSession } = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock.mockImplementation(() => {
      throw new TypeError("Failed to fetch");
    });
    await expect(signOut()).resolves.toBeUndefined();
    expect(hasSession()).toBe(false);
  });
});

describe("refusing a CSV before it leaves the machine", () => {
  // Duplicated from the server on purpose. The server's copy is the one that
  // decides, but this one lets the portal refuse a file WITHOUT UPLOADING IT —
  // an HR export carrying a `diagnosis` column should never leave the
  // administrator's laptop, and a check that runs after the upload has already
  // failed at exactly that.
  it("accepts the four eligibility columns", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("email,external_ref,access_start,access_end\na@b.c,1,,")).toEqual([]);
  });

  it("names a column that does not belong", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("email,diagnosis\na@b.c,anxiety")).toEqual(["diagnosis"]);
  });

  it("names every extra column, not just the first", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("email,diagnosis,salary,manager")).toEqual([
      "diagnosis", "salary", "manager",
    ]);
  });

  it("is forgiving about form — case, spaces and padding", async () => {
    // "Access Start" is what a spreadsheet exports; refusing it would send an
    // administrator hunting for a problem that is not there.
    const { unknownColumns } = await freshApi();
    expect(unknownColumns(" Email , Access Start ,ACCESS_END")).toEqual([]);
  });

  it("survives the BOM Excel puts at the front of a CSV", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("\ufeffemail,external_ref")).toEqual([]);
  });

  it("reads only the header row", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("email\ndiagnosis\nsalary")).toEqual([]);
  });

  it("handles CRLF, which is what Windows exports", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("email,diagnosis\r\na@b.c,x")).toEqual(["diagnosis"]);
  });

  it("ignores an empty trailing column rather than reporting it", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("email,")).toEqual([]);
  });

  it("says nothing about an empty file", async () => {
    const { unknownColumns } = await freshApi();
    expect(unknownColumns("")).toEqual([]);
  });
});

describe("the launch checklist", () => {
  async function withServer(org: Record<string, unknown>) {
    const mod = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock.mockImplementation(async (url: string) => {
      const u = String(url);
      if (u.endsWith("/auth/refresh")) return response(200, { access_token: "a", refresh_token: "r" });
      if (u.endsWith("/org")) return response(200, org);
      return response(200, []);
    });
    return mod;
  }

  const completeOrg = {
    legal_entity: "Acme Ltd",
    primary_contact_email: "ops@acme.test",
    privacy_contact_email: "privacy@acme.test",
    small_cell_suppression: true,
    reporting_threshold: 8,
    region: "IN",
  };

  it("reports the threshold and region the org actually has", async () => {
    const { getLaunchState } = await withServer(completeOrg);
    const state = await getLaunchState();
    expect(state.threshold).toBe(8);
    expect(state.region).toBe("IN");
  });

  it("ticks the profile step only when every contact is filled in", async () => {
    const { getLaunchState } = await withServer(completeOrg);
    const done = (await getLaunchState()).steps.find((s) => s.key === "profile")!.done;
    expect(done).toBe(true);
  });

  it("leaves the profile step unticked when a contact is missing", async () => {
    // The privacy contact is the one a regulator or a member would use. A
    // checklist that ticked itself without one would be telling the
    // administrator they are ready when they are not.
    const { getLaunchState } = await withServer({ ...completeOrg, privacy_contact_email: "" });
    const done = (await getLaunchState()).steps.find((s) => s.key === "profile")!.done;
    expect(done).toBe(false);
  });

  it("treats suppression being OFF as an unfinished privacy step", async () => {
    const { getLaunchState } = await withServer({ ...completeOrg, small_cell_suppression: false });
    const done = (await getLaunchState()).steps.find((s) => s.key === "privacy")!.done;
    expect(done).toBe(false);
  });

  it("gives every step a label and a link to somewhere", async () => {
    const { getLaunchState } = await withServer(completeOrg);
    for (const step of (await getLaunchState()).steps) {
      expect(step.label.trim()).not.toBe("");
      expect(step.href.startsWith("/")).toBe(true);
    }
  });
});

describe("the endpoint wrappers", () => {
  async function signedIn() {
    const mod = await freshApi();
    window.localStorage.setItem(PORTAL_REFRESH_KEY, "r-1");
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/auth/refresh")
        ? response(200, { access_token: "a", refresh_token: "r" })
        : response(200, { ok: true }),
    );
    return mod;
  }

  it("each hits the path its name promises", async () => {
    const mod: any = await signedIn();
    const cases: [string, string][] = [
      ["getOrg", "/org"],
      ["getSummary", "/org/summary"],
      ["getGroups", "/org/groups"],
      ["getGroupTotals", "/org/groups/totals"],
      ["getMembers", "/org/members"],
      ["getProgrammes", "/org/programmes"],
      ["getAdmins", "/org/admins"],
      ["getAudit", "/org/audit"],
    ];
    for (const [fn, path] of cases) {
      fetchMock.mockClear();
      await mod[fn]();
      const called = fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => !u.endsWith("/auth/refresh"));
      expect(called.some((u) => u.endsWith(path)), `${fn} did not call ${path}`).toBe(true);
    }
  });

  it("sends writes with the right method and body", async () => {
    const mod: any = await signedIn();
    fetchMock.mockClear();
    await mod.patchOrg({ reporting_threshold: 10 });
    const write = fetchMock.mock.calls.find(([u]) => String(u).endsWith("/org"))!;
    expect(write[1].method).toBe("PATCH");
    expect(JSON.parse(write[1].body)).toEqual({ reporting_threshold: 10 });
  });

  it("ends a membership by id, with DELETE", async () => {
    const mod: any = await signedIn();
    fetchMock.mockClear();
    await mod.endMembership("m-1");
    const call = fetchMock.mock.calls.find(([u]) => String(u).includes("/org/members/m-1"))!;
    expect(call[1].method).toBe("DELETE");
  });
});
