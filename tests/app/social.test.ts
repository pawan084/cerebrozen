import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type Social = typeof import("../../apps/app/lib/social");
async function freshSocial(env: Record<string, string> = {}): Promise<Social> {
  vi.resetModules();
  for (const [k, v] of Object.entries(env)) vi.stubEnv(k, v);
  return import("../../apps/app/lib/social");
}

function scriptsOnPage(): string[] {
  return [...document.head.querySelectorAll("script")].map((s) => s.getAttribute("src") ?? "");
}

beforeEach(() => {
  document.head.innerHTML = "";
});

afterEach(() => {
  vi.unstubAllEnvs();
});

// Parity with iOS: the buttons are always shown but stay inert until the owner
// configures the provider client ids. The half worth testing is the half that
// is invisible when it works — when unconfigured, NO external SDK is loaded,
// which is what keeps the default CSP clean. A regression here would quietly
// start pulling scripts from Google and Apple onto the sign-in page of a
// mental-health product for every visitor, configured or not.
describe("when nothing is configured", () => {
  it("reports both providers as unconfigured", async () => {
    const social = await freshSocial({
      NEXT_PUBLIC_GOOGLE_CLIENT_ID: "",
      NEXT_PUBLIC_APPLE_SERVICES_ID: "",
      NEXT_PUBLIC_APPLE_REDIRECT_URI: "",
    });
    expect(social.googleConfigured()).toBe(false);
    expect(social.appleConfigured()).toBe(false);
  });

  it("refuses Google with an error the button can explain", async () => {
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "" });
    const err = await social.googleIdToken().catch((e) => e);
    expect(err).toBeInstanceOf(social.NotConfiguredError);
    // The message is shown to a person, so it has to name the way forward.
    expect(err.message).toContain("use email below");
  });

  it("refuses Apple the same way", async () => {
    const social = await freshSocial({
      NEXT_PUBLIC_APPLE_SERVICES_ID: "",
      NEXT_PUBLIC_APPLE_REDIRECT_URI: "",
    });
    const err = await social.appleIdentityToken().catch((e) => e);
    expect(err).toBeInstanceOf(social.NotConfiguredError);
  });

  it("loads NO external script — the CSP stays clean", async () => {
    const social = await freshSocial({
      NEXT_PUBLIC_GOOGLE_CLIENT_ID: "",
      NEXT_PUBLIC_APPLE_SERVICES_ID: "",
      NEXT_PUBLIC_APPLE_REDIRECT_URI: "",
    });
    await social.googleIdToken().catch(() => {});
    await social.appleIdentityToken().catch(() => {});
    expect(scriptsOnPage()).toEqual([]);
  });
});

describe("Apple needs BOTH halves before it counts as configured", () => {
  it("is not configured with a services id and no redirect", async () => {
    // A Services ID without a registered https redirect fails at Apple's end
    // with an opaque error. Better to stay honestly inert.
    const social = await freshSocial({
      NEXT_PUBLIC_APPLE_SERVICES_ID: "in.cerebrozen.web",
      NEXT_PUBLIC_APPLE_REDIRECT_URI: "",
    });
    expect(social.appleConfigured()).toBe(false);
    await expect(social.appleIdentityToken()).rejects.toBeInstanceOf(social.NotConfiguredError);
  });

  it("is not configured with a redirect and no services id", async () => {
    const social = await freshSocial({
      NEXT_PUBLIC_APPLE_SERVICES_ID: "",
      NEXT_PUBLIC_APPLE_REDIRECT_URI: "https://app.cerebrozen.in/auth/apple",
    });
    expect(social.appleConfigured()).toBe(false);
  });

  it("is configured only when both are present", async () => {
    const social = await freshSocial({
      NEXT_PUBLIC_APPLE_SERVICES_ID: "in.cerebrozen.web",
      NEXT_PUBLIC_APPLE_REDIRECT_URI: "https://app.cerebrozen.in/auth/apple",
    });
    expect(social.appleConfigured()).toBe(true);
  });
});

