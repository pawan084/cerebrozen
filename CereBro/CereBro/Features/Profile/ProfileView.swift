import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    var body: some View {
        ScreenScaffold(eyebrow: "Settings and support", title: "You", trailingSystemImage: "gearshape", isRoot: true) {
            // Profile header
            HStack(spacing: 12) {
                Circle().fill(Theme.orb).frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 2) {
                    Text(Dummy.userName).displayFont(20).foregroundStyle(Theme.Palette.text)
                    Text("\(state.companion) · \(state.language)").appFont(12).foregroundStyle(Theme.Palette.muted2)
                }
                Spacer()
            }
            .padding(14)
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.line))

            NavRow(title: backend.isConnected ? "Account" : "Sign in",
                   subtitle: backend.isConnected ? "Manage your synced account" : "Apple, Google or email — sync across devices",
                   systemImage: "person.crop.circle", imageURL: Dummy.Img.privacy, emphasis: true) { CloudSyncView() }
            NavRow(title: "Daily reminder",
                   subtitle: state.reminderEnabled ? "On · gentle daily check-in" : "Off · tap to set a gentle nudge",
                   systemImage: "bell", imageURL: Dummy.Img.bell) { RemindersView() }
            NavRow(title: "Weekly insights", subtitle: "Your progress and patterns", systemImage: "chart.line.uptrend.xyaxis", imageURL: Dummy.Img.calm) { InsightsView() }
            NavRow(title: "Privacy & memory", subtitle: "Control what CereBro remembers", systemImage: "lock", imageURL: Dummy.Img.privacy) { PrivacyView() }
            NavRow(title: "Premium plan", subtitle: "Subscription and invoices", systemImage: "crown", imageURL: Dummy.Img.premium) { PremiumView() }
            NavRow(title: "Urgent support", subtitle: "Emergency resources", systemImage: "phone.fill", imageURL: Dummy.Img.support) { CrisisView() }
            NavRow(title: "Crisis region", subtitle: CrisisDirectory.displayName(state.crisisRegion), systemImage: "globe", imageURL: Dummy.Img.privacy) { CrisisRegionView() }
            NavRow(title: "Human support", subtitle: "Coach or therapist handoff", systemImage: "person.2", imageURL: Dummy.Img.meditate) { HumanSupportView() }

            SectionTitle(title: "Legal & account", trailing: nil)
            NavRow(title: "Privacy policy", subtitle: "How we handle your data", systemImage: "lock.shield", imageURL: Dummy.Img.privacy) { PrivacyPolicyView() }
            NavRow(title: "Export my data", subtitle: "Download a full copy", systemImage: "square.and.arrow.up", imageURL: Dummy.Img.plan) { DataExportView() }
            NavRow(title: "Delete account", subtitle: "Permanently erase everything", systemImage: "trash", imageURL: Dummy.Img.support) { AccountDeletionView() }

            SectionTitle(title: "App states (demo)", trailing: nil)
            NavRow(title: "Offline mode", subtitle: "Offline support state", systemImage: "wifi.slash", imageURL: Dummy.Img.breath) { OfflineView() }
            NavRow(title: "Empty journal", subtitle: "First-time empty state", systemImage: "book", imageURL: Dummy.Img.journal) { EmptyJournalView() }
            NavRow(title: "Voice loading", subtitle: "Loading state", systemImage: "hourglass", imageURL: Dummy.Img.voice) { VoiceLoadingView() }
            NavRow(title: "Voice error", subtitle: "Network fallback", systemImage: "exclamationmark.triangle", imageURL: Dummy.Img.chat) { VoiceErrorView() }
            NavRow(title: "Free limit", subtitle: "Usage limit state", systemImage: "lock", imageURL: Dummy.Img.premium) { FreeLimitView() }

            Button { state.resetAll() } label: {
                Text("Reset & view onboarding")
                    .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.pressable)
        }
    }
}
