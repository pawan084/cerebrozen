import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var showReset = false

    /// Real account name when signed in; a neutral placeholder otherwise.
    private var displayName: String {
        if let name = backend.user?.name, !name.isEmpty { return name }
        return backend.isConnected ? "You" : Dummy.userName
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Settings and support", title: "You", trailingSystemImage: "gearshape", isRoot: true) {
            // Profile header
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Theme.orb)
                    // variableColor only while genuinely live (synced account);
                    // the signed-out glyph is static chrome.
                    NativeEffectIcon(systemImage: backend.isConnected ? "checkmark.icloud.fill" : "person.fill",
                                     size: 22,
                                     weight: .semibold,
                                     color: Theme.Palette.ink,
                                     effect: backend.isConnected ? .variableColor : .none)
                }
                .frame(width: 56, height: 56)
                .shadow(color: Theme.Palette.lav.opacity(0.38), radius: 16, y: 6)
                VStack(alignment: .leading, spacing: 2) {
                    Text(displayName).displayFont(20).foregroundStyle(Theme.Palette.text)
                    Text("\(state.companion) · \(state.language)").appFont(12).foregroundStyle(Theme.Palette.muted2)
                }
                Spacer()
            }
            .padding(14)
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.line))

            // Sign-in happens in the onboarding funnel — You only manages an
            // existing account (no sign-in CTA for the signed-out state).
            if backend.isConnected {
                NavRow(title: "Account", subtitle: "Manage your synced account",
                       systemImage: "person.crop.circle", imageURL: Dummy.Img.privacy, emphasis: true) { CloudSyncView() }
            }
            NavRow(title: "Companion style", subtitle: "\(state.companion) · how CereBro talks with you",
                   systemImage: "bubble.left.and.text.bubble.right", imageURL: Dummy.Img.chat) { CompanionStyleView() }
            NavRow(title: "Daily reminder",
                   subtitle: state.reminderEnabled ? "On · gentle daily check-in" : "Off · tap to set a gentle nudge",
                   systemImage: "bell", imageURL: Dummy.Img.bell) { RemindersView() }
            // Ref "Take a quick tour": non-destructive replay of the tour.
            // No chevron — this flips to Home and overlays, it doesn't push.
            ListRow(title: "Take a quick tour", subtitle: "See what CereBro can do in 4 stops",
                    systemImage: "sparkles", chevron: false) {
                GuidedTourOverlay.reset()
                state.tourRequested = true
            }
            // The one screen the user fills in rather than reads.
            NavRow(title: "Goals & habits", subtitle: "The part you decide", systemImage: "target", imageURL: Dummy.Img.plan) { GoalsHabitsView() }
            NavRow(title: "Weekly insights", subtitle: "Your progress and patterns", systemImage: "chart.line.uptrend.xyaxis", imageURL: Dummy.Img.calm) { InsightsView() }
            NavRow(title: "Privacy & memory", subtitle: "Control what CereBro remembers", systemImage: "lock", imageURL: Dummy.Img.privacy) { PrivacyView() }
            NavRow(title: "Pattern dashboard", subtitle: "What the AI has learned · delete anytime", systemImage: "brain.head.profile", imageURL: Dummy.Img.privacy) { PatternDashboardView() }
            NavRow(title: "Premium plan", subtitle: "Manage your subscription", systemImage: "crown", imageURL: Dummy.Img.premium) { PremiumView() }
            // Written when things are steady, so it sits with the calm settings
            // rather than filed under crisis, where nobody browses.
            NavRow(title: "My safety plan", subtitle: "Yours, in your words · works offline", systemImage: "shield.lefthalf.filled", imageURL: Dummy.Img.privacy) { SafetyPlanView() }
            NavRow(title: "Urgent support", subtitle: "Emergency resources", systemImage: "phone.fill", imageURL: Dummy.Img.support) { CrisisView() }
            NavRow(title: "Crisis region", subtitle: CrisisDirectory.displayName(state.crisisRegion), systemImage: "globe", imageURL: Dummy.Img.privacy) { CrisisRegionView() }
            NavRow(title: "Human support", subtitle: "Coach or therapist handoff", systemImage: "person.2", imageURL: Dummy.Img.meditate) { HumanSupportView() }

            SectionTitle(title: "Legal & account", trailing: nil)
            NavRow(title: "Privacy policy", subtitle: "How we handle your data", systemImage: "lock.shield", imageURL: Dummy.Img.privacy) { PrivacyPolicyView() }
            NavRow(title: "Export my data", subtitle: "Download a full copy", systemImage: "square.and.arrow.up", imageURL: Dummy.Img.plan) { DataExportView() }
            NavRow(title: "Delete account", subtitle: "Permanently erase everything", systemImage: "trash", imageURL: Dummy.Img.support) { AccountDeletionView() }

            Button { showReset = true } label: {
                Text("Reset & view onboarding")
                    .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.pressable)
            .confirmationDialog("Reset the app?", isPresented: $showReset, titleVisibility: .visible) {
                Button("Reset & view onboarding", role: .destructive) { state.resetAll() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This clears your local data on this device and returns to onboarding.")
            }
        }
    }
}
