import SwiftUI

// MARK: - Locale-aware crisis resources
//
// Crisis hotlines differ by country, so we surface the right ones for the user's
// region (best-effort via the device locale). Numbers below are the widely
// published national lines; the default falls back to the EU/GSM emergency number
// plus an international helpline finder. Stored display strings are dialed after
// stripping non-digits, so "13 11 14" calls 131114.

struct CrisisResource: Identifiable {
    let id = UUID()
    let title: String
    var phone: String? = nil       // call line (display string)
    var textNumber: String? = nil  // SMS line (display string)
    var textKeyword: String? = nil // keyword to text (shown in the subtitle)
    var url: String? = nil         // web resource (e.g. helpline finder)
    var subtitle: String? = nil
}

enum CrisisDirectory {
    /// Region code from the device locale (e.g. "US", "GB"); falls back to "".
    static var currentRegion: String { Locale.current.region?.identifier.uppercased() ?? "" }

    /// Regions with a curated, region-specific set (besides Automatic / Other).
    static let supportedRegions = ["US", "CA", "GB", "IE", "AU", "NZ", "IN"]

    /// The region actually used to look up resources, honoring a user override
    /// ("" → fall back to the device region).
    static func effectiveRegion(_ override: String) -> String {
        override.isEmpty ? currentRegion : override
    }

    /// Human label for a region code; "" = automatic, "ZZ" = international.
    static func displayName(_ code: String) -> String {
        switch code {
        case "":   return "Automatic (device region)"
        case "ZZ": return "Other / International"
        default:   return Locale.current.localizedString(forRegionCode: code) ?? code
        }
    }

    /// A short "911 · 988"-style preview of a region's lines, for the picker.
    static func previewLine(_ code: String) -> String {
        resources(for: code.isEmpty ? currentRegion : code)
            .compactMap { $0.phone ?? $0.textNumber }
            .prefix(2).joined(separator: " · ")
    }

    /// Crisis resources for a region. Emergency line first, then crisis lines.
    static func resources(for region: String = currentRegion) -> [CrisisResource] {
        switch region {
        case "US":
            return [
                CrisisResource(title: "Emergency services", phone: "911"),
                CrisisResource(title: "988 Suicide & Crisis Lifeline", phone: "988",
                               textNumber: "988", subtitle: "Call or text · free · 24/7"),
                CrisisResource(title: "Crisis Text Line", textNumber: "741741",
                               textKeyword: "HOME", subtitle: "Text HOME · 24/7"),
            ]
        case "CA":
            return [
                CrisisResource(title: "Emergency services", phone: "911"),
                CrisisResource(title: "9-8-8 Suicide Crisis Helpline", phone: "988",
                               textNumber: "988", subtitle: "Call or text · free · 24/7"),
            ]
        case "GB":
            return [
                CrisisResource(title: "Emergency services", phone: "999"),
                CrisisResource(title: "Samaritans", phone: "116 123", subtitle: "Free · 24/7"),
                CrisisResource(title: "Shout", textNumber: "85258",
                               textKeyword: "SHOUT", subtitle: "Text SHOUT · 24/7"),
            ]
        case "IE":
            return [
                CrisisResource(title: "Emergency services", phone: "112"),
                CrisisResource(title: "Samaritans", phone: "116 123", subtitle: "Free · 24/7"),
                CrisisResource(title: "Text About It", textNumber: "50808",
                               textKeyword: "HELLO", subtitle: "Text HELLO · 24/7"),
            ]
        case "AU":
            return [
                CrisisResource(title: "Emergency services", phone: "000"),
                CrisisResource(title: "Lifeline", phone: "13 11 14",
                               textNumber: "0477 13 11 14", subtitle: "Call or text · 24/7"),
            ]
        case "NZ":
            return [
                CrisisResource(title: "Emergency services", phone: "111"),
                CrisisResource(title: "Need to talk?", phone: "1737",
                               textNumber: "1737", subtitle: "Call or text · free · 24/7"),
            ]
        case "IN":
            return [
                CrisisResource(title: "Emergency services", phone: "112"),
                CrisisResource(title: "KIRAN mental health helpline", phone: "1800-599-0019",
                               subtitle: "Free · 24/7"),
            ]
        default:
            return [
                CrisisResource(title: "Emergency services", phone: "112",
                               subtitle: "Works across the EU and most mobile networks"),
                CrisisResource(title: "Find a helpline", url: "https://findahelpline.com",
                               subtitle: "Free, confidential lines worldwide"),
            ]
        }
    }
}

