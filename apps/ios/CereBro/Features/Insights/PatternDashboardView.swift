import SwiftUI

/// Ref PATTERN DASHBOARD: "everything CereBro has learned about you —
/// visible, editable, and yours to delete." Statements come from
/// `/insights/patterns` with their supporting counts; deletion is real
/// (`DELETE /users/me/memory` — chat, insights, Oracle thread state).
struct PatternDashboardView: View {
    @EnvironmentObject var backend: BackendService
    @State private var patterns: [RemotePattern]?
    @State private var confirming = false
    @State private var status: String?

    var body: some View {
        ScreenScaffold(eyebrow: "Transparent AI memory", title: "Pattern dashboard",
                       trailingSystemImage: "brain.head.profile") {
            Text("Everything CereBro has learned about you — visible, honest, and yours to delete.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)

            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("What CereBro remembers")
                        .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.soft)
                    if let learned = patterns {
                        if learned.isEmpty {
                            Text("Nothing yet. Patterns only appear once a few weeks of real check-ins support them — no guesses, ever.")
                                .appFont(13).foregroundStyle(Theme.Palette.muted)
                        } else {
                            ForEach(learned) { p in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("· \(p.statement)")
                                        .appFont(13.5).foregroundStyle(Theme.Palette.soft)
                                    Text(p.basis)
                                        .appFont(11).foregroundStyle(Theme.Brand.cyan)
                                        .padding(.leading, 12)
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

            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Delete all memory")
                        .appFont(15, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("Removes chat history, computed insights and the companion's thread memory — it starts fresh. Your journal, check-ins and sleep diary stay: they're your content, with their own controls.")
                        .appFont(13).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        if !confirming { confirming = true; return }
                        Task {
                            if let wipe = try? await APIClient.shared.deleteMemory() {
                                confirming = false
                                patterns = []
                                status = "Memory cleared — \(wipe.chat_messages) messages and \(wipe.insights) insights forgotten."
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
        .task {
            guard backend.isConnected else { return }
            patterns = (try? await APIClient.shared.patterns())?.patterns
        }
    }
}
