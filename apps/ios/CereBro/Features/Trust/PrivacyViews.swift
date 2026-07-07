import SwiftUI

// MARK: - Privacy & memory dashboard
struct PrivacyView: View {
    @EnvironmentObject var state: AppState
    /// DPDP s.5(3): the consent notice is readable in English or an
    /// Eighth-Schedule language — the option lives right on the notice.
    @State private var noticeLang = "en"
    private var notice: ConsentNotice { ConsentNotices.notice(noticeLang) }
    var body: some View {
        ScreenScaffold(eyebrow: "Data control dashboard", title: "Privacy & Memory", trailingSystemImage: "lock") {
            HStack { NoticeLanguageMenu(code: $noticeLang); Spacer() }
            SettingsGroup {
                ToggleRow(title: notice.category("mood_history").label, subtitle: notice.category("mood_history").hint, isOn: $state.consent.moodHistory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: notice.category("ai_memory").label, subtitle: notice.category("ai_memory").hint, isOn: $state.consent.aiMemory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: notice.category("journal_memory").label, subtitle: notice.category("journal_memory").hint, isOn: $state.consent.journalMemory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: notice.category("sleep_history").label, subtitle: notice.category("sleep_history").hint, isOn: $state.consent.sleepHistory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: notice.category("voice_storage").label, subtitle: notice.category("voice_storage").hint, isOn: $state.consent.voiceStorage); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: notice.category("model_training").label, subtitle: notice.category("model_training").hint, isOn: $state.consent.modelTraining); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: "Anonymous usage stats", subtitle: "Counts only — never your content or account", isOn: $state.usageStatsOn); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: "Lock journal", subtitle: "Require Face ID / passcode", isOn: $state.journalLocked)
            }
            .onAppear { noticeLang = ConsentNotices.defaultCode(forAppLanguage: state.language) }
            Text("Usage stats are allowlisted counts (like which onboarding step was reached) tied to a random install id on our own servers — no third-party SDKs, never linked to you or your content.")
                .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                .fixedSize(horizontal: false, vertical: true)
            NavRow(title: "Memory detail", subtitle: "Editable AI memory", systemImage: "brain", imageURL: Dummy.Img.write) {
                MemoryDetailView(item: Dummy.memoryItems[0])
            }
            NavRow(title: "Export report", subtitle: "Choose sections and date range", systemImage: "square.and.arrow.up", imageURL: Dummy.Img.plan) { ExportReportView() }
            NavRow(title: "Delete memory", subtitle: "Typed confirmation required", systemImage: "trash", imageURL: Dummy.Img.privacy) { DeleteDataView() }
        }
    }
}

