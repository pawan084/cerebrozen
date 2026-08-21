import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LAYERS, Mixer, PRESETS, dominantLayer, matchingPreset } from "../../apps/app/lib/mixer";

// ── A Web Audio stand-in ────────────────────────────────────────────────────
//
// jsdom ships no AudioContext, and the point here is not to prove that noise
// sounds like rain — it is that the mixer wires the right things to the right
// places, honours the server-asset precedence Android documents, and ramps its
// volumes instead of jumping them.

class FakeParam {
  value = 0;
  ramps: { target: number; time: number; constant: number }[] = [];
  setTargetAtTime(target: number, time: number, constant: number) {
    this.ramps.push({ target, time, constant });
  }
  setValueAtTime(v: number) {
    this.value = v;
  }
  linearRampToValueAtTime(v: number) {
    this.value = v;
  }
}

class FakeNode {
  gain = new FakeParam();
  frequency = new FakeParam();
  detune = new FakeParam();
  Q = new FakeParam();
  type = "";
  buffer: unknown = null;
  loop = false;
  started = false;
  stopped = false;
  connectedTo: FakeNode[] = [];
  disconnected = false;
  connect(dest: any) {
    this.connectedTo.push(dest);
    return dest;
  }
  disconnect() {
    this.disconnected = true;
  }
  start() {
    this.started = true;
  }
  stop() {
    this.stopped = true;
  }
}

class FakeAudioContext {
  static instances: FakeAudioContext[] = [];
  state: "running" | "suspended" | "closed" = "suspended";
  sampleRate = 44100;
  currentTime = 0;
  destination = new FakeNode();
  nodes: FakeNode[] = [];
  closed = false;

  constructor() {
    FakeAudioContext.instances.push(this);
  }
  private track<T extends FakeNode>(n: T): T {
    this.nodes.push(n);
    return n;
  }
  createGain() {
    return this.track(new FakeNode());
  }
  createOscillator() {
    return this.track(new FakeNode());
  }
  createBiquadFilter() {
    return this.track(new FakeNode());
  }
  createBufferSource() {
    return this.track(new FakeNode());
  }
  createBuffer(_ch: number, len: number) {
    const data = new Float32Array(len);
    return { getChannelData: () => data, length: len };
  }
  async resume() {
    this.state = "running";
  }
  async suspend() {
    this.state = "suspended";
  }
  async close() {
    this.closed = true;
    this.state = "closed";
  }
}

function fakeElement() {
  return {
    loop: false,
    volume: 0,
    paused: true,
    play: vi.fn(async function (this: any) {
      this.paused = false;
    }),
    pause: vi.fn(function (this: any) {
      this.paused = true;
    }),
  } as unknown as HTMLAudioElement;
}

beforeEach(() => {
  FakeAudioContext.instances = [];
  (window as any).AudioContext = FakeAudioContext;
});

afterEach(() => {
  delete (window as any).AudioContext;
});

// ── The pure half ───────────────────────────────────────────────────────────

describe("naming the current blend", () => {
  it("recognises each preset exactly", () => {
    for (const p of PRESETS) expect(matchingPreset(p.volumes)).toBe(p.id);
  });

  it("deselects the chip as soon as a slider moves", () => {
    // Otherwise a preset sits there looking selected over a mix it no longer
    // describes — the chip becomes a lie about what you are hearing.
    const nudged = [...PRESETS[0].volumes];
    nudged[1] += 0.05;
    expect(matchingPreset(nudged)).toBeNull();
  });

  it("tolerates slider noise below one percent", () => {
    const jittered = PRESETS[1].volumes.map((v) => v + 0.005);
    expect(matchingPreset(jittered)).toBe("monsoon_night");
  });

  it("treats a missing slider as silence rather than throwing", () => {
    expect(matchingPreset([])).toBeNull();
    expect(matchingPreset([0, 0, 0, 0])).toBeNull();
  });

  it("names a custom blend by its loudest audible layer", () => {
    // "Mostly rain" beats "Custom mix".
    expect(dominantLayer([0.9, 0.2, 0, 0])).toBe("Rain");
    expect(dominantLayer([0.1, 0.8, 0, 0])).toBe("Ocean");
    expect(dominantLayer([0, 0, 0.4, 0.3])).toBe("Wind");
  });

  it("says nothing when everything is silent", () => {
    expect(dominantLayer([0, 0, 0, 0])).toBeNull();
    expect(dominantLayer([])).toBeNull();
  });

  it("ignores a layer that is technically on but inaudible", () => {
    // A slider left at 0.01 should not name the whole soundscape after it.
    expect(dominantLayer([0.01, 0, 0, 0])).toBeNull();
  });

  it("keeps the first of two equally loud layers rather than flickering", () => {
    expect(dominantLayer([0.5, 0.5, 0, 0])).toBe("Rain");
  });
});

