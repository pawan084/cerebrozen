// Web Push subscription helpers (account-page "Browser notifications" toggle).
//
// The server is the source of truth for availability: GET
// /users/me/push-subscriptions reports whether VAPID keys are configured and
// hands out the public application server key — no NEXT_PUBLIC_ env needed.

import { api } from "@/lib/api";

export type PushStatus = { enabled: boolean; public_key: string; subscriptions: number };

/** Push needs a secure context + SW + PushManager + Notification APIs. */
export function pushSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}

export function getPushStatus(): Promise<PushStatus> {
  return api<PushStatus>("/users/me/push-subscriptions");
}

/** Whether THIS browser holds a live push subscription. */
export async function isSubscribed(): Promise<boolean> {
  if (!pushSupported()) return false;
  const reg = await navigator.serviceWorker.getRegistration();
  return !!(await reg?.pushManager.getSubscription());
}

// applicationServerKey wants raw bytes; VAPID public keys travel base64url.
function urlBase64ToUint8Array(base64: string): Uint8Array {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const raw = atob((base64 + padding).replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

/** Ask permission, register the SW, subscribe, and store it server-side.
 * Throws with an honest message when the user declines or push is unusable. */
export async function subscribePush(publicKey: string): Promise<void> {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") throw new Error("Notifications were declined in the browser.");
  const reg = await navigator.serviceWorker.register("/sw.js");
  await navigator.serviceWorker.ready;
  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey) as BufferSource,
  });
  const json = sub.toJSON();
  await api("/users/me/push-subscriptions", {
    method: "POST",
    body: JSON.stringify({
      endpoint: sub.endpoint,
      p256dh: json.keys?.p256dh ?? "",
      auth: json.keys?.auth ?? "",
    }),
  });
}

/** Drop this browser's subscription server-side, then locally. */
export async function unsubscribePush(): Promise<void> {
  const reg = await navigator.serviceWorker.getRegistration();
  const sub = await reg?.pushManager.getSubscription();
  if (!sub) return;
  try {
    await api(`/users/me/push-subscriptions?endpoint=${encodeURIComponent(sub.endpoint)}`, {
      method: "DELETE",
    });
  } finally {
    await sub.unsubscribe();
  }
}
