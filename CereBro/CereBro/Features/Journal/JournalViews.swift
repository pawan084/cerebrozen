import SwiftUI
import LocalAuthentication

// MARK: - Journal home (tab root)
struct JournalHomeView: View {
    @EnvironmentObject var state: AppState
    @State private var unlocked = false
    @State private var writeNew = false

    var body: some View {
        Group {
            if !state.journalLocked || unlocked {
                content
            } else {
                JournalLockScreen { authenticate() }
            }
        }
        .onAppear { if state.journalLocked && !unlocked { authenticate() } }
    }

    private var content: some View {
        ScreenScaffold(eyebrow: "Journal hub", title: "Journal", trailingSystemImage: "book", isRoot: true) {
            HeroCard(tag: "Prompt for tonight", title: "Release the day",
                     subtitle: "What emotion did you avoid today, and what did it need from you?",
                     cta: "Write", imageURL: Dummy.Img.journal) { writeNew = true }
            NavRow(title: "New entry", subtitle: "Private writing with consent", systemImage: "square.and.pencil", imageURL: Dummy.Img.write, emphasis: true) { JournalEntryView() }
            NavRow(title: "History", subtitle: "Past entries and tags", systemImage: "clock", imageURL: Dummy.Img.journal) { JournalHistoryView() }
            NavRow(title: "Private mode", subtitle: "Choose what AI can read", systemImage: "lock", imageURL: Dummy.Img.privacy) { PrivacyView() }
        }
        .navigationDestination(isPresented: $writeNew) { JournalEntryView() }
    }

    /// Face ID / passcode gate. If the device has no biometrics or passcode set
    /// we unlock rather than lock the user out (the lock is a privacy add-on).
    private func authenticate() {
        let ctx = LAContext()
        var error: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            unlocked = true
            return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
                           localizedReason: "Unlock your private journal") { ok, _ in
            Task { @MainActor in
                if ok { withAnimation(.easeOut(duration: 0.25)) { unlocked = true } }
            }
        }
    }
}

/// Calm lock screen shown when the Journal is gated behind Face ID / passcode.
struct JournalLockScreen: View {
    var onUnlock: () -> Void
    var body: some View {
        ZStack {
            AppBackground(accent: Theme.Palette.lav)
            VStack(spacing: 16) {
                Image(systemName: "lock.fill")
                    .appFont(38, weight: .semibold).foregroundStyle(Theme.Palette.lav)
                Text("Journal locked").displayFont(24).foregroundStyle(Theme.Palette.text)
                Text("Your reflections stay private. Unlock with Face ID or your passcode.")
                    .multilineTextAlignment(.center)
                    .appFont(13).foregroundStyle(Theme.Palette.muted)
                    .padding(.horizontal, 34)
                PrimaryButton(title: "Unlock", systemImage: "faceid", action: onUnlock)
                    .padding(.horizontal, 44).padding(.top, 6)
            }
        }
    }
}

// MARK: - Journal entry
struct JournalEntryView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @Environment(\.dismiss) private var dismiss
    @State private var text = "Today I felt pressure because of tomorrow's meeting and a fear of being judged…"
    @State private var tags: Set<String> = ["Work", "Anxiety"]
    @State private var saved = false
    var body: some View {
        ScreenScaffold(eyebrow: "Private writing with consent", title: "Journal Entry", trailingSystemImage: "book") {
            Photo(url: Dummy.Img.write, symbol: "book").frame(height: 120).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            Card(cornerRadius: 18) {
                TextField("", text: $text, axis: .vertical)
                    .appFont(13).foregroundStyle(Theme.Palette.soft)
                    .frame(minHeight: 120, alignment: .topLeading).lineLimit(5...)
            }
            ChipRow(options: ["Work", "Anxiety", "Sleep", "Gratitude"], selection: $tags)
            NavRow(title: "See AI reflection", subtitle: "AI reflection output", systemImage: "sparkles", imageURL: Dummy.Img.privacy, emphasis: true) {
                JournalInsightView(entry: JournalEntry(title: "Draft", tags: Array(tags), date: "Today",
                                                       symbol: "book", imageURL: Dummy.Img.write, body: text))
            }
            PrimaryButton(title: "Save / Continue", systemImage: "book.fill") { save() }
        }
        .celebration(trigger: $saved)
    }

    private func save() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let firstLine = trimmed.split(separator: "\n").first.map(String.init) ?? trimmed
        let title = String(firstLine.prefix(42))
        let entry = JournalEntry(title: title, tags: Array(tags), date: "Today", symbol: "book",
                                 imageURL: Dummy.Img.write, body: trimmed)
        state.journalEntries.insert(entry, at: 0)
        state.recordActivity()
        backend.mirrorJournal(entry, body: trimmed)
        saved.toggle()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { dismiss() }
    }
}

// MARK: - Journal insight (reflection derived from the actual entry)
struct JournalInsightView: View {
    /// The entry to reflect on. Nil (or an empty body) yields a gentle generic.
    var entry: JournalEntry? = nil
    @Environment(\.dismiss) private var dismiss

