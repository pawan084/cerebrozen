"use client";

/* Fire a gentle celebration flourish from anywhere (mood check-in, journal save,
   sleep log, tool completion…). A DOM CustomEvent keeps it decoupled — the
   <Celebration /> listener in the shell renders it. Honors reduced motion there. */
export function celebrate(message = "Nice."): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("cbz:celebrate", { detail: message }));
  }
}