describe("when Google IS configured", () => {
  it("loads the SDK and resolves with the credential it hands back", async () => {
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    expect(social.googleConfigured()).toBe(true);

    // jsdom does not fetch scripts, so stand in for the network: fire onload
    // as soon as the element is appended, then play the part of the SDK.
    const appended = vi.spyOn(document.head, "appendChild").mockImplementation(((el: any) => {
      queueMicrotask(() => el.onload?.());
      return el;
    }) as any);
    (window as any).google = {
      accounts: {
        id: {
          initialize: ({ callback }: any) => {
            (window as any).__cb = callback;
          },
          prompt: () => (window as any).__cb({ credential: "jwt-from-google" }),
        },
      },
    };

    await expect(social.googleIdToken()).resolves.toBe("jwt-from-google");
    appended.mockRestore();
  });

  it("rejects rather than hanging when Google returns no credential", async () => {
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = vi.spyOn(document.head, "appendChild").mockImplementation(((el: any) => {
      queueMicrotask(() => el.onload?.());
      return el;
    }) as any);
    (window as any).google = {
      accounts: {
        id: {
          initialize: ({ callback }: any) => {
            (window as any).__cb = callback;
          },
          prompt: () => (window as any).__cb({}),
        },
      },
    };

    // A promise that never settles would leave the button spinning forever,
    // with no way back to the email form.
    await expect(social.googleIdToken()).rejects.toThrow(/no credential/);
    appended.mockRestore();
  });

  it("rejects when the person dismisses the prompt", async () => {
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = vi.spyOn(document.head, "appendChild").mockImplementation(((el: any) => {
      queueMicrotask(() => el.onload?.());
      return el;
    }) as any);
    (window as any).google = {
      accounts: {
        id: {
          initialize: () => {},
          prompt: (cb: any) => cb({ isNotDisplayed: () => true }),
        },
      },
    };

    await expect(social.googleIdToken()).rejects.toThrow(/dismissed/);
    appended.mockRestore();
  });
});

/** jsdom does not fetch scripts. Stand in for the network by firing the
 *  element's own handler as soon as it is appended. */
function serveScript(outcome: "load" | "error" = "load") {
  return vi.spyOn(document.head, "appendChild").mockImplementation(((el: any) => {
    queueMicrotask(() => (outcome === "load" ? el.onload?.() : el.onerror?.()));
    document.head.append.call(document.head, el);
    return el;
  }) as any);
}

const APPLE_SDK =
  "https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js";
const GOOGLE_SDK = "https://accounts.google.com/gsi/client";

async function configuredApple(): Promise<Social> {
  return freshSocial({
    NEXT_PUBLIC_APPLE_SERVICES_ID: "in.cerebrozen.web",
    NEXT_PUBLIC_APPLE_REDIRECT_URI: "https://app.cerebrozen.in/auth/apple",
  });
}

function stubAppleID(signIn: () => Promise<any> | any) {
  const init = vi.fn();
  (window as any).AppleID = { auth: { init, signIn } };
  return init;
}

describe("when Apple IS configured", () => {
  it("loads Apple's own SDK, and only that", async () => {
    // The URL is part of the CSP surface: anything else here is a script from
    // an unexpected origin on the sign-in page of a mental-health product.
    const social = await configuredApple();
    const appended = serveScript();
    stubAppleID(async () => ({ authorization: { id_token: "apple-jwt" } }));

    await social.appleIdentityToken();
    expect(scriptsOnPage()).toEqual([APPLE_SDK]);
    appended.mockRestore();
  });

  it("initialises with the configured ids, a popup, and the scope it needs", async () => {
    // usePopup matters: without it Apple full-page-redirects away and whatever
    // the person had typed into the form below is gone.
    const social = await configuredApple();
    const appended = serveScript();
    const init = stubAppleID(async () => ({ authorization: { id_token: "apple-jwt" } }));

    await social.appleIdentityToken();
    expect(init).toHaveBeenCalledWith({
      clientId: "in.cerebrozen.web",
      scope: "name email",
      redirectURI: "https://app.cerebrozen.in/auth/apple",
      usePopup: true,
    });
    appended.mockRestore();
  });

  it("hands back the identity token the backend verifies", async () => {
    const social = await configuredApple();
    const appended = serveScript();
    stubAppleID(async () => ({
      authorization: { id_token: "apple-jwt" },
      user: { name: { firstName: "Ananya", lastName: "Kapoor" } },
    }));

    await expect(social.appleIdentityToken()).resolves.toEqual({
      token: "apple-jwt",
      name: "Ananya Kapoor",
    });
    appended.mockRestore();
  });

  it("returns an empty name rather than 'undefined undefined'", async () => {
    // Apple sends the name ONLY on the first authorization. Every subsequent
    // sign-in has no user object at all, and a template with holes in it would
    // put that string on the account — visible in the app's own header.
    const social = await configuredApple();
    const appended = serveScript();
    stubAppleID(async () => ({ authorization: { id_token: "apple-jwt" } }));

    await expect(social.appleIdentityToken()).resolves.toEqual({
      token: "apple-jwt",
      name: "",
    });
    appended.mockRestore();
  });

  it("leaves no trailing space when only half a name comes back", async () => {
    const social = await configuredApple();
    const appended = serveScript();
    stubAppleID(async () => ({
      authorization: { id_token: "apple-jwt" },
      user: { name: { firstName: "Ananya" } },
    }));

    await expect(social.appleIdentityToken()).resolves.toEqual({
      token: "apple-jwt",
      name: "Ananya",
    });
    appended.mockRestore();
  });

  it("rejects rather than signing in with no token at all", async () => {
    const social = await configuredApple();
    const appended = serveScript();
    stubAppleID(async () => ({ authorization: {} }));

    await expect(social.appleIdentityToken()).rejects.toThrow(/no identity token/);
    appended.mockRestore();
  });

  it("surfaces a cancelled popup instead of hanging on it", async () => {
    // Apple rejects when the person closes the popup. Swallowing that would
    // leave the button disabled with no way back to the email form.
    const social = await configuredApple();
    const appended = serveScript();
    stubAppleID(async () => {
      throw new Error("popup_closed_by_user");
    });

    await expect(social.appleIdentityToken()).rejects.toThrow(/popup_closed_by_user/);
    appended.mockRestore();
  });
});

