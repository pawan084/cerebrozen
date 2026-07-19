"use client";

/* Journal lock — a local, device-biometric gate on the private journal (parity
   with the mobile app's biometric lock). Uses WebAuthn's platform authenticator
   (Touch ID / Face ID / Windows Hello / passkey). It's a LOCAL gate on the UI,
   not server auth: the credential lives on the device and just proves "this is
   me" before revealing entries. Degrades where WebAuthn/platform auth is absent. */

const KEY = "cbz-journal-lock";

export function lockSupported(): boolean {
  return typeof window !== "undefined" && !!window.PublicKeyCredential;
}

export function isLockOn(): boolean {
  try { return !!localStorage.getItem(KEY); } catch { return false; }
}

function toB64(buf: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(buf)));
}
function fromB64(s: string): Uint8Array {
  return Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
}

/** Register a device credential and turn the lock on. Returns false if declined. */
export async function enableLock(): Promise<boolean> {
  if (!lockSupported()) return false;
  try {
    const cred = (await navigator.credentials.create({
      publicKey: {
        challenge: crypto.getRandomValues(new Uint8Array(32)),
        rp: { name: "CereBroZen" },
        user: {
          id: crypto.getRandomValues(new Uint8Array(16)),
          name: "journal",
          displayName: "Journal lock",
        },
        pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -257 }],
        authenticatorSelection: { authenticatorAttachment: "platform", userVerification: "required" },
        timeout: 60000,
      },
    })) as PublicKeyCredential | null;
    if (!cred) return false;
    localStorage.setItem(KEY, toB64(cred.rawId));
    return true;
  } catch {
    return false;
  }
}

export function disableLock(): void {
  try { localStorage.removeItem(KEY); } catch { /* ignore */ }
}

/** Prompt the device to verify, gating the journal. True if verified (or no lock). */
export async function unlock(): Promise<boolean> {
  const id = (() => { try { return localStorage.getItem(KEY); } catch { return null; } })();
  if (!id) return true;
  try {
    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge: crypto.getRandomValues(new Uint8Array(32)),
        allowCredentials: [{ type: "public-key", id: fromB64(id) as BufferSource }],
        userVerification: "required",
        timeout: 60000,
      },
    });
    return !!assertion;
  } catch {
    return false;
  }
}
