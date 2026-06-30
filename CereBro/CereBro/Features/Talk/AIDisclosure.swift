import SwiftUI

// MARK: - AI status disclosure (regulatory UX for the Talk tab)
//
// The 2025–26 wave of AI-companion rules (NY Companion Models law, CA SB 243,
// UT HB 452, plus FTC scrutiny) converges on three obligations for a conversational
// wellness AI: (1) disclose clearly that the user is talking to AI and not a human
// or licensed therapist, at the start of and throughout the interaction; (2) re-assert
// that disclosure periodically during long continuous sessions; (3) detect crisis /
// self-harm signals and route to crisis services. These components implement (1) and
// (2); crisis routing (3) is surfaced via `CrisisBanner` + the backend's crisis
// suggestion, which now appears on the voice path as well as text.

/// Always-visible, low-key disclosure line. Persistent so the AI status is
/// disclosed at the start of and throughout every conversation. Tapping opens the
/// full disclosure + crisis resources.
struct AIDisclosureBanner: View {
    var onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: "info.circle").appFont(12, weight: .semibold)
                    .foregroundStyle(Theme.Palette.muted)
                Text("AI companion — not a therapist or crisis service.")
                    .appFont(11.5, weight: .medium).foregroundStyle(Theme.Palette.muted)
                    .lineLimit(1).minimumScaleFactor(0.85)
                Spacer(minLength: 4)
                Text("Details").appFont(11.5, weight: .heavy).foregroundStyle(Theme.Palette.lav)
                Image(systemName: "chevron.right").appFont(9, weight: .bold).foregroundStyle(Theme.Palette.lav)
            }
            .padding(.horizontal, 12).frame(minHeight: 34)
            .background(Theme.Palette.card)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Theme.Palette.line))
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("About this AI companion")
        .accessibilityHint("Opens what the AI can and can't do, and crisis resources")
    }
}

/// Re-asserts the AI disclosure every few hours of continuous use (the NY
/// Companion Models law requires a reminder roughly every 3 hours). Drives the
/// shared `show` binding that also presents the sheet on a banner tap.
struct PeriodicAIDisclosure: ViewModifier {
    @Binding var show: Bool
    /// Compliance floor: re-disclose after this much continuous engagement.
    private let interval: TimeInterval = 3 * 60 * 60
    @State private var sessionStart = Date()
    private let tick = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    func body(content: Content) -> some View {
        content.onReceive(tick) { now in
            if now.timeIntervalSince(sessionStart) >= interval {
                sessionStart = now          // reset the window after re-disclosing
                show = true
            }
        }
    }
}

extension View {
    /// Presents the AI disclosure sheet on `show`, and re-triggers it every ~3h of
    /// continuous use. Pair with an `AIDisclosureBanner` whose tap sets `show`.
    func aiDisclosure(show: Binding<Bool>) -> some View {
        self.modifier(PeriodicAIDisclosure(show: show))
            .sheet(isPresented: show) { AIDisclosureSheet() }
    }
}

/// Full disclosure: what the companion is, what it isn't, and a direct route to
/// crisis resources. Presented from the banner tap or the periodic reminder.
struct AIDisclosureSheet: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Before we talk").eyebrow()
                        Text("About your AI companion")
                            .displayFont(26).foregroundStyle(Theme.Palette.text)

                        DisclosurePoint(icon: "cpu", title: "It's AI, not a person",
                            detail: "You're talking with an automated companion — not a human, counselor, or licensed therapist.")
                        DisclosurePoint(icon: "cross.case", title: "Not medical care",
                            detail: "It can't diagnose, treat, or prescribe, and it can sometimes be wrong. It supports your wellbeing; it doesn't replace professional care.")
                        DisclosurePoint(icon: "exclamationmark.triangle", title: "Not for emergencies",
                            detail: "In a crisis, or if you might harm yourself, please reach emergency services or a crisis line right away.")
                        DisclosurePoint(icon: "eye", title: "Reviewed for safety",
                            detail: "Messages may be checked for crisis signals so support can be offered. You control what's remembered in Settings.")

                        Text("If you need help now").eyebrow().padding(.top, 4)
                        NavigationLink { CrisisView() } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "lifepreserver.fill").appFont(18, weight: .semibold)
                                    .foregroundStyle(Theme.Palette.danger)
                                    .frame(width: 42, height: 42)
                                    .background(Theme.Palette.danger.opacity(0.16), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Get crisis support").appFont(14.5, weight: .semibold).foregroundStyle(Theme.Palette.text)
                                    Text("Emergency & crisis line numbers").appFont(11.5).foregroundStyle(Theme.Palette.muted)
                                }
                                Spacer(minLength: 4)
                                Image(systemName: "chevron.right").appFont(13, weight: .bold).foregroundStyle(Theme.Palette.danger)
                            }
                            .padding(13)
                            .background(Theme.Palette.danger.opacity(0.10))
                            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous).stroke(Theme.Palette.danger.opacity(0.28)))
                        }
                        .buttonStyle(.pressable)

                        PrimaryButton(title: "Got it", systemImage: "checkmark.circle.fill") { dismiss() }
                            .padding(.top, 6)
                    }
                    .padding(18).padding(.top, 8)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") { dismiss() }.foregroundStyle(Theme.Palette.soft)
                }
            }
        }
        .presentationDetents([.large])
        .preferredColorScheme(.dark)
    }
}

private struct DisclosurePoint: View {
    let icon: String
    let title: String
    let detail: String
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).appFont(15, weight: .semibold).foregroundStyle(Theme.Palette.lav)
                .frame(width: 38, height: 38)
                .background(Theme.Palette.lav.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).appFont(14.5, weight: .semibold).foregroundStyle(Theme.Palette.text)
                Text(detail).appFont(12.5).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Crisis banner
/// Prominent, tappable crisis affordance shown whenever the backend's safety
/// layer flags risk in the conversation — now surfaced on BOTH the text and
/// voice paths (voice transcripts run through the same `/chat` crisis detection).
struct CrisisBanner: View {
    var body: some View {
        NavigationLink { CrisisView() } label: {
            HStack(spacing: 12) {
                Image(systemName: "lifepreserver.fill").appFont(18, weight: .bold)
                    .foregroundStyle(Theme.Palette.danger)
                    .frame(width: 42, height: 42)
                    .background(Theme.Palette.danger.opacity(0.18), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("Support is available right now")
                        .appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("If you're in crisis or thinking about self-harm, reach a crisis line or emergency services.")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right").appFont(13, weight: .bold).foregroundStyle(Theme.Palette.danger)
            }
            .padding(13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Palette.danger.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.panel, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel, style: .continuous).stroke(Theme.Palette.danger.opacity(0.3)))
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Crisis support available. Opens crisis resources.")
    }
}
