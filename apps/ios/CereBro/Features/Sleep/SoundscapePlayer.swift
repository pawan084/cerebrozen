import AVFoundation
import MediaPlayer
import SwiftUI

// MARK: - Soundscape kinds
/// The sleep/meditation catalogue is procedurally synthesized on-device — no
/// bundled audio files or network needed — so playback is genuinely real.
enum SoundscapeKind: Equatable {
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

// MARK: - Mixer (sums layered synth voices on the render thread)
/// A fixed set of ambient layers, each an independent `Synth` at its own volume,
/// so the user can blend e.g. rain + wind + a soft drone. Target volumes are
/// written from the main thread; the render thread reads a click-free smoothed
/// value. Volumes live in a raw buffer (not a Swift array) so there's no
/// reallocation to race with the audio callback.
private final class Mixer {
    let kinds: [SoundscapeKind]
    private let synths: [Synth]
    private let target: UnsafeMutablePointer<Float>    // set by main thread
    private let current: UnsafeMutablePointer<Float>   // smoothed, render thread only
    var count: Int { kinds.count }

    var sampleRate: Float = 44_100 { didSet { synths.forEach { $0.sampleRate = sampleRate } } }

    init(kinds: [SoundscapeKind]) {
        self.kinds = kinds
        self.synths = kinds.map { let s = Synth(); s.kind = $0; return s }
        target = .allocate(capacity: kinds.count); target.initialize(repeating: 0, count: kinds.count)
        current = .allocate(capacity: kinds.count); current.initialize(repeating: 0, count: kinds.count)
    }
    deinit { target.deallocate(); current.deallocate() }

    func setVolume(_ v: Float, at index: Int) {
        guard index >= 0 && index < count else { return }
        target[index] = max(0, min(1, v))
    }
    func volume(at index: Int) -> Float { (index >= 0 && index < count) ? target[index] : 0 }

    @inline(__always) func next() -> Float {
        var sum: Float = 0
        for i in 0..<count {
            current[i] += (target[i] - current[i]) * 0.0006   // ~ms ramp, click-free
            sum += synths[i].next() * current[i]
        }
        return sum
    }
}

/// One selectable ambient layer surfaced in the mix UI.
struct SoundLayer: Identifiable {
    let id: Int
    let name: String
    let symbol: String
    var volume: Float
}

// MARK: - Player
@MainActor
final class SoundscapePlayer: ObservableObject {
    @Published private(set) var isPlaying = false
    /// Seconds remaining on the sleep auto-stop timer, or nil when disarmed.
    @Published private(set) var sleepRemaining: Int?
    /// Master volume (0…1), adjustable live from the player.
    @Published var volume: Float = 0.7 {
        didSet { if isPlaying { engine.mainMixerNode.outputVolume = volume } }
    }

    /// The selected timer duration in minutes (drives the icon + cycling), stable
    /// while `sleepRemaining` counts down.
    private(set) var armedMinutes: Int?
    /// Back-compat name used by the UI for the armed duration.
    var sleepTimerMinutes: Int? { armedMinutes }
    /// "mm:ss" of time left before fade-out, for a live countdown label.
    var sleepRemainingText: String? {
        sleepRemaining.map { String(format: "%d:%02d", $0 / 60, $0 % 60) }
    }

    private let engine = AVAudioEngine()
    private let mixer = Mixer(kinds: SoundscapePlayer.layerKinds)
    private var source: AVAudioSourceNode?
    private var fadeTimer: Timer?
    private var sleepTimer: Timer?
    private var remoteConfigured = false
    private var nowPlayingTitle = "Soundscape"

    /// Mixable ambient layers (surfaced in the player's Mix section).
    static let layerKinds: [SoundscapeKind] = [.rain, .ocean, .wind, .meditation]
    private static let layerMeta: [(String, String)] = [
        ("Rain", "cloud.rain"), ("Ocean", "water.waves"), ("Wind", "wind"), ("Drone", "waveform.path"),
    ]
    /// Per-layer state for the UI (name, symbol, current volume).
    @Published private(set) var layers: [SoundLayer] = []

