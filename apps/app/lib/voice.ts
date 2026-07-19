"use client";

/* Browser-native voice — keyless, on-device, no backend required.
 *
 * The engine's higher-fidelity voice is LiveKit + Deepgram/ElevenLabs and needs
 * provider keys the local/air-gapped deployment may not have. This is the web
 * analogue of the mobile app's on-device STT/TTS fallback: the Web Speech API.
 * Everything here degrades cleanly where the browser lacks the capability
 * (Firefox has no SpeechRecognition; some browsers have no speechSynthesis). */

export type Recognition = {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((e: SpeechResultLike) => void) | null;
  onend: (() => void) | null;
  onerror: ((e: unknown) => void) | null;
};

export type SpeechResultLike = {
  resultIndex: number;
  results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }>;
};

export function sttSupported(): boolean {
  if (typeof window === "undefined") return false;
  const w = window as unknown as Record<string, unknown>;
  return !!(w.SpeechRecognition || w.webkitSpeechRecognition);
}

export function ttsSupported(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window && "SpeechSynthesisUtterance" in window;
}

export function getRecognition(lang = "en-US"): Recognition | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as Record<string, unknown>;
  const Ctor = (w.SpeechRecognition || w.webkitSpeechRecognition) as (new () => Recognition) | undefined;
  if (!Ctor) return null;
  const r = new Ctor();
  r.lang = lang;
  r.interimResults = true;
  r.continuous = false;
  return r;
}

/** Speak a coach reply. Strips markdown so it doesn't read "asterisk asterisk". */
export function speak(text: string): void {
  if (!ttsSupported()) return;
  const clean = text
    .replace(/```[\s\S]*?```/g, " code block ")
    .replace(/[*_`#>|]/g, "")
    .replace(/\[(.*?)\]\(.*?\)/g, "$1")
    .replace(/\s+/g, " ")
    .trim();
  if (!clean) return;
  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(clean);
  u.rate = 1;
  u.pitch = 1;
  window.speechSynthesis.speak(u);
}

export function stopSpeaking(): void {
  if (ttsSupported()) window.speechSynthesis.cancel();
}
