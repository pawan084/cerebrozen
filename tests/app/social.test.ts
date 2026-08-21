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
