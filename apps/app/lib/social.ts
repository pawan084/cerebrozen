// Sign in with Apple / Google for the web app.
//
// Parity with iOS: the buttons are always shown, but stay inert until the owner
// configures the provider client ids. When unconfigured we load NO external SDK
// (keeps the default CSP clean) and surface an honest notice; when configured we
// dynamically load the provider script and complete the flow, handing the
// resulting token to lib/api's signInApple / signInGoogle.

export const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "";
export const APPLE_SERVICES_ID = process.env.NEXT_PUBLIC_APPLE_SERVICES_ID || "";
// Where Apple posts back to (must be an https URL registered on the Services ID).
export const APPLE_REDIRECT_URI = process.env.NEXT_PUBLIC_APPLE_REDIRECT_URI || "";

export const googleConfigured = () => GOOGLE_CLIENT_ID.length > 0;
export const appleConfigured = () => APPLE_SERVICES_ID.length > 0 && APPLE_REDIRECT_URI.length > 0;

/** A provider isn't wired yet — thrown so callers can show the honest notice. */
export class NotConfiguredError extends Error {
  constructor(provider: string) {
    super(`${provider} sign-in isn't set up yet — use email below.`);
    this.name = "NotConfiguredError";
  }
}

const loaded = new Set<string>();
function loadScript(src: string): Promise<void> {
  if (loaded.has(src)) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const el = document.createElement("script");
    el.src = src;
    el.async = true;
    el.onload = () => {
      loaded.add(src);
      resolve();
    };
    el.onerror = () => reject(new Error(`Failed to load ${src}`));
    document.head.appendChild(el);
  });
}

/** Runs Google Identity Services and resolves with the ID token (a JWT the
 * backend verifies at /auth/google). Rejects with NotConfiguredError when the
 * client id is absent. */
export async function googleIdToken(): Promise<string> {
  if (!googleConfigured()) throw new NotConfiguredError("Google");
  await loadScript("https://accounts.google.com/gsi/client");
  const google = (window as any).google;
  return new Promise<string>((resolve, reject) => {
    try {
      google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: (resp: any) => {
          if (resp?.credential) resolve(resp.credential as string);
          else reject(new Error("Google returned no credential."));
        },
      });
      google.accounts.id.prompt((notification: any) => {
        if (notification?.isNotDisplayed?.() || notification?.isSkippedMoment?.()) {
          reject(new Error("Google sign-in was dismissed."));
        }
      });
    } catch (e) {
      reject(e as Error);
    }
  });
}

/** Runs Sign in with Apple (popup) and resolves with the identity token that
 * the backend verifies at /auth/apple. */
export async function appleIdentityToken(): Promise<{ token: string; name: string }> {
  if (!appleConfigured()) throw new NotConfiguredError("Apple");
  await loadScript(
    "https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js",
  );
  const AppleID = (window as any).AppleID;
  AppleID.auth.init({
    clientId: APPLE_SERVICES_ID,
    scope: "name email",
    redirectURI: APPLE_REDIRECT_URI,
    usePopup: true,
  });
  const res = await AppleID.auth.signIn();
  const token = res?.authorization?.id_token;
  if (!token) throw new Error("Apple returned no identity token.");
  const first = res?.user?.name?.firstName || "";
  const last = res?.user?.name?.lastName || "";
  return { token, name: `${first} ${last}`.trim() };
}