describe("the presets are Android's", () => {
  // Hand-duplicated per CLAUDE.md. Two clients whose "Monsoon night" sounds
  // different is the drift that makes "the same mixer" quietly untrue.
  const kotlin = readFileSync(
    resolve(__dirname, "../../apps/android/app/src/main/java/com/cerebrozen/app/audio/SoundscapeMixer.kt"),
    "utf8",
  );

  it("ships the same four ids in the same order", () => {
    const ids = [...kotlin.matchAll(/Preset\("([a-z_]+)"/g)].map((m) => m[1]);
    expect(ids.length).toBeGreaterThan(0);
    expect(PRESETS.map((p) => p.id)).toEqual(ids);
  });

  it("ships the same volume vectors", () => {
    const vectors = [...kotlin.matchAll(/Preset\("[a-z_]+",\s*listOf\(([^)]*)\)/g)].map((m) =>
      m[1].split(",").map((v) => parseFloat(v.replace("f", "").trim())),
    );
    expect(vectors.length).toBe(PRESETS.length);
    PRESETS.forEach((p, i) => {
      p.volumes.forEach((v, j) => expect(v).toBeCloseTo(vectors[i][j], 2));
    });
  });

  it("leads with a preset that matches the factory blend", () => {
    // So a first visit reads as a named preset rather than the puzzling
    // "Custom mix".
    expect(PRESETS[0].id).toBe("just_rain");
  });

  it("gives every layer a catalogue key an admin could upload against", () => {
    for (const l of LAYERS) expect(l.key).toBe(`ambience.${l.id}`);
  });
});

// ── The wiring ──────────────────────────────────────────────────────────────

describe("starting", () => {
  it("builds a synthesised voice for every layer when nothing is uploaded", async () => {
    const m = new Mixer();
    await m.start();
    // Four layers, each with at least a gain node reaching the master.
    expect(FakeAudioContext.instances).toHaveLength(1);
    expect(FakeAudioContext.instances[0].nodes.length).toBeGreaterThanOrEqual(5);
    expect(m.playing).toBe(true);
  });

  it("resumes the existing context rather than building a second one", async () => {
    // An AudioContext created outside a user gesture starts suspended, and
    // stacking contexts is how a page ends up with two soundscapes playing.
    const m = new Mixer();
    await m.start();
    await m.stop();
    await m.start();
    expect(FakeAudioContext.instances).toHaveLength(1);
  });

  it("falls back to webkitAudioContext", async () => {
    delete (window as any).AudioContext;
    (window as any).webkitAudioContext = FakeAudioContext;
    const m = new Mixer();
    await m.start();
    expect(FakeAudioContext.instances).toHaveLength(1);
    delete (window as any).webkitAudioContext;
  });

  it("is not playing before start, and not after stop", async () => {
    const m = new Mixer();
    expect(m.playing).toBe(false);
    await m.start();
    await m.stop();
    expect(m.playing).toBe(false);
  });
});

describe("an uploaded asset supersedes the synthesised layer", () => {
  it("builds no synth voice for a layer an admin has uploaded", async () => {
    // The precedence Android documents. Both playing at once would be two
    // rains over each other.
    const bare = new Mixer();
    await bare.start();
    const bareNodes = FakeAudioContext.instances[0].nodes.length;

    FakeAudioContext.instances = [];
    const m = new Mixer();
    m.setAssets({ "ambience.rain": "https://cdn/rain.mp3" });
    await m.start();
    expect(FakeAudioContext.instances[0].nodes.length).toBeLessThan(bareNodes);
  });

  it("ignores a catalogue row with an empty url", async () => {
    // Every ambience.* row currently ships with an empty url — the catalogue
    // saying "this key exists, play your own". Honouring it would produce a
    // silent slider and a claim of a mixer.
    const bare = new Mixer();
    await bare.start();
    const bareNodes = FakeAudioContext.instances[0].nodes.length;

    FakeAudioContext.instances = [];
    const m = new Mixer();
    m.setAssets({ "ambience.rain": "", "ambience.ocean": "" });
    await m.start();
    expect(FakeAudioContext.instances[0].nodes.length).toBe(bareNodes);
  });

  it("ignores keys that are not layers", async () => {
    const m = new Mixer();
    expect(() => m.setAssets({ "sleepstory.rain": "https://cdn/x.mp3" })).not.toThrow();
  });
});

describe("volumes", () => {
  it("opens at Android's 0.7 master", async () => {
    // The two clients opening at different loudness is exactly the drift this
    // default exists to prevent.
    const m = new Mixer();
    await m.start();
    const master = FakeAudioContext.instances[0].nodes.find(
      (n) => n.connectedTo.includes(FakeAudioContext.instances[0].destination as any),
    );
    expect(master!.gain.value).toBeCloseTo(0.7, 2);
  });

  it("ramps the MASTER instead of jumping it", async () => {
    // Setting .value mid-drag is what makes a volume control click and pop.
    //
    // Asserted on the master node specifically. A first version checked only
    // that SOME node had ramped, and a mutant that jumped the master still
    // passed — the per-layer ramps in applyVolumes were carrying the assertion.
    const m = new Mixer();
    await m.start();
    const ctx = FakeAudioContext.instances[0];
    const master = ctx.nodes.find((n) => n.connectedTo.includes(ctx.destination as any))!;
    const before = master.gain.ramps.length;

    m.setMaster(0.3);

    expect(master.gain.ramps.length).toBeGreaterThan(before);
    expect(master.gain.ramps.at(-1)!.target).toBeCloseTo(0.3, 2);
  });

  it("ramps a layer too", async () => {
    const m = new Mixer();
    await m.start();
    const before = FakeAudioContext.instances[0].nodes.reduce((n, x) => n + x.gain.ramps.length, 0);
    m.setLayer(0, 0.8);
    const after = FakeAudioContext.instances[0].nodes.reduce((n, x) => n + x.gain.ramps.length, 0);
    expect(after).toBeGreaterThan(before);
  });

  it("accepts a whole preset at once", async () => {
    const m = new Mixer();
    await m.start();
    expect(() => m.setAll(PRESETS[1].volumes)).not.toThrow();
  });

  it("does not throw when a slider moves before the context exists", () => {
    // The page renders its sliders before the user has clicked anything, and
    // start() may only be called from a gesture.
    const m = new Mixer();
    expect(() => m.setLayer(0, 0.5)).not.toThrow();
    expect(() => m.setMaster(0.5)).not.toThrow();
    expect(() => m.setAll([1, 1, 1, 1])).not.toThrow();
  });
});

describe("uploaded layers play through an <audio> element", () => {
  it("loops it and sets its volume from layer x master", async () => {
    const m = new Mixer();
    m.setAssets({ "ambience.rain": "https://cdn/rain.mp3" });
    m.setAll([0.5, 0, 0, 0]);
    const el = fakeElement();
    m.attachElement("rain", el);
    expect(el.loop).toBe(true);
    expect(el.volume).toBeCloseTo(0.5 * 0.7, 2);
  });

  it("never sets a volume above 1, which throws in the browser", async () => {
    const m = new Mixer();
    m.setAll([1, 0, 0, 0]);
    const el = fakeElement();
    m.attachElement("rain", el);
    m.setMaster(1);
    m.setAll([1, 0, 0, 0]);
    expect(el.volume).toBeLessThanOrEqual(1);
  });

  it("starts and pauses with the mixer", async () => {
    const m = new Mixer();
    m.setAssets({ "ambience.rain": "https://cdn/rain.mp3" });
    const el = fakeElement();
    m.attachElement("rain", el);
    await m.start();
    expect(el.play).toHaveBeenCalled();
    await m.stop();
    expect(el.pause).toHaveBeenCalled();
  });

  it("survives a browser refusing to autoplay the element", async () => {
    const m = new Mixer();
    const el = fakeElement();
    (el.play as any).mockRejectedValue(new Error("NotAllowedError"));
    m.attachElement("rain", el);
    await expect(m.start()).resolves.toBeUndefined();
  });
});

describe("disposing", () => {
  it("stops the voices, pauses the elements and closes the context", async () => {
    // A mixer left running after the page changes is audible, which makes this
    // the one leak in the app a user would actually notice.
    const m = new Mixer();
    const el = fakeElement();
    m.attachElement("rain", el);
    await m.start();
    const ctx = FakeAudioContext.instances[0];

    m.dispose();

    expect(el.pause).toHaveBeenCalled();
    expect(ctx.closed).toBe(true);
    expect(m.playing).toBe(false);
  });

  it("can be disposed without ever having started", () => {
    expect(() => new Mixer().dispose()).not.toThrow();
  });

  it("can start again after being disposed", async () => {
    const m = new Mixer();
    await m.start();
    m.dispose();
    await m.start();
    expect(m.playing).toBe(true);
  });
});
