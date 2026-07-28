import SwiftUI

/// The user's appearance choice (You → Appearance), persisted as `theme_mode`.
/// Raw values are the cross-client contract string ("system"/"night"/"dawn") —
/// the same vocabulary Android's `ThemeMode.prefValue()` and the web app's
/// `data-theme` attribute use.
enum ThemeMode: String, CaseIterable, Identifiable {
    case system, night, dawn

    var id: String { rawValue }

    /// Row label in the Appearance picker.
    var label: String {
        switch self {
        case .system: return "Match device"
        case .night:  return "Night"
        case .dawn:   return "Dawn"
        }
    }

    var detail: String {
        switch self {
        case .system: return "Follows your iOS appearance setting"
        case .night:  return "The deep indigo night sky, always"
        case .dawn:   return "A warm, light room — easier in daylight"
        }
    }
}

/// `theme_mode` preference string → ``ThemeMode`` (unknown/absent → system).
/// Pure and testable — the iOS twin of Android's `themeModeFromPref`.
func themeMode(fromPref raw: String?) -> ThemeMode {
    ThemeMode(rawValue: raw ?? "") ?? .system
}

/// Resolve the three inputs into "is the app painting Night right now?".
/// Pure so ``ContrastTest`` can gate the truth table without a running app.
func resolveIsNight(mode: ThemeMode, systemDark: Bool, forceNight: Bool) -> Bool {
    if forceNight { return true }
    switch mode {
    case .system: return systemDark
    case .night:  return true
    case .dawn:   return false
    }
}

/// The global snapshot every token getter in `Theme.swift` reads.
///
/// SwiftUI has no equivalent of Compose's snapshot state (where a token read
/// during composition subscribes the reader automatically), and it has no
/// equivalent of CSS variable scoping either. So the tokens stay *global*
/// statics — screens keep reading `Theme.Palette.…` unchanged — and the root
/// view re-keys its identity whenever this value flips, which makes the tree
/// re-read every token exactly once per theme change. Theme changes are rare
/// (a picker tap, or the device crossing sunset), so paying a full rebuild
/// there is much cheaper than threading an Environment palette through every
/// one of the app's colour reads.
enum ThemeSnapshot {
    /// Starts Night: the launch storyboard background is `LaunchBackground`
    /// (#0E0C22), and the app always opens into the splash or the signed-out
    /// funnel, both of which are Night-pinned. Dawn users therefore never see
    /// a bright first frame before ``AppTheme`` resolves.
    fileprivate(set) static var isNight = true
}

/// Owns the appearance preference and keeps ``ThemeSnapshot`` in sync.
///
/// Not an `@EnvironmentObject`-only type: `Theme`'s token getters are static
/// and cannot hold an environment reference, so the resolved boolean has to
/// live somewhere global. `AppTheme.shared` is that somewhere; the observable
/// wrapper exists so the root view can watch ``generation`` and rebuild.
final class AppTheme: ObservableObject {
    static let shared = AppTheme()
    static let prefKey = "theme_mode"

    /// The user's choice. Persisted on every change.
    @Published private(set) var mode: ThemeMode

    /// What the *device* is currently doing. Only consulted when `mode` is
    /// `.system`; fed by the root view from `@Environment(\.colorScheme)`,
    /// which reports the real system value because `.system` deliberately
    /// applies no `preferredColorScheme` override (see ``preferredScheme``).
    @Published private(set) var systemDark = true

    /// Contexts that are Night regardless of preference: the splash and the
    /// signed-out onboarding/auth funnel, whose art is bespoke night work.
    /// Both are *exclusive* full-window experiences, which is what makes a
    /// single global flag safe here — see the Sleep-tab note in IOS_PARITY.md.
    @Published private(set) var forceNight = true

    /// Bumped only when the resolved theme actually flips. The root view keys
    /// `.id()` on this, so nothing rebuilds when an input changes without
    /// changing the outcome (e.g. the device going dark while pinned Night).
    @Published private(set) var generation = 0

    var isNight: Bool {
        resolveIsNight(mode: mode, systemDark: systemDark, forceNight: forceNight)
    }

    /// The scheme to hand SwiftUI. `nil` under `.system` on purpose: applying
    /// no override is what lets the root view *read* the device's real choice
    /// back out of the environment instead of reading its own override.
    var preferredScheme: ColorScheme? {
        if forceNight { return .dark }
        switch mode {
        case .system: return nil
        case .night:  return .dark
        case .dawn:   return .light
        }
    }

    private init() {
        // UI tests pass `-resetState YES`. Wipe the preference here rather than
        // in AppState so this type owns its key outright and the wipe cannot
        // race AppState's initialiser — then PIN Night for the run.
        //
        // The pin matters: under `-resetState` the wiped preference resolves to
        // "match device", and a simulator booted in Light appearance would flip
        // the app to Dawn the moment onboarding finishes — re-keying the root
        // view mid-test and re-rendering every marketing screenshot in the wrong
        // theme. Same posture as the splash and the audio engine, which the
        // suite also gates off (see CLAUDE.md). Dawn's correctness is covered by
        // ContrastTest instead, which needs no rendering.
        if UserDefaults.standard.bool(forKey: "resetState") {
            UserDefaults.standard.removeObject(forKey: Self.prefKey)
            mode = .night
        } else {
            mode = themeMode(fromPref: UserDefaults.standard.string(forKey: Self.prefKey))
        }
        ThemeSnapshot.isNight = isNight
    }

    /// User picked an appearance in You → Appearance.
    func select(_ new: ThemeMode) {
        guard new != mode else { return }
        mode = new
        UserDefaults.standard.set(new.rawValue, forKey: Self.prefKey)
        sync()
    }

    func setSystemDark(_ dark: Bool) {
        guard dark != systemDark else { return }
        systemDark = dark
        sync()
    }

    func setForceNight(_ force: Bool) {
        guard force != forceNight else { return }
        forceNight = force
        sync()
    }

    private func sync() {
        let resolved = isNight
        guard resolved != ThemeSnapshot.isNight else { return }
        ThemeSnapshot.isNight = resolved
        generation += 1
    }
}
