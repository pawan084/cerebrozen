import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The admin client keeps its token in module state, so each test takes a fresh
// copy rather than trying to unwind it.
type AdminApi = typeof import("../../apps/admin/lib/api");
async function freshApi(): Promise<AdminApi> {
  vi.resetModules();
  return import("../../apps/admin/lib/api");
}

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

// Everything an operator is told about a failure comes from `kind`. The admin
// shell renders one of three states from it — "Your session ended", "We can't
// reach the server", "The server had trouble" — so a misclassification is not
// cosmetic: it sends someone to fix the wrong thing, at the moment they are
// most likely triaging a safety queue.
describe("signing in", () => {
  it("calls a dead backend offline, never bad credentials", async () => {
    // An operator retyping a correct password is the worst possible dead end.
    const { login, ApiError } = await freshApi();
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    const err = await login("admin@cerebro.app", "admin12345").catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.kind).toBe("offline");
    expect(err.message).toContain("couldn't reach");
  });

  it.each([400, 401, 403])("calls %i bad credentials", async (status) => {
    const { login } = await freshApi();
    fetchMock.mockResolvedValue(response(status, {}));
    const err = await login("a@b.c", "nope").catch((e) => e);
    expect(err.kind).toBe("unauthorized");
    expect(err.message).toMatch(/don't match an admin account/);
  });

  it("separates a 500 from a refused password", async () => {
    const { login } = await freshApi();
    fetchMock.mockResolvedValue(response(500, {}));
    const err = await login("a@b.c", "right-password").catch((e) => e);
    expect(err.kind).toBe("server");
    expect(err.status).toBe(500);
    expect(err.message).not.toMatch(/don't match/);
  });

  it("sends credentials so the refresh cookie is actually stored", async () => {
    // Without credentials:"include" the browser silently drops the httpOnly
    // Set-Cookie on a cross-origin response, and every reload lands the
    // operator back on sign-in with no error to explain it.
    const { login } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "t-1" }));
    await login("admin@cerebro.app", "admin12345");
    expect(fetchMock.mock.calls[0][1].credentials).toBe("include");
  });

  it("posts form-encoded, as the OAuth2 password flow requires", async () => {
    const { login } = await freshApi();
    fetchMock.mockResolvedValue(response(200, { access_token: "t-1" }));
    await login("admin@cerebro.app", "admin12345");
    const init = fetchMock.mock.calls[0][1];
    expect(init.headers["Content-Type"]).toBe("application/x-www-form-urlencoded");
    expect(String(init.body)).toContain("username=admin%40cerebro.app");
  });
});

describe("an authenticated call", () => {
  it("does not sign the operator out because the network blinked", async () => {
    // An unreachable API is not an expired session. Clearing the token here
    // would drop someone out of a triage queue for a dropped packet.
    const { api, setToken, getToken } = await freshApi();
    setToken("t-1");
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    const err = await api("/admin/overview").catch((e) => e);
    expect(err.kind).toBe("offline");
    expect(getToken()).toBe("t-1");
  });

  it("classifies a 5xx as the server's problem, with the status attached", async () => {
    const { api, setToken } = await freshApi();
    setToken("t-1");
    fetchMock.mockResolvedValue(response(503, {}));
    const err = await api("/admin/overview").catch((e) => e);
    expect(err.kind).toBe("server");
    expect(err.status).toBe(503);
    // The shape is load-bearing — callers match on it.
    expect(err.message).toBe("Request failed: 503");
  });

  it("classifies a 4xx as the request's problem", async () => {
    const { api, setToken } = await freshApi();
    setToken("t-1");
    fetchMock.mockResolvedValue(response(413, {}));
    const err = await api("/admin/media").catch((e) => e);
    expect(err.kind).toBe("request");
    expect(err.status).toBe(413);
  });

  it("attaches the bearer token when it has one", async () => {
    const { api, setToken } = await freshApi();
    setToken("t-1");
    fetchMock.mockResolvedValue(response(200, {}));
    await api("/admin/overview");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer t-1");
  });

  it("returns undefined for a 204 rather than parsing an empty body", async () => {
    const { api, setToken } = await freshApi();
    setToken("t-1");
    fetchMock.mockResolvedValue(response(204));
    await expect(api("/admin/content/1")).resolves.toBeUndefined();
  });

  it("clears the token once a 401 survives the refresh attempt", async () => {
    const { api, setToken, getToken } = await freshApi();
    setToken("t-1");
    fetchMock.mockResolvedValue(response(401, {}));
    const err = await api("/admin/overview").catch((e) => e);
    expect(err.kind).toBe("unauthorized");
    expect(getToken()).toBeNull();
  });
});

describe("the token", () => {
  it("round-trips and clears", async () => {
    const { setToken, getToken, clearToken } = await freshApi();
    expect(getToken()).toBeNull();
    setToken("t-1");
    expect(getToken()).toBe("t-1");
    clearToken();
    expect(getToken()).toBeNull();
  });
});
