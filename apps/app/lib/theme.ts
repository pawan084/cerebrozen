"use client";

/* Appearance: Night (dark, the default) / Dawn (light) / System — parity with the
   mobile app. The choice is stored locally and applied by stamping data-theme on
   <html>; globals.css defines the light palette under :root[data-theme="light"]. */

export type ThemeChoice = "system" | "light" | "dark";

export function getThemeChoice(): ThemeChoice {
  try {
    const t = localStorage.getItem("theme");
    if (t === "light" || t === "dark" || t === "system") return t;
  } catch { /* ignore */ }
  return "dark"; // the app's established default
}

export function resolveTheme(c: ThemeChoice): "light" | "dark" {
  if (c === "system") {
    return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: light)").matches
      ? "light"
      : "dark";
  }
  return c;
}

export function applyTheme(c: ThemeChoice): void {
  if (typeof document !== "undefined") document.documentElement.dataset.theme = resolveTheme(c);
}

export function setThemeChoice(c: ThemeChoice): void {
  try { localStorage.setItem("theme", c); } catch { /* ignore */ }
  applyTheme(c);
}
