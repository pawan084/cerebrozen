import SwiftUI

// MARK: - Human support
struct HumanSupportView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Coach/therapist handoff", title: "Human Support", trailingSystemImage: "person.2") {
            HeroCard(tag: "Optional", title: "Book human support",
                     subtitle: "Connect with a vetted coach or licensed therapist partner.",
                     cta: "Book", imageURL: Dummy.Img.support)
            DangerPanel {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Emergency boundary").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("CereBro is wellness support, not emergency care. In a crisis, use the resources below.").appFont(12).foregroundStyle(Theme.Palette.muted)
                }
            }
            NavRow(title: "Coach booking", subtitle: "Human support booking", systemImage: "calendar", imageURL: Dummy.Img.meditate, emphasis: true) { CoachBookingView() }
            NavRow(title: "Trusted contact", subtitle: "Private consent-first setup", systemImage: "person.crop.circle.badge.checkmark", imageURL: Dummy.Img.privacy) { TrustedContactView() }
            NavRow(title: "Crisis flow", subtitle: "Free safety escalation", systemImage: "phone.fill", imageURL: Dummy.Img.mood) { CrisisView() }
        }
    }
}

// MARK: - Coach booking
struct CoachBookingView: View {
    @State private var slot: Set<String> = ["Tomorrow 6 PM"]
    var body: some View {
        ScreenScaffold(eyebrow: "Human support booking", title: "Coach Booking", trailingSystemImage: "calendar") {
            ListRow(title: "Dr. Aarav Mehta", subtitle: "Therapist · CBT, anxiety", systemImage: "person.fill", imageURL: Dummy.Img.support, emphasis: true)
            ListRow(title: "Sara Khan", subtitle: "Wellness coach · Sleep, stress", systemImage: "person.fill", imageURL: Dummy.Img.meditate)
            SectionTitle(title: "Pick a time", trailing: nil)
            ChipRow(options: ["Today 8 PM", "Tomorrow 6 PM", "Sat 11 AM", "Sun 4 PM"], selection: $slot)
            PrimaryButton(title: "Request session", systemImage: "calendar.badge.plus")
        }
    }
}

// MARK: - Crisis flow
struct CrisisView: View {
    @EnvironmentObject var state: AppState
    /// Region-appropriate hotlines — the user's override, else the device region.
    private var resources: [CrisisResource] {
        CrisisDirectory.resources(for: CrisisDirectory.effectiveRegion(state.crisisRegion))
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Free safety escalation", title: "You're not alone",
                       trailingSystemImage: "heart.fill", accent: Theme.Accent.warm) {
            DangerPanel {
                Text("If you are in immediate danger, call your local emergency number now.")
                    .appFont(13).foregroundStyle(Theme.Palette.danger)
            }
            ForEach(resources) { CrisisResourceCard(resource: $0) }
            NavRow(title: "Crisis region", subtitle: CrisisDirectory.displayName(state.crisisRegion), systemImage: "globe", imageURL: Dummy.Img.privacy) { CrisisRegionView() }
            NavRow(title: "Notify a trusted contact", subtitle: "Send a check-in message", systemImage: "person.crop.circle.badge.checkmark", imageURL: Dummy.Img.privacy) { TrustedContactView() }
            Text("CereBro can't provide emergency care, but it can help you reach people who can.")
                .appFont(12).foregroundStyle(Theme.Palette.muted)
        }
    }
}

struct CallCard: View {
    let title: String
    let number: String

    /// `tel://` URL built from the displayed number (digits and `+` only).
    private var telURL: URL? {
        let dialable = number.filter { $0.isNumber || $0 == "+" }
        return dialable.isEmpty ? nil : URL(string: "tel://\(dialable)")
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.danger)
                Text(number).displayFont(25).foregroundStyle(Theme.Palette.text)
            }
            Spacer()
            if let telURL {
                Link(destination: telURL) {
                    Image(systemName: "phone.fill").appFont(18, weight: .bold).foregroundStyle(Theme.Palette.danger)
                        .frame(width: 48, height: 48).background(Theme.Palette.danger.opacity(0.16), in: Circle())
                }
                .accessibilityLabel("Call \(title), \(number)")
            }
        }
        .padding(16)
        .background(Theme.Palette.danger.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.danger.opacity(0.28)))
    }
}

// MARK: - Trusted contact
struct TrustedContactView: View {
    @State private var name = ""
    @State private var consent = true
    var body: some View {
        ScreenScaffold(eyebrow: "Emergency contact setup", title: "Trusted Contact", trailingSystemImage: "person.crop.circle.badge.checkmark") {
            Photo(url: Dummy.Img.privacy, symbol: "person").frame(height: 120).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            Card(cornerRadius: 18) {
                TextField("Contact name & number", text: $name)
                    .foregroundStyle(Theme.Palette.text)
            }
            SettingsGroup {
                ToggleRow(title: "Consent to notify", subtitle: "Only with your explicit action", isOn: $consent)
            }
            PrimaryButton(title: "Save trusted contact")
        }
    }
}
