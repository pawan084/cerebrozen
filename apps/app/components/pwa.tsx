"use client";

import { useEffect, useState } from "react";

/** Registers the service worker (PWA install + fast static loads) and shows an
 *  offline banner. Registration is a JS API call, not an inline script, so it's
 *  fine under the app's nonce-CSP. */
export function Pwa() {
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => { /* SW is best-effort */ });
    }
    const sync = () => setOffline(!navigator.onLine);
    sync();
    window.addEventListener("online", sync);
    window.addEventListener("offline", sync);
    return () => {
      window.removeEventListener("online", sync);
      window.removeEventListener("offline", sync);
    };
  }, []);

  if (!offline) return null;
  return (
    <div className="offline-banner" role="status" aria-live="polite">
      You&rsquo;re offline — live coaching needs a connection. Anything you&rsquo;ve written is safe.
    </div>
  );
}
