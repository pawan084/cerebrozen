import SwiftUI

// MARK: - Voice companion (Talk tab root)
struct TalkView: View {
    @EnvironmentObject var backend: BackendService
    @EnvironmentObject var state: AppState
    @StateObject private var voice = VoiceCompanion()
    @State private var showDisclosure = false
    @State private var savedToJournal = false

    /// Backend safety layer flagged risk in the conversation (voice transcripts
    /// run through the same `/chat` crisis detection as text).
    private var inCrisis: Bool { backend.suggestions.contains { $0.action == "crisis" } }

    private var phaseColor: Color {
        switch voice.phase {
        case .recording: return Theme.Palette.soft
        case .thinking:  return Theme.Accent.sleep
        case .speaking:  return Theme.Accent.breathe
        case .error:     return Theme.Palette.danger
        default:         return Theme.Palette.muted
        }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "AI voice companion", title: "I'm listening", trailingSystemImage: "mic", isRoot: true) {
            AIDisclosureBanner { showDisclosure = true }

            Photo(url: Dummy.Img.voice, symbol: "mic").frame(height: 98).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))

            // Tap-to-talk orb — reacts to your live voice level + conversation phase.
            Button {
                Task { await voice.toggle(backend: backend) }
            } label: {
                VoiceOrb(phase: voice.phase, level: voice.level, size: 132)
                    .frame(maxWidth: .infinity)
                    .frame(height: 210)
                    .contentShape(Circle())
            }
            .buttonStyle(.pressable)
            // Only block taps while a round-trip is in flight; `.speaking` stays
            // tappable so the user can interrupt (barge-in).
            .disabled(!backend.isConnected || voice.phase.blocksInput)
            .accessibilityLabel(voice.isRecording ? "Stop recording"
                                : (voice.phase == .speaking ? "Interrupt and talk" : "Start talking"))

            Text(backend.isConnected ? voice.phase.label : "Connect cloud sync to talk live")
                .appFont(13, weight: .heavy)
                .foregroundStyle(phaseColor)
                .frame(maxWidth: .infinity)
                .contentTransition(.opacity)
                .animation(.easeInOut(duration: 0.25), value: voice.phase)

            if backend.isConnected && voice.phase == .speaking {
                Text("Tap the orb to interrupt")
                    .appFont(11, weight: .semibold)
                    .foregroundStyle(Theme.Palette.muted2)
                    .frame(maxWidth: .infinity)
                    .transition(.opacity)
            }

            LiveWaveform(level: voice.level,
                         active: voice.isRecording || voice.phase == .speaking,
                         tint: phaseColor)

            // Live exchange when connected; otherwise the static example.
            if backend.isConnected {
                if !voice.transcript.isEmpty {
                    ChatBubble(message: .init(text: voice.transcript, isUser: true))
                }
                ChatBubble(message: .init(text: voice.reply.isEmpty
                    ? "Tap the orb and tell me what's on your mind. I'll listen, then talk it through."
                    : voice.reply, isUser: false))
            } else {
                ChatBubble(message: .init(text: "It sounds like tomorrow's meeting is creating pressure. Want to calm your body first or unpack the thought?", isUser: false))
            }

            if inCrisis { CrisisBanner() }

            HStack(spacing: 8) {
                Button { saveSessionToJournal() } label: { MiniChip("Save to journal") }
                    .buttonStyle(.pressable)
                    .disabled(voice.transcript.isEmpty && voice.reply.isEmpty)
                NavigationLink { DailyPlanView() } label: { MiniChip("Add to plan") }.buttonStyle(.pressable)
                NavigationLink { ChatView() } label: { MiniChip("Text mode") }.buttonStyle(.pressable)
            }

            VStack(spacing: 10) {
                if !backend.isConnected {
                    NavRow(title: "Connect to talk live", subtitle: "Sign in to use the voice companion", systemImage: "icloud", imageURL: Dummy.Img.chat, emphasis: true) { CloudSyncView() }
                }
                NavRow(title: "Quick SOS reset", subtitle: "Fast anxiety/stress reset", systemImage: "exclamationmark.triangle", imageURL: Dummy.Img.mood, emphasis: true) { SOSView() }
                NavRow(title: "Switch to chat", subtitle: "Text fallback", systemImage: "bubble.left", imageURL: Dummy.Img.chat) { ChatView() }
            }
        }
        .aiDisclosure(show: $showDisclosure)
        .celebration(trigger: $savedToJournal)
    }

    /// Capture a short summary of the spoken exchange into the journal.
    private func saveSessionToJournal() {
        let you = voice.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        let reply = voice.reply.trimmingCharacters(in: .whitespacesAndNewlines)
        var lines: [String] = []
        if !you.isEmpty { lines.append("You: \(you)") }
        if !reply.isEmpty { lines.append("Companion: \(reply)") }
        guard !lines.isEmpty else { return }
        let stem = you.isEmpty ? "reflection" : you
        let entry = JournalEntry(title: String("Talk session — \(stem)".prefix(46)),
                                 tags: ["Talk"], date: "Today", symbol: "mic", imageURL: Dummy.Img.voice)
        state.journalEntries.insert(entry, at: 0)
        state.recordActivity()
        backend.mirrorJournal(entry, body: lines.joined(separator: "\n\n"))
        savedToJournal.toggle()
    }
}

struct MiniChip: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text)
            .appFont(12, weight: .heavy)
            .foregroundStyle(Theme.Palette.muted)
            .padding(.horizontal, 12).frame(minHeight: 36)
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).stroke(Theme.Palette.line))
    }
}

