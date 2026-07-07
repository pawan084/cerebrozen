// CereBro service worker — Web Push only (no fetch interception/caching).
// Payloads come encrypted end-to-end from the backend (RFC 8291); shape:
// { title, body, deeplink, kind } — see backend/app/services/webpush.py.

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    // Non-JSON payload: fall back to plain text as the body.
    data = { body: event.data ? event.data.text() : "" };
  }
  event.waitUntil(
    self.registration.showNotification(data.title || "CereBro", {
      body: data.body || "",
      data: { deeplink: data.deeplink || "" },
      tag: data.kind || "cerebro-nudge", // one visible nudge per kind
    })
  );
});

// Map the cross-platform cerebro:// deeplinks onto web app routes.
const ROUTES = { mood: "/home", breathe: "/games", sleep: "/sleep" };

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const key = (event.notification.data?.deeplink || "").replace("cerebro://", "");
  const url = ROUTES[key] || "/home";
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((wins) => {
      for (const win of wins) {
        if ("focus" in win) {
          win.navigate(url);
          return win.focus();
        }
      }
      return clients.openWindow(url);
    })
  );
});
