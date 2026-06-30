import SwiftUI
import LocalAuthentication

// MARK: - Journal home (tab root)
struct JournalHomeView: View {
    @EnvironmentObject var state: AppState
    @State private var unlocked = false

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
                     cta: "Write", imageURL: Dummy.Img.journal)
            NavRow(title: "New entry", subtitle: "Private writing with consent", systemImage: "square.and.pencil", imageURL: Dummy.Img.write, emphasis: true) { JournalEntryView() }
            NavRow(title: "History", subtitle: "Past entries and tags", systemImage: "clock", imageURL: Dummy.Img.journal) { JournalHistoryView() }
            NavRow(title: "Private mode", subtitle: "Choose what AI can read", systemImage: "lock", imageURL: Dummy.Img.privacy) { PrivacyView() }
        }
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
            NavRow(title: "See AI reflection", subtitle: "AI reflection output", systemImage: "sparkles", imageURL: Dummy.Img.privacy, emphasis: true) { JournalInsightView() }
            PrimaryButton(title: "Save / Continue", systemImage: "book.fill") { save() }
        }
        .celebration(trigger: $saved)
    }

    private func save() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let firstLine = trimmed.split(separator: "\n").first.map(String.init) ?? trimmed
        let title = String(firstLine.prefix(42))
        let entry = JournalEntry(title: title, tags: Array(tags), date: "Today", symbol: "book", imageURL: Dummy.Img.write)
        state.journalEntries.insert(entry, at: 0)
        state.recordActivity()
        backend.mirrorJournal(entry, body: trimmed)
        saved.toggle()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { dismiss() }
    }
}

// MARK: - Journal insight
struct JournalInsightView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "AI reflection output", title: "Journal Insight", trailingSystemImage: "sparkles") {
            Photo(url: Dummy.Img.privacy, symbol: "sparkles").frame(height: 120).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            InsightCard(label: "Emotional theme", title: "You're carrying performance pressure.",
                        detail: "There is a fear of being judged connected to upcoming work pressure.")
            NavRow(title: "Reframe this thought", subtitle: "Structured CBT", systemImage: "brain", imageURL: Dummy.Img.write) { CBTReframeView() }
            PrimaryButton(title: "Save / Continue", systemImage: "book.fill")
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
                       systemImage: e.symbol, imageURL: e.imageURL) { JournalInsightView() }
            }
            NavRow(title: "Private mode", subtitle: "Choose what AI can read", systemImage: "lock", imageURL: Dummy.Img.privacy) { PrivacyView() }
        }
    }
}