describe("loading a provider's SDK", () => {
  it("rejects when the script cannot be fetched at all", async () => {
    // Offline, or blocked by an extension or a corporate proxy. Without the
    // onerror handler the promise never settles and the button spins forever.
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = serveScript("error");

    await expect(social.googleIdToken()).rejects.toThrow(`Failed to load ${GOOGLE_SDK}`);
    appended.mockRestore();
  });

  it("does not stack a second copy on a second attempt", async () => {
    // Two sign-in attempts in one visit is ordinary — a dismissed prompt, then
    // another press. Re-appending the SDK re-runs it against a page that has
    // already initialised it.
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = serveScript();
    (window as any).google = {
      accounts: {
        id: {
          initialize: ({ callback }: any) => {
            (window as any).__cb = callback;
          },
          prompt: () => (window as any).__cb({ credential: "jwt-from-google" }),
        },
      },
    };

    await social.googleIdToken();
    await social.googleIdToken();
    expect(scriptsOnPage()).toEqual([GOOGLE_SDK]);
    appended.mockRestore();
  });
});

describe("when Google's SDK is present but broken", () => {
  it("rejects instead of leaving the button spinning", async () => {
    // The SDK is a third-party script that can change under us. Anything it
    // throws synchronously inside initialize/prompt has to become a rejection,
    // or the promise never settles and there is no way back to the email form.
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = serveScript();
    (window as any).google = {
      accounts: {
        id: {
          initialize: () => {
            throw new Error("gsi: invalid client id");
          },
          prompt: () => {},
        },
      },
    };

    await expect(social.googleIdToken()).rejects.toThrow(/invalid client id/);
    appended.mockRestore();
  });

  it("rejects when the prompt is skipped rather than shown", async () => {
    // The other half of the dismissal branch: isSkippedMoment fires when the
    // browser suppresses the One Tap prompt (a previous dismissal, or cookies
    // blocked). Same dead end for the user, so the same rejection.
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = serveScript();
    (window as any).google = {
      accounts: {
        id: {
          initialize: () => {},
          prompt: (cb: any) => cb({ isNotDisplayed: () => false, isSkippedMoment: () => true }),
        },
      },
    };

    await expect(social.googleIdToken()).rejects.toThrow(/dismissed/);
    appended.mockRestore();
  });

  it("stays pending — not rejected — while the prompt is still on screen", async () => {
    // A notification that is neither skipped nor undisplayed is the prompt
    // simply being shown. Rejecting there would kill a sign-in mid-flow.
    const social = await freshSocial({ NEXT_PUBLIC_GOOGLE_CLIENT_ID: "client-123" });
    const appended = serveScript();
    (window as any).google = {
      accounts: {
        id: {
          initialize: () => {},
          prompt: (cb: any) => cb({ isNotDisplayed: () => false, isSkippedMoment: () => false }),
        },
      },
    };

    const settled = vi.fn();
    social.googleIdToken().then(settled, settled);
    await new Promise((r) => setTimeout(r, 10));
    expect(settled).not.toHaveBeenCalled();
    appended.mockRestore();
  });
});