// MARK: - Resource card (call / text / web actions)
struct CrisisResourceCard: View {
    let resource: CrisisResource

    private func telURL(_ s: String) -> URL? {
        let d = s.filter { $0.isNumber || $0 == "+" }
        return d.isEmpty ? nil : URL(string: "tel://\(d)")
    }
    private func smsURL(_ s: String) -> URL? {
        let d = s.filter { $0.isNumber || $0 == "+" }
        return d.isEmpty ? nil : URL(string: "sms:\(d)")
    }

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(resource.title).appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.danger)
                if let phone = resource.phone {
                    Text(phone).displayFont(24).foregroundStyle(Theme.Palette.text)
                } else if let t = resource.textNumber {
                    Text(t).displayFont(22).foregroundStyle(Theme.Palette.text)
                }
                if let sub = resource.subtitle {
                    Text(sub).appFont(11).foregroundStyle(Theme.Palette.muted)
                }
            }
            Spacer(minLength: 4)
            HStack(spacing: 8) {
                if let phone = resource.phone, let u = telURL(phone) {
                    actionLink(u, system: "phone.fill", label: "Call \(resource.title), \(phone)")
                }
                if let t = resource.textNumber, let u = smsURL(t) {
                    actionLink(u, system: "message.fill",
                               label: "Text \(resource.textKeyword.map { "\($0) to " } ?? "")\(t)")
                }
                if let url = resource.url, let u = URL(string: url) {
                    actionLink(u, system: "safari.fill", label: "Open \(resource.title)")
                }
            }
        }
        .padding(16)
        .background(Theme.Palette.danger.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.danger.opacity(0.28)))
    }

    private func actionLink(_ url: URL, system: String, label: String) -> some View {
        Link(destination: url) {
            Image(systemName: system).appFont(17, weight: .bold).foregroundStyle(Theme.Palette.danger)
                .frame(width: 48, height: 48)
                .background(Theme.Palette.danger.opacity(0.16), in: Circle())
        }
        .accessibilityLabel(label)
    }
}

// MARK: - Region picker
/// Lets the user override which country's crisis lines are shown (e.g. a
/// traveler, or a device set to a different region than where they want help).
struct CrisisRegionView: View {
    @EnvironmentObject var state: AppState
    /// Automatic, then the curated regions, then International.
    private var options: [String] { [""] + CrisisDirectory.supportedRegions + ["ZZ"] }

    var body: some View {
        ScreenScaffold(eyebrow: "Crisis resources", title: "Crisis Region",
                       trailingSystemImage: "globe", accent: Theme.Accent.warm) {
            Text("Choose which country's crisis lines to show. Automatic follows your device region.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            SettingsGroup {
                ForEach(Array(options.enumerated()), id: \.element) { index, code in
                    Button {
                        state.crisisRegion = code; Haptics.selection()
                    } label: {
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(CrisisDirectory.displayName(code))
                                    .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                                let preview = CrisisDirectory.previewLine(code)
                                if !preview.isEmpty {
                                    Text(preview).appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                                }
                            }
                            Spacer()
                            if state.crisisRegion == code {
                                Image(systemName: "checkmark").appFont(14, weight: .bold)
                                    .foregroundStyle(Theme.Palette.lav)
                            }
                        }
                        .padding(12).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(state.crisisRegion == code ? .isSelected : [])
                    if index < options.count - 1 { Divider().overlay(Theme.Palette.line) }
                }
            }

            SectionTitle(title: "Preview", trailing: nil)
            ForEach(CrisisDirectory.resources(for: CrisisDirectory.effectiveRegion(state.crisisRegion))) {
                CrisisResourceCard(resource: $0)
            }
        }
    }
}
