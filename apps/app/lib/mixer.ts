// The layered soundscape mixer, web edition.
//
// Android's `SoundscapeMixer` blends four bundled loops (R.raw.rain, ocean,
// wind, drone) and prefers an uploaded server asset when one exists. The web
// client has no bundled loops — and every `ambience.*` row in the catalogue
// currently ships with an empty `url`, so "just fetch the same files" would
// have produced four silent sliders and a claim of a mixer.
//
// So the layers are SYNTHESISED with the Web Audio API, and a server asset
// supersedes the synthesised layer the moment an admin uploads one — the same
// precedence Android documents. Rain, wind and ocean are all shaped noise;
// drone is a pair of detuned oscillators. Nothing is downloaded, it works with
// no connection at all, and it costs no bandwidth.
//
// Autoplay policy: an AudioContext created outside a user gesture starts
// suspended. `start()` is therefore only ever called from a click handler, and
// it resumes an existing context rather than building a second one.

export type LayerId = "rain" | "ocean" | "wind" | "drone";

export const LAYERS: { id: LayerId; label: string; key: string }[] = [
  { id: "rain", label: "Rain", key: "ambience.rain" },
  { id: "ocean", label: "Ocean", key: "ambience.ocean" },
  { id: "wind", label: "Wind", key: "ambience.wind" },
  { id: "drone", label: "Drone", key: "ambience.drone" },
];

/** Android's four presets, same order, same vectors (SoundscapeMixer.presets).
 *  "Just rain" is first and matches the factory blend so a first visit reads as
 *  a named preset rather than the puzzling "Custom mix". */
export const PRESETS: { id: string; label: string; volumes: number[] }[] = [
  { id: "just_rain", label: "Just rain", volumes: [0.7, 0, 0, 0] },
  { id: "monsoon_night", label: "Monsoon night", volumes: [0.8, 0, 0.35, 0.2] },
  { id: "shoreline", label: "Shoreline", volumes: [0, 0.8, 0.3, 0] },
  { id: "still_air", label: "Still air", volumes: [0, 0, 0.25, 0.5] },
];

/** The preset the current blend matches within slider noise, or null — so
 *  nudging any slider honestly deselects the chip instead of leaving a preset
 *  looking selected over a mix it no longer describes. */
export function matchingPreset(volumes: number[]): string | null {
  return (
    PRESETS.find((p) => p.volumes.every((v, i) => Math.abs((volumes[i] ?? 0) - v) < 0.01))?.id ??
    null
  );
}

/** Names a non-preset blend by its loudest audible layer ("Mostly rain" beats
 *  "Custom mix"). Null when everything is silent. */
export function dominantLayer(volumes: number[]): string | null {
  let best = -1;
  volumes.forEach((v, i) => {
    if (v > 0.02 && (best < 0 || v > volumes[best])) best = i;
  });
  return best < 0 ? null : LAYERS[best].label;
}

type Voice = { gain: GainNode; stop: () => void };

// A few seconds of noise, looped. Long enough that the loop point is not a
// pulse you can hear, short enough not to allocate a minute of float samples.
const NOISE_SECONDS = 4;

function noiseBuffer(ctx: AudioContext, kind: "white" | "brown"): AudioBuffer {
  const len = Math.floor(ctx.sampleRate * NOISE_SECONDS);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const d = buf.getChannelData(0);
  if (kind === "white") {
    for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
  } else {
    // Brown (red) noise: an integrated random walk, which is what gives surf
    // and distant weather their weight. Leaked toward zero so it cannot drift
    // into a DC offset over a four-second buffer.
    let last = 0;
    for (let i = 0; i < len; i++) {
      last = (last + (Math.random() * 2 - 1) * 0.02) * 0.996;
      d[i] = last * 6;
    }
  }
  return buf;
}

function noiseSource(ctx: AudioContext, kind: "white" | "brown"): AudioBufferSourceNode {
  const src = ctx.createBufferSource();
  src.buffer = noiseBuffer(ctx, kind);
  src.loop = true;
  src.start();
  return src;
}

/** A slow sine on some parameter — the difference between weather and a hiss. */
function drift(ctx: AudioContext, param: AudioParam, centre: number, depth: number, hz: number) {
  const lfo = ctx.createOscillator();
  const amp = ctx.createGain();
  lfo.frequency.value = hz;
  amp.gain.value = depth;
  param.value = centre;
  lfo.connect(amp).connect(param);
  lfo.start();
  return lfo;
}

