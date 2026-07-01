import AVFoundation
import SwiftUI

/// Plays a queue of synthesized audio clips back-to-back. The voice loop feeds it
/// one sentence of TTS at a time, so the companion can *start speaking the first
/// sentence while later ones are still being generated* — collapsing the long
/// "Thinking…" dead air of a synthesize-the-whole-reply-then-play approach.
@MainActor
final class SentenceQueuePlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var isActive = false
    /// Called when the queue drains and nothing more is expected.
    var onDrained: (() -> Void)?

    private var queue: [Data] = []
    private var player: AVAudioPlayer?
    /// True once the producer signals it has enqueued the final clip.
    private var producerFinished = false

    /// The currently-playing clip, exposed so the orb can meter its level.
    var current: AVAudioPlayer? { player }

    func reset() {
        player?.stop(); player = nil
        queue.removeAll()
        producerFinished = false
        isActive = false
    }

    /// Enqueue a clip; starts playback immediately if idle.
    func enqueue(_ data: Data) {
        queue.append(data)
        if player == nil { playNext() }
    }

    /// Signal that no more clips are coming; drains when the queue empties.
    func finish() {
        producerFinished = true
        if player == nil && queue.isEmpty { drain() }
    }

    func stop() { reset() }

    private func playNext() {
        guard !queue.isEmpty else {
            player = nil
            if producerFinished { drain() }
            return
        }
        let data = queue.removeFirst()
        do {
            let p = try AVAudioPlayer(data: data)
            p.delegate = self
            p.isMeteringEnabled = true
            p.prepareToPlay()
            p.play()
            player = p
            isActive = true
        } catch {
            playNext()   // skip a bad clip
        }
    }

    private func drain() {
        isActive = false
        onDrained?()
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.player = nil
            self.playNext()
        }
    }
}
