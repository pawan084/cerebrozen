import AVFoundation
import Foundation
import SwiftUI

/// Drives the full voice loop for the Talk tab:
///   mic → /voice/stt (Deepgram) → /chat (LLM) → /voice/tts (ElevenLabs) → play
///
/// Recording uses AAC/m4a (mono, 16 kHz) which Deepgram accepts directly. The
/// loop requires an authenticated cloud session, since the voice endpoints are
/// behind auth; when not connected the Talk tab keeps its offline UI.
@MainActor
final class VoiceCompanion: NSObject, ObservableObject {
    enum Phase: Equatable {
        case idle
        case recording
        case transcribing
        case thinking
        case speaking
        case error(String)

        var label: String {
            switch self {
            case .idle: return "Tap to talk"
            case .recording: return "Listening…"
            case .transcribing: return "Hearing you…"
            case .thinking: return "Thinking…"
            case .speaking: return "Speaking…"
            case .error(let m): return m
            }
        }
        var isBusy: Bool {
            switch self { case .transcribing, .thinking, .speaking: return true; default: return false }
        }
        /// Phases where input must be blocked — a server round-trip is in flight.
        /// `.speaking` is deliberately excluded so the user can tap to interrupt.
        var blocksInput: Bool {
            switch self { case .transcribing, .thinking: return true; default: return false }
        }
    }

    /// One completed spoken exchange (kept so a whole session can be journaled,
    /// not just the last turn).
    struct Turn: Identifiable { let id = UUID(); let you: String; let reply: String }

    @Published private(set) var phase: Phase = .idle
    @Published private(set) var transcript: String = ""
    @Published private(set) var reply: String = ""
    /// The full multi-turn session so far.
    @Published private(set) var turns: [Turn] = []
    /// Smoothed live audio level (0…1) — mic input while recording, playback
    /// envelope while speaking. Drives the reactive orb + waveform.
    @Published private(set) var level: CGFloat = 0

    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private var fileURL: URL?
    private var meterTimer: Timer?
    /// Plays streamed TTS sentences back-to-back (low-latency speaking).
    private let queue = SentenceQueuePlayer()
    /// The in-flight STT→LLM→TTS pipeline, so a tap can cancel it.
    private var processing: Task<Void, Never>?
    private weak var currentBackend: BackendService?
    // Voice-activity endpointing (auto-stop on trailing silence).
    private var sawSpeech = false
    private var silenceFrames = 0

    var isRecording: Bool { phase == .recording }

    private var interruptionObserver: NSObjectProtocol?

