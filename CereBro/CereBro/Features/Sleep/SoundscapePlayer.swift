import AVFoundation
import SwiftUI

// MARK: - Soundscape kinds
/// The sleep/meditation catalogue is procedurally synthesized on-device — no
/// bundled audio files or network needed — so playback is genuinely real.
enum SoundscapeKind {
    case rain, ocean, wind, meditation, ambient

    static func from(_ item: ContentItem) -> SoundscapeKind {
        let s = (item.title + " " + item.subtitle + " " + item.symbol).lowercased()
        if s.contains("rain") || s.contains("storm") { return .rain }
        if s.contains("ocean") || s.contains("sea") || s.contains("wave") || s.contains("water") { return .ocean }
        if s.contains("wind") || s.contains("night") || s.contains("drift") || s.contains("forest") { return .wind }
        if s.contains("breath") || s.contains("medit") || s.contains("calm") || s.contains("mind") || s.contains("body") { return .meditation }
        return .ambient
    }
}

// MARK: - DSP (runs on the audio render thread; no allocations / locks)
/// A tiny procedural synth. All state is touched only from the render callback,
/// so it needs no locking. `kind` is set before `play()` and left alone after.
private final class Synth {
    var kind: SoundscapeKind = .ambient
    var sampleRate: Float = 44_100

    private var rng: UInt32 = 0x9E3779B9
    private var lp: Float = 0, lp2: Float = 0
    private var mod: Float = 0
    private var tone: [Float] = [0, 0, 0]

    @inline(__always) private func white() -> Float {
        rng ^= rng << 13; rng ^= rng >> 17; rng ^= rng << 5
        return Float(Int32(bitPattern: rng)) / Float(Int32.max)
    }

    @inline(__always) func next() -> Float {
        let twoPi = 2 * Float.pi
        switch kind {
        case .rain:
            let n = white()
            lp += 0.5 * (n - lp)               // gentle smoothing
            return (n - lp * 0.7) * 0.22       // keep the bright "patter"
        case .ocean:
            lp += 0.02 * (white() - lp)        // deep rumble
            mod += twoPi * 0.09 / sampleRate   // ~0.09 Hz swell
            if mod > twoPi { mod -= twoPi }
            return lp * (0.5 + 0.5 * sin(mod)) * 0.95
        case .wind:
            lp  += 0.05 * (white() - lp)
            lp2 += 0.05 * (lp - lp2)
            mod += twoPi * 0.07 / sampleRate
            if mod > twoPi { mod -= twoPi }
            return lp2 * (0.45 + 0.4 * sin(mod)) * 1.2
        case .meditation:
            let freqs: [Float] = [110, 164.81, 220]   // A2 · E3 · A3 drone
            var s: Float = 0
            for i in 0..<3 {
                tone[i] += twoPi * freqs[i] / sampleRate
                if tone[i] > twoPi { tone[i] -= twoPi }
                s += sin(tone[i])
            }
            mod += twoPi * 0.13 / sampleRate          // slow tremolo
            if mod > twoPi { mod -= twoPi }
            return (s / 3) * 0.17 * (0.75 + 0.25 * sin(mod))
        case .ambient:
            lp += 0.08 * (white() - lp)
            return lp * 0.28
        }
    }
}

// MARK: - Player
@MainActor
final class SoundscapePlayer: ObservableObject {
    @Published private(set) var isPlaying = false
    /// Minutes remaining on the sleep auto-stop timer, or nil when disarmed.
    @Published private(set) var sleepTimerMinutes: Int?

    private let engine = AVAudioEngine()
    private let synth = Synth()
    private var source: AVAudioSourceNode?
    private var fadeTimer: Timer?
    private var sleepTimer: Timer?
    private var baseVolume: Float = 0.7

    func configure(for item: ContentItem) { synth.kind = SoundscapeKind.from(item) }

    func toggle() { isPlaying ? pause() : play() }

    func play() {
        activateSession()
        if source == nil { buildSourceNode() }
        engine.mainMixerNode.outputVolume = baseVolume
        do {
            if !engine.isRunning { try engine.start() }
            isPlaying = true
            Haptics.soft(intensity: 0.4)
        } catch {
            isPlaying = false
        }
    }

    func pause() {
        engine.pause()
        isPlaying = false
    }

    /// Full teardown — call when leaving the player so audio never leaks.
    func stop() {
        cancelSleepTimer()
        fadeTimer?.invalidate(); fadeTimer = nil
        engine.stop()
        if let s = source { engine.detach(s); source = nil }
        isPlaying = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: Sleep auto-stop timer (fades out, then stops)

    /// Cycles the sleep timer: off → 15 → 30 → 45 → 60 → off.
    func cycleSleepTimer() {
        let next: Int?
        switch sleepTimerMinutes {
        case nil:  next = 15
        case 15:   next = 30
        case 30:   next = 45
        case 45:   next = 60
        default:   next = nil
        }
        if let minutes = next { armSleepTimer(minutes: minutes) } else { cancelSleepTimer() }
    }

    private func armSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerMinutes = minutes
        let fadeLead: TimeInterval = 12                 // begin fading this long before the end
        let total = TimeInterval(minutes * 60)
        sleepTimer = Timer.scheduledTimer(withTimeInterval: max(1, total - fadeLead), repeats: false) { [weak self] _ in
            Task { @MainActor in self?.fadeOutAndStop(over: fadeLead) }
        }
    }

    private func cancelSleepTimer() {
        sleepTimer?.invalidate(); sleepTimer = nil
        sleepTimerMinutes = nil
    }

    private func fadeOutAndStop(over seconds: TimeInterval) {
        let steps = 24
        let interval = seconds / Double(steps)
        var step = 0
        fadeTimer?.invalidate()
        fadeTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] t in
            Task { @MainActor in
                guard let self else { t.invalidate(); return }
                step += 1
                self.engine.mainMixerNode.outputVolume = self.baseVolume * Float(steps - step) / Float(steps)
                if step >= steps { t.invalidate(); self.stop() }
            }
        }
    }

    // MARK: Engine setup

    private func buildSourceNode() {
        let sr = engine.outputNode.outputFormat(forBus: 0).sampleRate
        let format = AVAudioFormat(standardFormatWithSampleRate: sr, channels: 1)!
        synth.sampleRate = Float(sr)
        let node = AVAudioSourceNode { [synth] _, _, frameCount, ablPointer -> OSStatus in
            let abl = UnsafeMutableAudioBufferListPointer(ablPointer)
            for frame in 0..<Int(frameCount) {
                let sample = synth.next()
                for buffer in abl {
                    let ptr = buffer.mData!.assumingMemoryBound(to: Float.self)
                    ptr[frame] = sample
                }
            }
            return noErr
        }
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        source = node
    }

    private func activateSession() {
        let session = AVAudioSession.sharedInstance()
        // .playback so it keeps going with the screen locked (UIBackgroundModes: audio).
        try? session.setCategory(.playback, mode: .default, options: [])
        try? session.setActive(true)
    }
}
