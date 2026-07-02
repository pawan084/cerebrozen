import SwiftUI

struct CloudSyncView: View {
    @EnvironmentObject var backend: BackendService

    var body: some View {
        ScreenScaffold(eyebrow: backend.isConnected ? "Your account" : "Private by design",
                       title: backend.isConnected ? "Account" : "Sign in",
                       trailingSystemImage: "person.crop.circle", accent: Theme.Palette.lav) {
            ToolBanner(imageURL: Dummy.Img.privacy, symbol: "person.crop.circle",
                       caption: "Sign in to keep your plan, journal and check-ins in sync across devices.")

            statusCard

            if backend.isConnected {
                connectedBody
            } else {
                AuthForm()   // shared form (also embedded in onboarding's account step)
            }
        }
    }

    // MARK: Status

    private var statusCard: some View {
        Card {
            HStack(spacing: 12) {
                Circle().fill(statusColor).frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 2) {
                    Text(statusTitle).appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                    #if DEBUG
                    Text(backend.baseURL).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                    #endif
                }
                Spacer()
            }
        }
    }

    private var statusColor: Color {
        switch backend.status {
        case .connected: return Theme.Palette.success
        case .connecting: return Theme.Palette.cream
        case .error: return Theme.Palette.danger
        case .signedOut: return Theme.Palette.muted
        }
    }
    private var statusTitle: String {
        switch backend.status {
        case let .connected(email): return "Connected as \(email)"
        case .connecting: return "Connecting…"
        case let .error(msg): return msg
        case .signedOut: return "Not connected"
        }
    }

    // MARK: Connected body — shows server-driven plan + insights

    private var connectedBody: some View {
        VStack(spacing: 14) {
            if let plan = backend.plan {
                SectionTitle(title: "Your agentic plan", trailing: nil)
                InsightCard(label: plan.source == "ai" ? "AI-generated" : "Adaptive plan",
                            title: plan.title, detail: plan.rationale)
                ForEach(plan.steps) { step in
                    Button {
                        Task { await backend.toggleStep(step.id, done: !step.done) }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: step.done ? "checkmark.circle.fill" : "circle")
                                .appFont(20, weight: .semibold)
                                .foregroundStyle(step.done ? Theme.Palette.lav : Theme.Palette.muted)
                            RowLabel(title: step.title, subtitle: step.detail,
                                     systemImage: step.symbol, emphasis: step.done)
                        }
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel(step.done ? "Mark \(step.title) not done" : "Mark \(step.title) done")
                }
            }
            if let insight = backend.insight {
                SectionTitle(title: "This week", trailing: nil)
                InsightCard(label: insight.headline, title: insight.summary)
                ForEach(insight.metrics, id: \.label) { m in
                    MetricBar(label: m.label, value: m.value, progress: m.progress)
                }
            }
            SecondaryButton(title: "Sign out", systemImage: "rectangle.portrait.and.arrow.right") {
                backend.signOut()
            }
        }
    }
}