    private var reflection: JournalReflection.Result {
        JournalReflection.analyze(entry?.body ?? "", tags: entry?.tags ?? [])
    }

    var body: some View {
        ScreenScaffold(eyebrow: "AI reflection output", title: "Journal Insight", trailingSystemImage: "sparkles") {
            Photo(url: Dummy.Img.privacy, symbol: "sparkles").frame(height: 120).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            InsightCard(label: "Emotional theme", title: reflection.theme, detail: reflection.detail)
            NavRow(title: "Reframe this thought", subtitle: "Structured CBT", systemImage: "brain", imageURL: Dummy.Img.write) { CBTReframeView() }
            PrimaryButton(title: "Done", systemImage: "checkmark") { dismiss() }
        }
    }
}

/// Derives an emotional theme from an entry's own text + tags. A lightweight,
/// on-device analysis (works offline) so the reflection actually reflects what
/// was written — not a single hardcoded theme for every entry.
enum JournalReflection {
    struct Result { let theme: String; let detail: String }

    static func analyze(_ body: String, tags: [String]) -> Result {
        let t = (body + " " + tags.joined(separator: " ")).lowercased()
        func has(_ words: [String]) -> Bool { words.contains { t.contains($0) } }

        if has(["meeting", "deadline", "work", "boss", "judged", "fail", "perform", "presentation"]) {
            return Result(theme: "You're carrying performance pressure.",
                          detail: "There's a fear of being judged tied to work. Naming it is the first step to loosening its grip.")
        }
        if has(["sleep", "tired", "awake", "insomnia", "night", "rest"]) {
            return Result(theme: "Your mind is racing at rest.",
                          detail: "Sleep gets harder when the day hasn't been set down. A short wind-down may help close the loop.")
        }
        if has(["lonely", "alone", "isolated", "no one", "nobody"]) {
            return Result(theme: "You're feeling disconnected.",
                          detail: "Loneliness is heavy and deeply human. Reaching toward one small connection can ease it.")
        }
        if has(["angry", "frustrat", "unfair", "annoyed", "resent"]) {
            return Result(theme: "There's unspoken frustration here.",
                          detail: "Anger often guards something that matters to you. What need is sitting underneath it?")
        }
        if has(["grateful", "thankful", "win", "proud", "happy", "calm"]) {
            return Result(theme: "You're noticing what went right.",
                          detail: "Savoring small wins trains the mind to find them again. Worth holding onto.")
        }
        if has(["anx", "worry", "worried", "nervous", "overwhelm", "stress", "panic"]) {
            return Result(theme: "Anxiety is asking for your attention.",
                          detail: "Worry is the mind trying to protect you. Grounding the body first can quiet the noise.")
        }
        return Result(theme: "You showed up for yourself today.",
                      detail: "Putting feelings into words is its own kind of care. Notice what stood out as you wrote.")
    }
}

// MARK: - Journal detail (reopen a real entry)
/// Shows the actual written text of a saved entry — not a placeholder. Older
/// entries saved before bodies were persisted fall back to the AI reflection.
struct JournalDetailView: View {
    let entry: JournalEntry
    private var dateLine: String {
        let f = DateFormatter(); f.dateStyle = .medium; f.timeStyle = .short
        return f.string(from: entry.createdAt)
    }
    var body: some View {
        ScreenScaffold(eyebrow: dateLine, title: entry.title, trailingSystemImage: "book") {
            if !entry.tags.isEmpty {
                Text(entry.tags.map { "#\($0)" }.joined(separator: "  "))
                    .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if entry.body.isEmpty {
                InsightCard(label: "Reflection", title: "This entry was saved before text was kept on-device.",
                            detail: "New entries now store your full writing here.")
            } else {
                Card(cornerRadius: 18) {
                    Text(entry.body)
                        .appFont(14).foregroundStyle(Theme.Palette.soft)
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                        .textSelection(.enabled)
                }
            }
            NavRow(title: "See AI reflection", subtitle: "Emotional themes in this entry",
                   systemImage: "sparkles", imageURL: Dummy.Img.privacy, emphasis: true) { JournalInsightView(entry: entry) }
            NavRow(title: "Talk this through", subtitle: "Discuss with your companion",
                   systemImage: "mic", imageURL: Dummy.Img.voice) { ChatView() }
        }
    }
}

// MARK: - Journal history
struct JournalHistoryView: View {
    @EnvironmentObject var state: AppState
    var body: some View {
        ScreenScaffold(eyebrow: "Past entries and tags", title: "Journal History", trailingSystemImage: "book") {
            ForEach(state.journalEntries) { e in
                NavRow(title: e.title,
                       subtitle: e.tags.isEmpty ? e.date : "\(e.tags.joined(separator: " · ")) · \(e.date)",
                       systemImage: e.symbol, imageURL: e.imageURL) { JournalDetailView(entry: e) }
            }
            NavRow(title: "Private mode", subtitle: "Choose what AI can read", systemImage: "lock", imageURL: Dummy.Img.privacy) { PrivacyView() }
        }
    }
}
