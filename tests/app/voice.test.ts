import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const authedFetchMock = vi.fn();
const net = vi.hoisted(() => ({ down: false }));
vi.mock("../../apps/app/lib/api", () => ({
  authedFetch: (...args: unknown[]) => {
    if (net.down) throw new TypeError("Failed to fetch");
    return authedFetchMock(...args);
  },
}));

import { canRecord, speak, startRecording, transcribe, voiceStatus } from "../../apps/app/lib/voice";

function response(status: number, body?: unknown, blob?: Blob): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (body === undefined) throw new SyntaxError("not json");
      return body;
    },
    blob: async () => blob ?? new Blob(["audio"]),
  } as Response;
}

beforeEach(() => {
  net.down = false;
  authedFetchMock.mockReset();
});

afterEach(() => vi.unstubAllGlobals());

describe("whether to show a microphone at all", () => {
  // "Everything degrades without keys": /voice/status is the authority, and a
  // client that cannot reach it shows NO microphone rather than a button that
  // fails when pressed.
  it("reports what the server reports", async () => {
    authedFetchMock.mockResolvedValue(response(200, { stt: true, tts: true }));
    await expect(voiceStatus()).resolves.toEqual({ stt: true, tts: true });
  });

  it("is off, not on, when the endpoint fails", async () => {
    authedFetchMock.mockResolvedValue(response(503));
    await expect(voiceStatus()).resolves.toEqual({ stt: false, tts: false });
  });

  it("is off when the network never answers", async () => {
    net.down = true;
    await expect(voiceStatus()).resolves.toEqual({ stt: false, tts: false });
  });

  it("treats a truthy-but-not-true value as off", async () => {
    // Strict === true: a server sending "yes" or 1 must not switch on a feature
    // whose backing key may be absent.
    authedFetchMock.mockResolvedValue(response(200, { stt: "yes", tts: 1 }));
    await expect(voiceStatus()).resolves.toEqual({ stt: false, tts: false });
  });

  it("is off when the body has no flags at all", async () => {
    authedFetchMock.mockResolvedValue(response(200, {}));
    await expect(voiceStatus()).resolves.toEqual({ stt: false, tts: false });
  });
});

describe("whether this browser can capture a clip", () => {
  it("is false without mediaDevices — an insecure origin has none", () => {
    vi.stubGlobal("navigator", {});
    expect(canRecord()).toBe(false);
  });

  it("is false without MediaRecorder", () => {
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia: () => {} } });
    vi.stubGlobal("MediaRecorder", undefined);
    expect(canRecord()).toBe(false);
  });

  it("is true when both exist", () => {
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia: () => {} } });
    vi.stubGlobal("MediaRecorder", class {});
    expect(canRecord()).toBe(true);
  });
});

describe("recording", () => {
  /** A MediaRecorder stand-in, plus the tracks whose stop() we care about. */
  function fakeCapture() {
    const tracks = [{ stop: vi.fn() }, { stop: vi.fn() }];
    const stream = { getTracks: () => tracks };
    let instance: any;
    class FakeRecorder {
      ondataavailable: ((e: any) => void) | null = null;
      onstop: (() => void) | null = null;
      mimeType = "audio/webm";
      constructor() {
        instance = this;
      }
      start() {}
      stop() {
        this.ondataavailable?.({ data: new Blob(["chunk"]) });
        this.onstop?.();
      }
    }
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia: async () => stream } });
    vi.stubGlobal("MediaRecorder", FakeRecorder);
    return { tracks, get instance() { return instance; } };
  }

  it("stops every microphone track when the recording ends", async () => {
    // The one that matters most. Leaving a track live keeps the browser's
    // recording indicator on — on a page about privacy, the worst possible
    // thing to get wrong, and completely invisible in a screenshot.
    const cap = fakeCapture();
    const rec = await startRecording();
    await rec.stop();
    for (const t of cap.tracks) expect(t.stop).toHaveBeenCalled();
  });

  it("resolves with the captured audio", async () => {
    fakeCapture();
    const rec = await startRecording();
    const clip = await rec.stop();
    expect(clip.size).toBeGreaterThan(0);
  });

  it("does not name a container the browser may not have", async () => {
    // Chrome gives webm/opus and Safari mp4/aac; naming one explicitly throws
    // on the browser that lacks it, so the constructor is called bare.
    const cap = fakeCapture();
    await startRecording();
    expect(cap.instance.mimeType).toBe("audio/webm");
  });
});