    func configure(for item: ContentItem) {
        nowPlayingTitle = item.title
        // Start with the layer matching the item at full, the rest silent.
        let primary = SoundscapeKind.from(item)
        for i in 0..<mixer.count {
            mixer.setVolume(mixer.kinds[i] == primary ? 1 : 0, at: i)
        }
        if !mixer.kinds.contains(primary) { mixer.setVolume(1, at: 0) }   // fall back to rain
        syncLayers()
        setupRemoteCommands()
    }

    // MARK: Mix (layer volumes)

    func setLayerVolume(_ v: Float, at index: Int) {
        mixer.setVolume(v, at: index)
        if index >= 0 && index < layers.count { layers[index].volume = max(0, min(1, v)) }
    }

    func toggleLayer(_ index: Int) {
        let on = mixer.volume(at: index) > 0.02
        setLayerVolume(on ? 0 : 0.7, at: index)
        Haptics.selection()
    }

    private func syncLayers() {
        layers = (0..<mixer.count).map { i in
            SoundLayer(id: i, name: Self.layerMeta[i].0, symbol: Self.layerMeta[i].1, volume: mixer.volume(at: i))
        }
    }

    func toggle() { isPlaying ? pause() : play() }

    func play() {
        activateSession()
        if source == nil { buildSourceNode() }
        engine.mainMixerNode.outputVolume = volume
        do {
            if !engine.isRunning { try engine.start() }
            isPlaying = true
            updateNowPlaying()
            Haptics.soft(intensity: 0.4)
        } catch {
            isPlaying = false
        }
    }

    func pause() {
        engine.pause()
        isPlaying = false
        updateNowPlaying()
    }

    /// Full teardown — call when leaving the player so audio never leaks.
    func stop() {
        cancelSleepTimer()
        fadeTimer?.invalidate(); fadeTimer = nil
        engine.stop()
        if let s = source { engine.detach(s); source = nil }
        isPlaying = false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: Sleep auto-stop timer (live countdown, fades out, then stops)

    /// Cycles the sleep timer: off → 15 → 30 → 45 → 60 → off.
    func cycleSleepTimer() {
        let next: Int?
        switch armedMinutes {
        case nil:  next = 15
        case 15:   next = 30
        case 30:   next = 45
        case 45:   next = 60
        default:   next = nil
        }
        if let minutes = next { armSleepTimer(minutes: minutes) } else { cancelSleepTimer() }
    }

    private let fadeLead = 12   // seconds: begin fading this long before the end

    private func armSleepTimer(minutes: Int) {
        cancelSleepTimer()
        armedMinutes = minutes
        sleepRemaining = minutes * 60
        Haptics.selection()
        sleepTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, let r = self.sleepRemaining else { return }
                let left = r - 1
                self.sleepRemaining = max(0, left)
                if left == self.fadeLead { self.fadeOutAndStop(over: TimeInterval(self.fadeLead)) }
                if left <= 0 { self.sleepTimer?.invalidate(); self.sleepTimer = nil }
            }
        }
    }

    private func cancelSleepTimer() {
        sleepTimer?.invalidate(); sleepTimer = nil
        sleepRemaining = nil
        armedMinutes = nil
    }

    // MARK: Lock-screen / Control Center (Now Playing + remote commands)

    private func setupRemoteCommands() {
        guard !remoteConfigured else { return }
        remoteConfigured = true
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in Task { @MainActor in self?.play() }; return .success }
        c.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.pause() }; return .success }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.toggle() }; return .success }
        c.stopCommand.addTarget { [weak self] _ in Task { @MainActor in self?.stop() }; return .success }
    }

    private func updateNowPlaying() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: nowPlayingTitle,
            MPMediaItemPropertyArtist: "CereBro · Sleep",
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
        ]
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
                self.engine.mainMixerNode.outputVolume = self.volume * Float(steps - step) / Float(steps)
                if step >= steps { t.invalidate(); self.stop() }
            }
        }
    }

    // MARK: Engine setup

    private func buildSourceNode() {
        let sr = engine.outputNode.outputFormat(forBus: 0).sampleRate
        let format = AVAudioFormat(standardFormatWithSampleRate: sr, channels: 1)!
        mixer.sampleRate = Float(sr)
        let node = AVAudioSourceNode { [mixer] _, _, frameCount, ablPointer -> OSStatus in
            let abl = UnsafeMutableAudioBufferListPointer(ablPointer)
            for frame in 0..<Int(frameCount) {
                let sample = mixer.next()
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
