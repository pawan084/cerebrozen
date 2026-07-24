// Theme preference (System / Night / Dawn) — the web analogue of Android's
// You → Appearance `theme_mode` pref. Stored locally (a display preference,
// not server data); the root layout's pre-paint script reads the same key.
export type ThemeMode = "system" | "night" | "dawn";

const KEY = "theme_mode";

export function getThemeMode(): ThemeMode {
  if (typeof window === "undefined") return "system";
  const v = window.localStorage.getItem(KEY);
  return v === "night" || v === "dawn" ? v : "system";
}

export function setThemeMode(mode: ThemeMode) {
  if (typeof window === "undefined") return;
  if (mode === "system") {
    window.localStorage.removeItem(KEY);
    delete document.documentElement.dataset.theme;
  } else {
    window.localStorage.setItem(KEY, mode);
    document.documentElement.dataset.theme = mode;
  }
}
