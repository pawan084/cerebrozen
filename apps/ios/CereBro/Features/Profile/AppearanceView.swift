import SwiftUI

// MARK: - Appearance (Night / Dawn)
/// The iOS half of the Dusk & Dawn theme switch (IOS_PARITY item 16 — Android
/// shipped its picker in the 2026-07-12 redesign, the web app in parity Wave E).
/// The choice persists as `theme_mode` using the same "system"/"night"/"dawn"
/// vocabulary the other two clients write, so a user's preference reads the same
/// everywhere even though nothing syncs it server-side yet.
struct AppearanceView: View {
    @EnvironmentObject var theme: AppTheme

    var body: some View {
        ScreenScaffold(eyebrow: "How CereBro looks", title: "Appearance") {
            Text("Night is the app's home key — the deep indigo sky the whole design was drawn against. Dawn is the same palette rebalanced for a lit room.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            ForEach(ThemeMode.allCases) { mode in
                ListRow(title: mode.label,
                        subtitle: mode.detail,
                        systemImage: symbol(for: mode),
                        emphasis: theme.mode == mode) {
                    theme.select(mode)
                    Haptics.selection()
                }
                .accessibilityAddTraits(theme.mode == mode ? .isSelected : [])
            }

            // Honest about the two places the preference does not apply. Both
            // are deliberate: their art is bespoke night work, not a palette.
            Text("The splash and the signed-out sign-up flow stay in Night whichever you pick — their artwork is painted for a dark sky.")
                .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 2)
        }
    }

    private func symbol(for mode: ThemeMode) -> String {
        switch mode {
        case .system: return "iphone"
        case .night:  return "moon.stars"
        case .dawn:   return "sun.horizon"
        }
    }
}