    override init() {
        super.init()
        // A phone call / Siri / route change shouldn't leave the orb stuck
        // recording or speaking — reset to idle when an interruption begins.
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            guard let self,
                  let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  AVAudioSession.InterruptionType(rawValue: raw) == .began else { return }
            MainActor.assumeIsolated { self.handleInterruption() }
        }
    }

    deinit {
        if let o = interruptionObserver { NotificationCenter.default.removeObserver(o) }
    }

    private func handleInterruption() {
        recorder?.stop(); recorder = nil
        player?.stop(); player = nil
        queue.stop()
        processing?.cancel(); processing = nil
        stopMetering()
        if phase != .idle { phase = .idle }
    }

    // MARK: Permission

    func requestPermission() async -> Bool {
        await withCheckedContinuation { cont in
            AVAudioApplication.requestRecordPermission { granted in cont.resume(returning: granted) }
        }
    }

    // MARK: Record

    func toggle(backend: BackendService) async {
        Haptics.tap(.medium)
        switch phase {
        case .recording:
            await stopAndProcess(backend: backend)
        case .transcribing, .thinking:
            cancelProcessing()          // tap again to cancel a slow request
        case .speaking:
            stopPlayback()              // barge-in: cut the reply short…
            await startRecording(backend: backend)   // …and start listening immediately
        default:
            await startRecording(backend: backend)
        }
    }

    /// Stop any in-progress playback (used for tap-to-interrupt / barge-in).
    private func stopPlayback() {
        player?.stop(); player = nil
        queue.stop()
        processing?.cancel(); processing = nil
        stopMetering()
        phase = .idle
    }

    /// Cancel an in-flight STT/LLM/TTS round-trip and return to idle.
    private func cancelProcessing() {
        processing?.cancel(); processing = nil
        queue.stop()
        stopMetering()
        phase = .idle
        Haptics.tap(.light)
    }

    private func startRecording(backend: BackendService) async {
        guard !phase.isBusy else { return }
        guard await requestPermission() else {
            phase = .error("Microphone access is off. Enable it in Settings.")
            return
        }
        currentBackend = backend
        sawSpeech = false; silenceFrames = 0
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)

            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("cerebro-speech.m4a")
            let settings: [String: Any] = [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVSampleRateKey: 16_000,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
            ]
            let rec = try AVAudioRecorder(url: url, settings: settings)
            rec.isMeteringEnabled = true
            rec.record()
            recorder = rec
            fileURL = url
            transcript = ""; reply = ""
            phase = .recording
            startMetering()
        } catch {
            phase = .error("Couldn't start recording.")
        }
    }

    private func stopAndProcess(backend: BackendService) async {
        recorder?.stop()
        recorder = nil
        stopMetering()
        guard let url = fileURL, let data = try? Data(contentsOf: url), !data.isEmpty else {
            phase = .error("No audio captured. Try again.")
            Haptics.warning()
            return
        }
        // Run the round-trip as a cancellable task so a tap can abort it.
        processing = Task { await runPipeline(data: data, backend: backend) }
    }

    private func runPipeline(data: Data, backend: BackendService) async {
        // 1. Speech → text (Deepgram)
        phase = .transcribing
        let heard: String
        do {
            heard = try await APIClient.shared.transcribe(audio: data, contentType: "audio/m4a")
        } catch {
            if !Task.isCancelled { phase = .error(message(error)); Haptics.warning() }
            return
        }
        if Task.isCancelled { return }
        guard !heard.isEmpty else {
            phase = .error("I didn't catch that. Try again."); Haptics.warning(); return
        }
        transcript = heard

        // 2. Reply. Stream through the Oracle (speak sentence-by-sentence, low
        //    latency) when available; otherwise the deterministic /chat + one TTS.
        phase = .thinking
        if backend.oracleAvailable {
            await speakStreaming(user: heard, backend: backend)
        } else {
            guard let answer = await backend.sendChatGetReply(heard) else {
                if !Task.isCancelled { phase = .error("Couldn't reach the companion."); Haptics.warning() }
                return
            }
            if Task.isCancelled { return }
            reply = answer
            turns.append(Turn(you: heard, reply: answer))
            Haptics.success()
            phase = .speaking
            do {
                let audio = try await APIClient.shared.synthesize(text: answer)
                if Task.isCancelled { return }
                try play(audio); startMetering()
            } catch {
                phase = .idle   // TTS optional — the text reply already landed
            }
        }
    }

    /// Stream the Oracle reply and speak each sentence as soon as it's ready.
    private func speakStreaming(user: String, backend: BackendService) async {
        queue.reset()
        queue.onDrained = { [weak self] in
            Task { @MainActor in
                self?.stopMetering()
                if self?.phase == .speaking { self?.phase = .idle }
            }
        }
        var buffer = "", full = ""
        var started = false
        do {
            for try await token in backend.oracleReplyStream(user) {
                if Task.isCancelled { queue.stop(); return }
                buffer += token; full += token
                while let sentence = Self.popSentence(&buffer) {
                    await synthesizeAndEnqueue(sentence, started: &started)
                }
            }
        } catch {
            // Stream failed — speak whatever completed.
        }
        if Task.isCancelled { queue.stop(); return }
        let tail = buffer.trimmingCharacters(in: .whitespacesAndNewlines)
        if !tail.isEmpty { await synthesizeAndEnqueue(tail, started: &started) }

        reply = full
        if !full.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            turns.append(Turn(you: user, reply: full)); Haptics.success()
        }
        queue.finish()
        if !started { phase = .idle }   // nothing was spoken — settle
    }

    private func synthesizeAndEnqueue(_ text: String, started: inout Bool) async {
        guard let audio = try? await APIClient.shared.synthesize(text: text), !Task.isCancelled else { return }
        queue.enqueue(audio)
        if !started {
            started = true
            phase = .speaking
            startMetering()
        }
    }

    /// Pop the first complete sentence (through terminal punctuation) off `buffer`.
    private static func popSentence(_ buffer: inout String) -> String? {
        guard let idx = buffer.firstIndex(where: { ".!?".contains($0) }) else { return nil }
        let next = buffer.index(after: idx)
        let sentence = String(buffer[..<next]).trimmingCharacters(in: .whitespacesAndNewlines)
        buffer = String(buffer[next...])
        return sentence.isEmpty ? nil : sentence
    }

    // MARK: Playback

    private func play(_ data: Data) throws {
        let p = try AVAudioPlayer(data: data)
        p.isMeteringEnabled = true
        p.delegate = self
        p.prepareToPlay()
        p.play()
        player = p
    }

    // MARK: Audio metering (drives the reactive orb)

    private func startMetering() {
        meterTimer?.invalidate()
        meterTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.sampleLevel() }
        }
    }

    private func sampleLevel() {
        var power: Float = -160
        if isRecording, let r = recorder {
            r.updateMeters(); power = r.averagePower(forChannel: 0)
            endpoint(power)                       // auto-stop on trailing silence
        } else if phase == .speaking {
            if let p = player {
                p.updateMeters(); power = p.averagePower(forChannel: 0)
            } else if let q = queue.current {     // streaming TTS clip
                q.updateMeters(); power = q.averagePower(forChannel: 0)
            }
        }
        // Map dBFS (-50…0) to 0…1 and smooth for fluid motion.
        let norm = max(0, min(1, (CGFloat(power) + 50) / 50))
        level = level * 0.55 + norm * 0.45
    }

    /// Voice-activity endpointing: once we've heard speech, ~1.5s of trailing
    /// silence auto-ends the turn — natural turn-taking without a manual stop tap.
    private func endpoint(_ power: Float) {
        if power > -35 { sawSpeech = true; silenceFrames = 0 }
        else if sawSpeech { silenceFrames += 1 }
        if sawSpeech && silenceFrames > 45 {      // ~45 frames @ 30fps ≈ 1.5s
            sawSpeech = false; silenceFrames = 0
            if let backend = currentBackend { Task { await stopAndProcess(backend: backend) } }
        }
    }

    private func stopMetering() {
        meterTimer?.invalidate(); meterTimer = nil
        level = 0
    }

    private func message(_ error: Error) -> String {
        (error as? APIError)?.errorDescription ?? "Something went wrong. Try again."
    }
}

extension VoiceCompanion: AVAudioPlayerDelegate {
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.stopMetering(); self.phase = .idle }
    }
}
