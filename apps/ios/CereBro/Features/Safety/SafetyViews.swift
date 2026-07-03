import SwiftUI

// MARK: - Human support
struct HumanSupportView: View {
    @State private var showBooking = false
    var body: some View {
        ScreenScaffold(eyebrow: "Coach/therapist handoff", title: "Human Support", trailingSystemImage: "person.2") {
            HeroCard(tag: "Optional", title: "Human support",
                     subtitle: "Connect with a vetted coach or licensed therapist partner.",
                     cta: "Book", imageURL: Dummy.Img.support) { showBooking = true }
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
        .navigationDestination(isPresented: $showBooking) { CoachBookingView() }
    }
}

// MARK: - Coach booking
struct CoachBookingView: View {
    @State private var slot: Set<String> = ["Tomorrow 6 PM"]
    @State private var requested = false
    var body: some View {
        ScreenScaffold(eyebrow: "Human support booking", title: "Coach Booking", trailingSystemImage: "calendar") {
            // Generic partner categories — never invented clinician names.
            RowLabel(title: "Licensed therapists", subtitle: "CBT, anxiety, low mood", systemImage: "person.badge.shield.checkmark", emphasis: true, chevron: false)
            RowLabel(title: "Wellness coaches", subtitle: "Sleep, stress, habits", systemImage: "person.2", chevron: false)
            SectionTitle(title: "Pick a time", trailing: nil)
            ChipRow(options: ["Today 8 PM", "Tomorrow 6 PM", "Sat 11 AM", "Sun 4 PM"], selection: $slot)
            PrimaryButton(title: requested ? "Noted" : "Notify me when booking opens",
                          systemImage: requested ? "checkmark.circle.fill" : "bell.badge") {
                requested = true; Haptics.success()
            }
            if requested {
                InsightCard(label: "Noted",
                            title: "Human-session booking is rolling out.",
                            detail: "You'll see it here first — nothing is scheduled yet.")
            }
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
    @EnvironmentObject var backend: BackendService
    @State private var name = ""
    @State private var email = ""
    @State private var relationship = ""
    @State private var consent = false
    @State private var saved = false
    @State private var message: String?

    var body: some View {
        ScreenScaffold(eyebrow: "Emergency contact setup", title: "Trusted Contact", trailingSystemImage: "person.crop.circle.badge.checkmark") {
            Text("If CereBro detects a crisis and you've given consent, we'll send this person a gentle check-in message. Nothing is shared otherwise.")
                .appFont(12.5).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            Card(cornerRadius: 18) {
                VStack(spacing: 10) {
                    TextField("Contact name", text: $name).foregroundStyle(Theme.Palette.text)
                        .accessibilityLabel("Contact name")
                    Divider().overlay(Theme.Palette.line)
                    TextField("Their email", text: $email)
                        .foregroundStyle(Theme.Palette.text)
                        .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .accessibilityLabel("Contact email")
                    Divider().overlay(Theme.Palette.line)
                    TextField("Relationship (optional)", text: $relationship).foregroundStyle(Theme.Palette.text)
                        .accessibilityLabel("Relationship")
                }
            }
            SettingsGroup {
                ToggleRow(title: "Consent to notify in a crisis", subtitle: "Off by default — required for any alert", isOn: $consent)
            }
            PrimaryButton(title: "Save trusted contact", systemImage: "checkmark") { save() }
            if let message {
                Text(message).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !backend.isConnected {
                Text("Sign in to sync your trusted contact across devices.")
                    .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
            }
        }
        .celebration(trigger: $saved)
        .task { await load() }
    }

    private func load() async {
        guard backend.isConnected else { return }
        let fetched = try? await APIClient.shared.trustedContact()   // RemoteTrustedContact??
        if let c = fetched ?? nil {
            name = c.name; email = c.value; relationship = c.relationship; consent = c.notify_consent
        }
    }

    private func save() {
        Task {
            guard backend.isConnected else {
                message = "Sign in first to save a trusted contact."
                return
            }
            do {
                _ = try await APIClient.shared.saveTrustedContact(
                    name: name, method: "email", value: email,
                    relationship: relationship, notifyConsent: consent)
                saved.toggle()
                message = consent ? "Saved. We'll only reach out with your consent."
                                  : "Saved. Turn on consent to enable crisis alerts."
            } catch {
                message = "Couldn't save. Please try again."
            }
        }
    }
}