struct ChatBubble: View {
    let message: ChatMessage
    var body: some View {
        HStack {
            if message.isUser { Spacer(minLength: 40) }
            Text(message.text)
                .appFont(12.5)
                .foregroundStyle(message.isUser ? .white : Theme.Palette.soft)
                .padding(.horizontal, 13).padding(.vertical, 11)
                .background(message.isUser ? Theme.Palette.userBubble : Theme.Palette.card)
                .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
                .overlay(message.isUser ? nil : RoundedRectangle(cornerRadius: 17, style: .continuous).stroke(Theme.Palette.line))
            if !message.isUser { Spacer(minLength: 40) }
        }
    }
}

// MARK: - Chat (text fallback)
/// When signed into cloud sync, the transcript + replies come from the backend's
/// AI companion (`/chat`). Otherwise it uses the local, offline transcript.
struct ChatView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var draft = ""
    @State private var sending = false
    @State private var showDisclosure = false
    @State private var savedChat = false

    private var inCrisis: Bool { backend.suggestions.contains { $0.action == "crisis" } }

    var body: some View {
        ScreenScaffold(eyebrow: backend.isConnected ? "Live AI companion" : "Text fallback",
                       title: "AI Chat", trailingSystemImage: "mic") {
            AIDisclosureBanner { showDisclosure = true }

            Photo(url: Dummy.Img.chat, symbol: "bubble.left").frame(height: 98).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            if backend.isConnected {
                // Empty state: personalized starters from the self-reflection.
                if backend.chat.isEmpty && !backend.isStreaming {
                    ConversationStartersRail(topics: backend.starters) { sendText($0) }
                }
                // Live transcript: assistant turns can carry an inline activity.
                ForEach(backend.chat) { m in
                    ChatBubble(message: .init(text: m.text, isUser: m.role == "user"))
                    if let widget = m.widget {
                        ActivityWidgetCard(widget: widget)
                    }
                }
                // Streaming Oracle reply (token-by-token) + any inline activity.
                if backend.isStreaming {
                    ChatBubble(message: .init(text: backend.streamingText.isEmpty ? "…" : backend.streamingText, isUser: false))
                    if let w = backend.streamingWidget { ActivityWidgetCard(widget: w) }
                }
                // Approve/decline a paused write action.
                if let confirm = backend.pendingConfirm {
                    ToolConfirmCard(confirm: confirm) { approved in
                        Task { await backend.resolveConfirm(approved: approved) }
                    }
                }
            } else {
                ForEach(state.chatHistory) { m in
                    ChatBubble(message: .init(text: m.text, isUser: m.isUser))
                }
            }
            if inCrisis { CrisisBanner() }

            HStack(spacing: 8) {
                NavigationLink { CBTReframeView() } label: { MiniChip("Reframe") }.buttonStyle(.pressable)
                NavigationLink { BreathingView() } label: { MiniChip("2-min reset") }.buttonStyle(.pressable)
                Button { saveChatToJournal() } label: { MiniChip("Save to journal") }
                    .buttonStyle(.pressable)
                    .disabled(backend.chat.isEmpty && state.chatHistory.isEmpty)
            }
            // Backend-driven quick replies (activities / crisis / send).
            SuggestionChipRail(suggestions: backend.suggestions) { sendText($0) }
            HStack(spacing: 9) {
                TextField("Type a message…", text: $draft)
                    .foregroundStyle(Theme.Palette.text)
                    .autocorrectionDisabled()
                    .padding(.horizontal, 14).frame(height: 46)
                    .background(Theme.Palette.field)
                    .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                Button { send() } label: {
                    Image(systemName: sending ? "ellipsis" : "arrow.up")
                        .appFont(16, weight: .bold).foregroundStyle(Theme.Palette.ink)
                        .frame(width: 46, height: 46).background(Theme.Palette.cream, in: Circle())
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Send message")
                .disabled(sending || backend.isStreaming || backend.pendingConfirm != nil)
            }
        }
        .aiDisclosure(show: $showDisclosure)
        .celebration(trigger: $savedChat)
        .task {
            await backend.loadChat()
            // Seed the empty state with starters from the local self-reflection,
            // so they appear even before any profile sync.
            await backend.loadStarters(motivations: state.selectedMotivations,
                                       goals: state.selectedGoals, language: state.language)
        }
    }

    private func send() {
        sendText(draft)
        draft = ""
    }

    private func sendText(_ raw: String) {
        let t = raw.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty, !sending, !backend.isStreaming, backend.pendingConfirm == nil else { return }
        if backend.isConnected {
            sending = true
            Task {
                if backend.oracleAvailable {
                    await backend.sendOracle(t)        // streaming agent (tools + confirm)
                } else {
                    await backend.sendChat(t)          // deterministic fallback
                }
                sending = false
            }
        } else {
            state.chatHistory.append(.init(text: t, isUser: true))
        }
    }

    /// Summarize the current chat into a journal entry.
    private func saveChatToJournal() {
        let msgs: [(role: String, text: String)] = backend.isConnected
            ? backend.chat.map { ($0.role, $0.text) }
            : state.chatHistory.map { ($0.isUser ? "user" : "assistant", $0.text) }
        let lines = msgs.suffix(8).map { ($0.role == "user" ? "You: " : "Companion: ") + $0.text }
        guard !lines.isEmpty else { return }
        let firstUser = msgs.first(where: { $0.role == "user" })?.text ?? "reflection"
        let entry = JournalEntry(title: String("Talk session — \(firstUser)".prefix(46)),
                                 tags: ["Talk"], date: "Today", symbol: "mic", imageURL: Dummy.Img.chat)
        state.journalEntries.insert(entry, at: 0)
        state.recordActivity()
        backend.mirrorJournal(entry, body: lines.joined(separator: "\n\n"))
        savedChat.toggle()
    }
}
