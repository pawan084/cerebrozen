"use client";

/* Ambient soundscape mixer — fully synthesized with the Web Audio API, so it needs
   no audio assets or backend (the web analogue of the mobile app's runtime tone
   synthesis). Each layer is looped brown-ish noise shaped by a filter (+ a slow LFO
   for ocean/wind movement), blended under a master gain. AudioContext is created on
   the first play, after a user gesture, per browser autoplay policy. */

export type LayerName = "rain" | "ocean" | "wind" | "drone";

type Live = { gain: GainNode; src: AudioBufferSourceNode; lfo?: OscillatorNode };

export class Soundscape {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private noise: AudioBuffer | null = null;
  private layers = new Map<LayerName, Live>();

  supported(): boolean {
    return typeof window !== "undefined" && (typeof AudioContext !== "undefined" || "webkitAudioContext" in window);
  }

  private ensure() {
    if (this.ctx) return;
    const Ctor = (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext);
    this.ctx = new Ctor();
    this.master = this.ctx.createGain();
    this.master.gain.value = 0.8;
    this.master.connect(this.ctx.destination);
    // 2s of brown-ish noise, looped.
    const len = this.ctx.sampleRate * 2;
    this.noise = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
    const data = this.noise.getChannelData(0);
    let last = 0;
    for (let i = 0; i < len; i++) {
      const white = Math.random() * 2 - 1;
      last = (last + 0.02 * white) / 1.02;
      data[i] = last * 3.5;
    }
  }

  toggle(name: LayerName, volume = 0.5): boolean {
    this.ensure();
    const ctx = this.ctx!, master = this.master!, noise = this.noise!;
    ctx.resume();
    if (this.layers.has(name)) {
      const l = this.layers.get(name)!;
      try { l.src.stop(); l.lfo?.stop(); } catch { /* already stopped */ }
      l.gain.disconnect();
      this.layers.delete(name);
      return false;
    }
    const src = ctx.createBufferSource();
    src.buffer = noise; src.loop = true;
    const filter = ctx.createBiquadFilter();
    const gain = ctx.createGain(); gain.gain.value = volume;
    let lfo: OscillatorNode | undefined;
    if (name === "rain") { filter.type = "highpass"; filter.frequency.value = 1200; }
    else if (name === "ocean") {
      filter.type = "lowpass"; filter.frequency.value = 450;
      lfo = ctx.createOscillator(); lfo.frequency.value = 0.12;
      const lg = ctx.createGain(); lg.gain.value = 0.35;
      lfo.connect(lg); lg.connect(gain.gain); lfo.start();
    } else if (name === "wind") {
      filter.type = "bandpass"; filter.frequency.value = 600; filter.Q.value = 0.7;
      lfo = ctx.createOscillator(); lfo.frequency.value = 0.08;
      const lg = ctx.createGain(); lg.gain.value = 300;
      lfo.connect(lg); lg.connect(filter.frequency); lfo.start();
    } else { filter.type = "lowpass"; filter.frequency.value = 220; }
    src.connect(filter); filter.connect(gain); gain.connect(master); src.start();
    this.layers.set(name, { gain, src, lfo });
    return true;
  }

  setVolume(name: LayerName, v: number) { const l = this.layers.get(name); if (l) l.gain.gain.value = v; }
  setMaster(v: number) { if (this.master) this.master.gain.value = v; }
  active(): LayerName[] { return [...this.layers.keys()]; }
  stopAll() { for (const n of this.active()) this.toggle(n); }
}
