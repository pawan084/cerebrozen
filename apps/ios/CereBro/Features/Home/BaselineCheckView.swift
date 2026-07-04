import SwiftUI

// MARK: - Starting point (contextual baseline)
/// The honest "before" measurement: two 1–5 scales (stress, sleep), asked
/// gently from Home once a few real check-ins exist — deliberately NOT part
/// of the 90-second onboarding. Insights' "Your starting point" card renders
/// from this. Local-only; never leaves the device.
struct BaselineCheckView: View {
    @EnvironmentObject var state: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var stress = 0                  // 0 = not chosen yet
    @State private var sleep = 0

    private static let stressWords = ["Very calm", "Mostly calm", "Managing", "Stretched", "Overwhelmed"]
    private static let sleepWords = ["Rough", "Poor", "Okay", "Good", "Rested"]

    private var complete: Bool { stress > 0 && sleep > 0 }

    var body: some View {
        ScreenScaffold(eyebrow: "Two quick scales", title: "Your starting point",
                       trailingSystemImage: "flag") {
            Text("Where things stand these days. Saving this lets future insights show real change instead of guesses.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            SectionTitle(title: "How stressed have you felt lately?")
            scaleRow(words: Self.stressWords, symbol: "flame", accessibilityName: "Stress", value: $stress)

            SectionTitle(title: "How has sleep felt lately?")
            scaleRow(words: Self.sleepWords, symbol: "moon.zzz", accessibilityName: "Sleep", value: $sleep)

            PrimaryButton(title: "Save my starting point", systemImage: "flag.fill") {
                guard complete else { return }
                state.setBaseline(stress: stress, sleep: sleep)
                Haptics.selection()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { dismiss() }
            }
            .disabled(!complete)
            .opacity(complete ? 1 : 0.45)

            Text("Stays on this device — used only to show your progress in Insights.")
                .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
        }
    }

    /// Five tappable levels, mirroring the sleep check-in's quality picker.
    private func scaleRow(words: [String], symbol: String,
                          accessibilityName: String, value: Binding<Int>) -> some View {
        HStack(spacing: 8) {
            ForEach(1...5, id: \.self) { level in
                let chosen = value.wrappedValue
                Button { value.wrappedValue = level; Haptics.selection() } label: {
                    VStack(spacing: 6) {
                        Image(systemName: level <= chosen ? "\(symbol).fill" : symbol)
                            .appFont(19, weight: .semibold)
                            .foregroundStyle(level <= chosen ? Theme.Palette.lav : Theme.Palette.muted2)
                        Text(words[level - 1])
                            .appFont(9.5, weight: .semibold)
                            .foregroundStyle(level == chosen ? Theme.Palette.soft : Theme.Palette.muted2)
                    }
                    .frame(maxWidth: .infinity, minHeight: 62)
                    .background(level == chosen ? Theme.Palette.cardEmphasis : Theme.Palette.card)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(level == chosen ? Theme.Palette.lav.opacity(0.7) : Theme.Palette.line))
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("\(accessibilityName) \(level) of 5, \(words[level - 1])")
                .accessibilityAddTraits(level == chosen ? .isSelected : [])
            }
        }
    }
}
