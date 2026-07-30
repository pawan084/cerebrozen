import SwiftUI

/// Ref PATTERN DASHBOARD: "everything CereBro has learned about you —
/// visible, editable, and yours to delete." Statements come from
/// `/insights/patterns` with their supporting counts; deletion is real
/// (`DELETE /users/me/memory` — chat, insights, Oracle thread state).
struct PatternDashboardView: View {
    @EnvironmentObject var backend: BackendService
    @State private var patterns: [RemotePattern]?
    @State private var memories: [RemoteMemory]?
    @State private var draft = ""
    @State private var editingID: String?
    @State private var editText = ""
    @State private var memoryError: String?
    @State private var confirming = false
    @State private var status: String?

    private func sourceLabel(_ source: String) -> String {
        switch source {
        case "manual": return "You added this"
        case "confirmed": return "You approved this"
        case "onboarding": return "From onboarding"
        default: return source
        }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Transparent AI memory", title: "Pattern dashboard",
                       trailingSystemImage: "brain.head.profile") {
            Text("Everything CereBro has learned about you — visible, honest, and yours to delete.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)

            // Computed patterns: hideable, never editable — nothing is stored
            // to rewrite. Hiding is the honest equivalent of "delete this".
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Patterns it noticed")
                        .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.soft)
                    Text("Worked out from your own check-ins each time you open this — never stored. Hide one and it stops being shown or used.")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                        .fixedSize(horizontal: false, vertical: true)
                    if let learned = patterns {
                        if learned.isEmpty {
                            Text("Nothing yet. Patterns only appear once a few weeks of real check-ins support them — no guesses, ever.")
                                .appFont(13).foregroundStyle(Theme.Palette.muted)
                        } else {
                            ForEach(learned) { p in
                                HStack(alignment: .top, spacing: 10) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("· \(p.statement)")
                                            .appFont(13.5).foregroundStyle(Theme.Palette.soft)
                                        Text(p.basis)
                                            .appFont(11).foregroundStyle(Theme.Brand.cyan)
                                            .padding(.leading, 12)
                                    }
                                    Spacer(minLength: 0)
                                    Button("Hide") { hide(p.statement) }
                                        .appFont(12).foregroundStyle(Theme.Palette.muted)
                                        .frame(minHeight: 44)
                                        .accessibilityLabel("Hide: \(p.statement)")
                                }
                            }
                        }
                    } else {
                        Text(backend.isConnected ? "Looking at your data…"
                             : "Sign in to see what the AI has learned about you.")
                            .appFont(13).foregroundStyle(Theme.Palette.muted)
                    }
                }
            }

            // The user's own notes: real rows, so real edit + delete.
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("What you asked it to remember")
                        .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.soft)
                    Text("Your words, not its guesses. Edit or delete any of them.")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                    if let memoryError {
                        Text(memoryError).appFont(12).foregroundStyle(Theme.Palette.danger)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if let saved = memories {
                        if saved.isEmpty {
                            Text("Nothing saved yet.")
                                .appFont(13).foregroundStyle(Theme.Palette.muted)
                        } else {
                            ForEach(saved) { m in
                                if editingID == m.id {
                                    VStack(alignment: .leading, spacing: 8) {
                                        TextField("Edit this memory…", text: $editText, axis: .vertical)
                                            .appFont(13).foregroundStyle(Theme.Palette.soft)
                                        HStack(spacing: 16) {
                                            Button("Save") { save(m.id) }
                                                .appFont(13, weight: .semibold)
                                                .foregroundStyle(Theme.Palette.lav)
                                            Button("Cancel") { editingID = nil }
                                                .appFont(13).foregroundStyle(Theme.Palette.muted)
                                        }
                                        .frame(minHeight: 44)
                                    }
                                } else {
                                    HStack(alignment: .top, spacing: 10) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(m.body)
                                                .appFont(13.5).foregroundStyle(Theme.Palette.soft)
                                            Text(sourceLabel(m.source))
                                                .appFont(11).foregroundStyle(Theme.Palette.muted2)
                                        }
                                        Spacer(minLength: 0)
                                        Button("Edit") { editingID = m.id; editText = m.body }
                                            .appFont(12).foregroundStyle(Theme.Palette.lav)
                                            .frame(minHeight: 44)
                                            .accessibilityLabel("Edit: \(m.body)")
                                        Button("Delete") { remove(m.id) }
                                            .appFont(12).foregroundStyle(Theme.Palette.danger)
                                            .frame(minHeight: 44)
                                            .accessibilityLabel("Delete: \(m.body)")
                                    }
                                }
                            }
                        }
                    }
                    HStack(spacing: 10) {
                        TextField("Something worth remembering…", text: $draft)
                            .appFont(13).foregroundStyle(Theme.Palette.soft)
                        Button("Remember") { add() }
                            .appFont(13, weight: .semibold)
                            .foregroundStyle(draft.trimmingCharacters(in: .whitespaces).isEmpty
                                             ? Theme.Palette.muted2 : Theme.Palette.lav)
                            .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                            .frame(minHeight: 44)
                    }
                }
            }

            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Delete all memory")
                        .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("Removes chat history, computed insights, saved notes and the companion's thread memory — it starts fresh. Your journal, check-ins and sleep diary stay: they're your content, with their own controls.")
                        .appFont(13).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        if !confirming { confirming = true; return }
                        Task {
                            if let wipe = try? await APIClient.shared.deleteMemory() {
                                confirming = false
                                patterns = []
                                memories = []
                                let notes = wipe.memories ?? 0
                                status = "Memory cleared — \(wipe.chat_messages) messages, \(wipe.insights) insights and \(notes) saved notes forgotten."
                            } else {
                                status = "Couldn't delete — try again."
                            }
                        }
                    } label: {
                        Text(confirming ? "Tap again to confirm" : "Delete all memory")
                            .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.danger)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.pressable)
                    if let status { Text(status).appFont(12.5).foregroundStyle(Theme.Palette.muted) }
                }
            }
        }
        .task { await reload() }
    }

    private func reload() async {
        guard backend.isConnected else { return }
        patterns = (try? await APIClient.shared.patterns())?.patterns
        memories = (try? await APIClient.shared.memories()) ?? []
    }

    /// One failure message for every write here: the likeliest cause by far is
    /// the `ai_memory` consent switch being off, and saying so is more useful
    /// than surfacing a 403.
    private func failed() {
        memoryError = "Couldn't save — is AI memory switched on in Privacy & Memory?"
    }

    private func add() {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        Task {
            do {
                try await APIClient.shared.addMemory(body)
                draft = ""; memoryError = nil
                await reload()
            } catch { failed() }
        }
    }

    private func save(_ id: String) {
        let body = editText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        Task {
            do {
                try await APIClient.shared.editMemory(id: id, body: body)
                editingID = nil; memoryError = nil
                await reload()
            } catch { failed() }
        }
    }

    private func remove(_ id: String) {
        Task {
            do {
                try await APIClient.shared.deleteMemory(id: id)
                await reload()
            } catch { failed() }
        }
    }

    private func hide(_ statement: String) {
        Task {
            do {
                try await APIClient.shared.suppressPattern(statement)
                await reload()
            } catch { failed() }
        }
    }
}