describe("turning a clip into words", () => {
  it("uploads as multipart with a filename FastAPI will accept", async () => {
    authedFetchMock.mockResolvedValue(response(200, { transcript: "hello there" }));
    await transcribe(new Blob(["audio"]));
    const [path, init] = authedFetchMock.mock.calls[0];
    expect(path).toBe("/voice/stt");
    expect(init.body).toBeInstanceOf(FormData);
    // Without a filename the part is a plain field, not an UploadFile.
    const file = (init.body as FormData).get("audio") as File;
    expect(file).toBeTruthy();
  });

  it("returns the transcript", async () => {
    authedFetchMock.mockResolvedValue(response(200, { transcript: "hello there" }));
    await expect(transcribe(new Blob(["a"]))).resolves.toBe("hello there");
  });

  it("returns empty rather than undefined when there were no words", async () => {
    authedFetchMock.mockResolvedValue(response(200, {}));
    await expect(transcribe(new Blob(["a"]))).resolves.toBe("");
  });

  it("surfaces the server's own reason", async () => {
    // "could not transcribe" is a different thing from "not configured", and
    // the caller shows which — so the detail has to survive.
    authedFetchMock.mockResolvedValue(response(503, { detail: "Speech-to-text is not configured." }));
    await expect(transcribe(new Blob(["a"]))).rejects.toThrow("Speech-to-text is not configured.");
  });

  it("falls back to a human sentence when the server gives no reason", async () => {
    authedFetchMock.mockResolvedValue(response(500));
    await expect(transcribe(new Blob(["a"]))).rejects.toThrow("Could not turn that into words.");
  });
});

describe("speaking a reply", () => {
  function fakeAudio() {
    const created: any[] = [];
    const revoked: string[] = [];
    vi.stubGlobal("URL", {
      createObjectURL: () => "blob:fake-url",
      revokeObjectURL: (u: string) => revoked.push(u),
    });
    vi.stubGlobal(
      "Audio",
      class {
        onended: (() => void) | null = null;
        constructor(public src: string) {
          created.push(this);
        }
        play() {
          return Promise.resolve();
        }
      },
    );
    return { created, revoked };
  }

  it("returns the element so the caller can stop it", async () => {
    // A companion that talks over you is worse than one that stays quiet.
    fakeAudio();
    authedFetchMock.mockResolvedValue(response(200, undefined, new Blob(["mp3"])));
    const audio = await speak("hello");
    expect(audio).toBeTruthy();
  });

  it("revokes the object URL when playback ends", async () => {
    // Otherwise every spoken reply leaks its clip for the life of the tab.
    const fake = fakeAudio();
    authedFetchMock.mockResolvedValue(response(200, undefined, new Blob(["mp3"])));
    const audio: any = await speak("hello");
    audio.onended();
    expect(fake.revoked).toContain("blob:fake-url");
  });

  it("truncates very long text rather than sending it all", async () => {
    fakeAudio();
    authedFetchMock.mockResolvedValue(response(200, undefined, new Blob(["mp3"])));
    await speak("x".repeat(9000));
    expect(JSON.parse(authedFetchMock.mock.calls[0][1].body).text).toHaveLength(5000);
  });

  it("returns null when TTS is unavailable, instead of throwing at the caller", async () => {
    fakeAudio();
    authedFetchMock.mockResolvedValue(response(503));
    await expect(speak("hello")).resolves.toBeNull();
  });

  it("survives a browser that refuses to autoplay", async () => {
    // play() rejects without a user gesture. The reply is still on screen as
    // text, so this must not surface as an error.
    const revoked: string[] = [];
    vi.stubGlobal("URL", {
      createObjectURL: () => "blob:fake-url",
      revokeObjectURL: (u: string) => revoked.push(u),
    });
    vi.stubGlobal(
      "Audio",
      class {
        onended: (() => void) | null = null;
        constructor(public src: string) {}
        play() {
          return Promise.reject(new Error("NotAllowedError"));
        }
      },
    );
    authedFetchMock.mockResolvedValue(response(200, undefined, new Blob(["mp3"])));
    await expect(speak("hello")).resolves.toBeTruthy();
  });
});