function buildVoice(ctx: AudioContext, id: LayerId): Voice {
  const gain = ctx.createGain();
  gain.gain.value = 0;
  const stops: { stop: () => void }[] = [];

  if (id === "drone") {
    // Two detuned oscillators under a low filter: a held chord, not a tone.
    const filter = ctx.createBiquadFilter();
    filter.type = "lowpass";
    filter.frequency.value = 320;
    for (const hz of [55, 82.5]) {
      const osc = ctx.createOscillator();
      osc.type = "sine";
      osc.frequency.value = hz;
      osc.detune.value = hz === 55 ? -6 : 5;
      const g = ctx.createGain();
      g.gain.value = 0.5;
      osc.connect(g).connect(filter);
      osc.start();
      stops.push(osc);
    }
    filter.connect(gain);
  } else {
    const src = noiseSource(ctx, id === "ocean" ? "brown" : "white");
    stops.push(src);
    const filter = ctx.createBiquadFilter();
    if (id === "rain") {
      // Bright and busy, with only a little movement — rain is steady.
      filter.type = "highpass";
      filter.frequency.value = 900;
      const shelf = ctx.createBiquadFilter();
      shelf.type = "lowpass";
      shelf.frequency.value = 7000;
      src.connect(filter).connect(shelf).connect(gain);
      stops.push({ stop: () => shelf.disconnect() });
    } else if (id === "ocean") {
      // Surf: brown noise under a filter that opens and closes on a ~12s swell.
      filter.type = "lowpass";
      stops.push(drift(ctx, filter.frequency, 620, 380, 0.085));
      src.connect(filter).connect(gain);
    } else {
      // Wind: a band sweeping slowly across the mid range.
      filter.type = "bandpass";
      filter.Q.value = 0.8;
      stops.push(drift(ctx, filter.frequency, 520, 300, 0.05));
      src.connect(filter).connect(gain);
    }
  }

  return {
    gain,
    stop: () => {
      for (const s of stops) {
        try {
          s.stop();
        } catch {
          // Already stopped — a second stop() on a finished node throws.
        }
      }
      gain.disconnect();
    },
  };
}

export class Mixer {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private voices = new Map<LayerId, Voice>();
  /** Layers playing from an uploaded server asset instead of the synth. */
  private elements = new Map<LayerId, HTMLAudioElement>();
  private volumes: number[] = LAYERS.map(() => 0);
  /** 0.7, the same default `SoundscapeMixer.master` carries on Android — the
   *  two clients opening at different loudness is the kind of drift that makes
   *  "the same mixer" quietly untrue. */
  private masterVolume = 0.7;
  /** key → url, for the layers an admin has actually uploaded. */
  private assets: Partial<Record<LayerId, string>> = {};

  get playing(): boolean {
    return this.ctx !== null && this.ctx.state === "running";
  }

  /** Server assets supersede the synthesised layers, exactly as on Android.
   *  Rows with an empty url are ignored — that is the catalogue saying "this
   *  key exists, play your own". */
  setAssets(byKey: Record<string, string>) {
    for (const l of LAYERS) {
      const url = byKey[l.key];
      if (url) this.assets[l.id] = url;
    }
  }

  /** Must be called from a user gesture. */
  async start(): Promise<void> {
    if (!this.ctx) {
      const Ctor: typeof AudioContext =
        window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new Ctor();
      this.master = this.ctx.createGain();
      this.master.gain.value = this.masterVolume;
      this.master.connect(this.ctx.destination);
      for (const l of LAYERS) {
        if (this.assets[l.id]) continue;
        const voice = buildVoice(this.ctx, l.id);
        voice.gain.connect(this.master);
        this.voices.set(l.id, voice);
      }
    }
    await this.ctx.resume();
    this.applyVolumes();
    for (const [, el] of this.elements) void el.play().catch(() => {});
  }

  async stop(): Promise<void> {
    for (const [, el] of this.elements) el.pause();
    if (this.ctx) await this.ctx.suspend();
  }

  setMaster(v: number): void {
    this.masterVolume = v;
    if (this.master && this.ctx) {
      // A short ramp rather than a jump: setting `.value` mid-slider-drag is
      // what makes a volume control click and pop.
      this.master.gain.setTargetAtTime(v, this.ctx.currentTime, 0.02);
    }
    for (const [id, el] of this.elements) el.volume = this.layerVolume(id) * v;
  }

  setLayer(index: number, v: number): void {
    this.volumes[index] = v;
    this.applyVolumes();
  }

  setAll(volumes: number[]): void {
    this.volumes = [...volumes];
    this.applyVolumes();
  }

  private layerVolume(id: LayerId): number {
    return this.volumes[LAYERS.findIndex((l) => l.id === id)] ?? 0;
  }

  private applyVolumes(): void {
    if (!this.ctx) return;
    for (const [id, voice] of this.voices) {
      voice.gain.gain.setTargetAtTime(this.layerVolume(id), this.ctx.currentTime, 0.03);
    }
    for (const [id, el] of this.elements) {
      el.volume = Math.min(1, this.layerVolume(id) * this.masterVolume);
    }
  }

  /** Attach the <audio> elements for uploaded layers. Called by the page once
   *  the catalogue has answered, because an element needs a src to exist. */
  attachElement(id: LayerId, el: HTMLAudioElement): void {
    el.loop = true;
    el.volume = Math.min(1, this.layerVolume(id) * this.masterVolume);
    this.elements.set(id, el);
  }

  dispose(): void {
    for (const [, v] of this.voices) v.stop();
    this.voices.clear();
    for (const [, el] of this.elements) el.pause();
    this.elements.clear();
    void this.ctx?.close();
    this.ctx = null;
    this.master = null;
  }
}