// MARK: - Export report
struct ExportReportView: View {
    @EnvironmentObject var state: AppState
    @State private var sections: Set<String> = ["Mood history", "Journal insights"]
    @State private var dateRange: Set<String> = ["Last 30 days"]
    @State private var report: String?
    var body: some View {
        ScreenScaffold(eyebrow: "Shareable report flow", title: "Export Report", trailingSystemImage: "square.and.arrow.up") {
            Text("Choose what to include. Export is generated on-device.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
            ChipRow(options: ["Mood history", "Journal insights", "Patterns", "Sleep data", "Plan"], selection: $sections)
            SectionTitle(title: "Date range", trailing: nil)
            ChipRow(options: ["Last 7 days", "Last 30 days", "All time"], selection: $dateRange, singleSelect: true)
            PrimaryButton(title: "Generate report", systemImage: "doc.text") { report = buildReport() }
            if let report {
                Card(cornerRadius: 18) {
                    Text(report).appFont(12).foregroundStyle(Theme.Palette.soft)
                        .frame(maxWidth: .infinity, alignment: .leading).textSelection(.enabled)
                }
                ShareLink(item: report, preview: SharePreview("CereBro report")) {
                    Text("Share report").appFont(14, weight: .heavy).foregroundStyle(Theme.Palette.ink)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(Theme.Palette.cream, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }
        }
    }

    /// Build a plain-text report on-device from the selected sections.
    private func buildReport() -> String {
        let range = dateRange.first ?? "All time"
        var lines = ["CereBro report · \(range)", ""]
        if sections.contains("Mood history") { lines.append("Mood check-ins: \(state.moodLogs.count)") }
        if sections.contains("Journal insights") { lines.append("Journal entries: \(state.journalEntries.count)") }
        if sections.contains("Plan") { lines.append("Plan steps completed: \(state.completedSteps.count)") }
        if sections.contains("Patterns") { lines.append("Current streak: \(state.currentStreak) days (best \(state.bestStreak))") }
        if state.hasBaseline { lines.append("Baseline — stress \(state.baselineStress)/5, sleep \(state.baselineSleep)/5") }
        Haptics.success()
        return lines.joined(separator: "\n")
    }
}

// MARK: - Privacy policy (in-app, App Store requirement)
struct PrivacyPolicyView: View {
    private let updated = "4 July 2026"
    var body: some View {
        ScreenScaffold(eyebrow: "How we handle your data", title: "Privacy Policy", trailingSystemImage: "lock.shield") {
            Text("Last updated \(updated)").appFont(12).foregroundStyle(Theme.Palette.muted2)
            policy("Privacy by design",
                   "CereBro collects as little as possible and puts you in charge of what's remembered. It's wellness support — not a medical service, and never a substitute for professional care or emergency help.")
            policy("What we collect",
                   "Your account (email, name, or an Apple identifier), onboarding choices (language, companion, self-reflection), and the wellness data you create — mood check-ins, journal entries, chats, and plan progress. Voice audio storage is off by default.")
            policy("How we use it",
                   "To run your plan, insights, journal, and companion, and to personalize gentle nudges and conversation starters from your own check-ins. We use your content to train models only if you explicitly opt in.")
            policy("AI & voice providers",
                   "To generate replies and process voice we send the minimum necessary text to trusted processors (OpenAI/Anthropic, Deepgram, ElevenLabs). Core features still work with AI disabled.")
            policy("Your controls",
                   "Change what the AI remembers anytime in Privacy & Memory. Export a full copy of your data, or permanently delete your account and all associated data — both from inside the app.")
            policy("Contact & grievances",
                   "Questions, requests, or complaints: grievance@cerebrozen.in — we respond within 90 days and include this contact in every reply. India: after using this channel you may approach the Data Protection Board.")

            Link(destination: URL(string: "https://cerebrozen.in/privacy")!) {
                HStack(spacing: 6) {
                    Text("Read the full policy online").appFont(13, weight: .heavy)
                    Image(systemName: "arrow.up.right.square")
                }.foregroundStyle(Theme.Palette.lav)
            }
            .padding(.top, 4)
            DangerPanel {
                Text("CereBro is wellness support, not emergency care. If you're in immediate danger, contact your local emergency services.")
                    .appFont(12).foregroundStyle(Theme.Palette.muted)
            }
        }
    }

    private func policy(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).appFont(14.5, weight: .semibold).foregroundStyle(Theme.Palette.text)
            Text(body).appFont(12.5).foregroundStyle(Theme.Palette.soft)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Data export (download a copy)
struct DataExportView: View {
    @EnvironmentObject var backend: BackendService
    @State private var exportText: String?
    @State private var loading = false
    @State private var failed = false

    var body: some View {
        ScreenScaffold(eyebrow: "Data portability", title: "Export My Data", trailingSystemImage: "square.and.arrow.up") {
            Text("Download everything CereBro stores for you — profile, moods, journal, chats, plans, nudges, and insights — as a JSON file.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            if !backend.isConnected {
                DangerPanel {
                    Text("Sign in first — export pulls your data from the server.")
                        .appFont(12).foregroundStyle(Theme.Palette.muted)
                }
            } else if let text = exportText {
                Card(cornerRadius: 18) {
                    Text("Your export is ready.").appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }
                ShareLink(item: text,
                          preview: SharePreview("CereBro data export")) {
                    Text("Share / save export")
                        .appFont(14, weight: .heavy).foregroundStyle(Theme.Palette.ink)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(Theme.Palette.cream, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            } else {
                PrimaryButton(title: loading ? "Preparing…" : "Prepare export",
                              systemImage: "doc.text") {
                    Task {
                        loading = true; failed = false
                        if let data = await backend.exportData(),
                           let s = String(data: data, encoding: .utf8) { exportText = s }
                        else { failed = true }
                        loading = false
                    }
                }
                .disabled(loading)
                if failed {
                    Text("Couldn't prepare the export. Please try again.")
                        .appFont(12).foregroundStyle(Theme.Palette.danger)
                }
            }
        }
    }
}

// MARK: - Delete account (destructive, server + local)
struct AccountDeletionView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @Environment(\.dismiss) private var dismiss
    @State private var confirmation = ""
    @State private var working = false

    var body: some View {
        ScreenScaffold(eyebrow: "Permanent account deletion", title: "Delete Account", trailingSystemImage: "trash") {
            DangerPanel {
                VStack(alignment: .leading, spacing: 4) {
                    Text("This permanently deletes your account and all data.").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("Moods, journal, chats, plans and insights are erased on our servers and this device. Type DELETE to confirm — this can't be undone.")
                        .appFont(12).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Card(cornerRadius: 18) {
                TextField("Type DELETE", text: $confirmation)
                    .foregroundStyle(Theme.Palette.text).autocorrectionDisabled()
                    .textInputAutocapitalization(.characters)
            }
            Button {
                Task {
                    working = true
                    await backend.deleteAccount()   // server cascade + sign out
                    state.resetAll()                // wipe local + return to onboarding
                    working = false
                    dismiss()
                }
            } label: {
                Text(working ? "Deleting…" : "Delete my account")
                    .appFont(14, weight: .heavy)
                    .foregroundStyle(canDelete ? .white : Theme.Palette.muted2)
                    .frame(maxWidth: .infinity).frame(height: 52)
                    .background(canDelete ? Theme.Palette.danger : Theme.Palette.card,
                                in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.pressable)
            .disabled(!canDelete || working)
            .accessibilityLabel("Delete my account")
        }
    }

    private var canDelete: Bool { confirmation == "DELETE" }
}

// MARK: - Delete data (destructive)
struct DeleteDataView: View {
    @EnvironmentObject var state: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var confirmation = ""
    var body: some View {
        ScreenScaffold(eyebrow: "Destructive action confirmation", title: "Delete Data", trailingSystemImage: "trash") {
            DangerPanel {
                VStack(alignment: .leading, spacing: 4) {
                    Text("This permanently deletes what CereBro remembers.").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("Clears your chat history and mood check-ins from this device. Your journal is kept. Type DELETE to confirm — this can't be undone.")
                        .appFont(12).foregroundStyle(Theme.Palette.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
            Card(cornerRadius: 18) {
                TextField("Type DELETE", text: $confirmation)
                    .foregroundStyle(Theme.Palette.text).autocorrectionDisabled()
                    .textInputAutocapitalization(.characters)
            }
            Button { deleteMemory() } label: {
                Text("Delete everything")
                    .appFont(14, weight: .heavy)
                    .foregroundStyle(confirmation == "DELETE" ? .white : Theme.Palette.muted2)
                    .frame(maxWidth: .infinity).frame(height: 52)
                    .background(confirmation == "DELETE" ? Theme.Palette.danger : Theme.Palette.card,
                                in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.pressable)
            .disabled(confirmation != "DELETE")
            .accessibilityLabel("Delete everything CereBro remembers")
        }
    }

    private func deleteMemory() {
        state.chatHistory = []
        state.moodLogs = []
        Haptics.success()
        dismiss()
    }
}
