import AVFoundation
import SwiftUI

// MARK: - Tool background sound

/// Which bundled seamless loop (Resources/Sounds/<raw>.m4a) backs a tool session.
enum ToolSound: String {
    case rain, ocean, wind, drone
}

/// Plays one quiet looping ambience behind a tool session (breathing, grounding,
/// CBT, micro-activities).
///
/// Deliberately lighter than the Sleep `SoundscapePlayer`: a single
/// `AVAudioPlayer` on an `.ambient`, mix-with-others session, so it respects the
/// silent switch, never fights the user's own music or the sleep player, and
/// needs no background mode — the sound belongs to the screen, not the app.
///
/// Ownership: SwiftUI fires the *incoming* screen's `onAppear` before the
/// *outgoing* screen's `onDisappear` on push/pop, so start/stop can't be paired
/// naively — the old screen would silence the new one (and a same-sound handoff
/// like CBT → Balanced Thought would cut out). Each screen therefore claims the
/// player with a session token; a stale screen's `stop` is a no-op.
@MainActor
final class ToolAmbience: ObservableObject {
    static let shared = ToolAmbience()

    @Published private(set) var playing: ToolSound?

    /// Same UITest gate as `SoundscapePlayer`: under `-resetState` (only ever
    /// passed by the UITest launcher) we track state without touching CoreAudio,
    /// so the suite can't hang on the Simulator's audio HAL.
    private let audioEnabled = !ProcessInfo.processInfo.arguments.contains("-resetState")
    private var player: AVAudioPlayer?
    private var owner: UUID?
    private var fadeTimer: Timer?
    private let targetVolume: Float = 0.32   // quiet bed, never foreground

    func play(_ sound: ToolSound, owner id: UUID) {
        owner = id
        if playing == sound { return }        // seamless same-sound handoff
        playing = sound
        guard audioEnabled else { return }
        fadeTimer?.invalidate(); fadeTimer = nil
        player?.stop()
        guard let url = Bundle.main.url(forResource: sound.rawValue, withExtension: "m4a"),
              let p = try? AVAudioPlayer(contentsOf: url) else { player = nil; return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)
        p.numberOfLoops = -1
        p.volume = 0
        p.play()
        player = p
        fade(p, to: targetVolume)
    }

    /// Stops only when called by the screen that currently owns the sound.
    func stop(owner id: UUID) {
        guard owner == id else { return }
        owner = nil
        playing = nil
        guard audioEnabled, let p = player else { return }
        player = nil
        fade(p, to: 0) { p.stop() }
    }

    /// ~0.7 s linear ramp so starts/stops are click-free.
    private func fade(_ p: AVAudioPlayer, to target: Float, then completion: (() -> Void)? = nil) {
        fadeTimer?.invalidate()
        let steps = 14
        let delta = (target - p.volume) / Float(steps)
        var step = 0
        fadeTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { t in
            Task { @MainActor in
                step += 1
                p.volume += delta
                if step >= steps {
                    t.invalidate()
                    p.volume = target
                    completion?()
                }
            }
        }
    }
}

// MARK: - Screen modifier + mute toggle

/// Attaches a background sound to a tool screen: starts on appear (honoring the
/// persisted preference), hands off/stops on disappear, and floats a small mute
/// toggle over the content.
private struct ToolAmbienceModifier: ViewModifier {
    let sound: ToolSound
    @EnvironmentObject private var state: AppState
    @ObservedObject private var ambience = ToolAmbience.shared
    @State private var sessionID = UUID()

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottomTrailing) { toggle.padding(18) }
            .onAppear { if state.toolSoundOn { ambience.play(sound, owner: sessionID) } }
            .onDisappear { ambience.stop(owner: sessionID) }
    }

    private var toggle: some View {
        Button {
            state.toolSoundOn.toggle()
            if state.toolSoundOn {
                ambience.play(sound, owner: sessionID)
            } else {
                ambience.stop(owner: sessionID)
            }
            Haptics.selection()
        } label: {
            Image(systemName: state.toolSoundOn ? "speaker.wave.2.fill" : "speaker.slash.fill")
                .appFont(15, weight: .semibold)
                .foregroundStyle(state.toolSoundOn ? Theme.Palette.soft : Theme.Palette.muted)
                .frame(width: 40, height: 40)
                .background(Theme.Stroke.iconWell, in: Circle())
                .overlay(Circle().stroke(Theme.Palette.line))
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Background sound")
        .accessibilityValue(state.toolSoundOn ? "On" : "Off")
    }
}

extension View {
    /// Quiet looping ambience for a tool session, with a floating mute toggle.
    func toolAmbience(_ sound: ToolSound) -> some View {
        modifier(ToolAmbienceModifier(sound: sound))
    }
}
