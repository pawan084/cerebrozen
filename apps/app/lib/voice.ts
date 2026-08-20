// Voice for the web chat — the browser half of the same loop iOS and Android
// use, against the same two endpoints (backend/app/api/routes/voice.py).
//
// It goes through the SERVER, not the browser's own SpeechRecognition. That is
// deliberate: `webkitSpeechRecognition` ships audio to a vendor's servers under
// no consent this product asked for, exists in roughly one browser, and would
// have made the web client's voice a different product from the mobile one.
// /voice/stt drops the bytes the moment a transcript exists unless
// `voice_storage` consent says otherwise, and it says which in the response.
//
// Everything degrades without keys, per the project rule: /voice/status is the
// authority, and a client that cannot reach it shows no microphone at all
// rather than a button that fails when pressed.

import { authedFetch } from "./api";

export type VoiceStatus = { stt: boolean; tts: boolean };

export async function voiceStatus(): Promise<VoiceStatus> {
  try {
    const res = await authedFetch("/voice/status");
    if (!res.ok) return { stt: false, tts: false };
    const body = await res.json();
    return { stt: body.stt === true, tts: body.tts === true };
  } catch {
    return { stt: false, tts: false };
  }
}

/** Whether this browser can capture a clip at all — Safari on iOS gates
 *  getUserMedia, and an insecure origin has no mediaDevices whatsoever. */
export function canRecord(): boolean {
  return typeof navigator !== "undefined" && !!navigator.mediaDevices?.getUserMedia && typeof MediaRecorder !== "undefined";
}

export type Recording = { stop: () => Promise<Blob> };

/**
 * Start recording. The returned `stop` resolves with the clip.
 *
 * The microphone track is stopped explicitly on the way out — leaving it live
 * keeps the browser's recording indicator on, which on a page about privacy is
 * the worst possible thing to get wrong.
 */
export async function startRecording(): Promise<Recording> {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  const chunks: BlobPart[] = [];
  // Let the browser pick its own container: Chrome gives webm/opus, Safari
  // mp4/aac, and naming one explicitly throws on the browser that lacks it.
  const rec = new MediaRecorder(stream);
  rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
  rec.start();

  return {
    stop: () =>
      new Promise<Blob>((resolve) => {
        rec.onstop = () => {
          for (const track of stream.getTracks()) track.stop();
          resolve(new Blob(chunks, { type: rec.mimeType || "audio/webm" }));
        };
        rec.stop();
      }),
  };
}

/** Send the clip up and get words back. Throws with the server's own reason —
 *  "could not transcribe" is a different thing from "not configured", and the
 *  caller shows which. */
export async function transcribe(clip: Blob): Promise<string> {
  const form = new FormData();
  // A filename is required for FastAPI to treat the part as an UploadFile.
  form.append("audio", clip, "clip.webm");
  const res = await authedFetch("/voice/stt", { method: "POST", body: form });
  if (!res.ok) {
    const detail = await res
      .json()
      .then((b) => (typeof b?.detail === "string" ? b.detail : ""))
      .catch(() => "");
    throw new Error(detail || "Could not turn that into words.");
  }
  const body = await res.json();
  return (body.transcript as string) ?? "";
}

/** Speak a reply. Returns the playing element so a caller can stop it when the
 *  person starts typing again — a companion that talks over you is worse than
 *  one that stays quiet. */
export async function speak(text: string): Promise<HTMLAudioElement | null> {
  const res = await authedFetch("/voice/tts", {
    method: "POST",
    body: JSON.stringify({ text: text.slice(0, 5000) }),
  });
  if (!res.ok) return null;
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const audio = new Audio(url);
  // Revoke on the way out, or every spoken reply leaks its clip for the life
  // of the tab.
  audio.onended = () => URL.revokeObjectURL(url);
  await audio.play().catch(() => {});
  return audio;
}
