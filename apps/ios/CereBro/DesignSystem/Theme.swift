import SwiftUI

/// CereBro design system — a single source of truth for color, type, shape and
/// motion, derived directly from the brand mark (the orb-lotus logo) and the
/// night-lake splash.
///
/// The hierarchy is intentional and one-directional:
///
///   `Brand`   → raw, named swatches lifted straight from the artwork. Never used
///               directly by a screen; they exist so every other token can point
///               at *one* place when the palette is tuned.
///   `Palette` → semantic roles (background, text, line, primary accent…). Screens
///               and components read these, never `Brand`.
///   `Accent`  → gentle per-section hue shifts (home/sleep/breathe/crisis) so the
///               app stays in one family while each context orients you.
///   `Radius` / `Stroke` / `Gradient` → the *shape*, *hairline* and *fill* details
///               that make every component feel like one system.
///
/// Change a value in `Brand`, and the whole app moves with it.
enum Theme {

    // MARK: - Brand swatches (straight from the logo + splash)
    /// The literal colors of the artwork. Tuning the brand happens here and only
    /// here; nothing in a feature screen should reference `Brand` directly.
    enum Brand {
        // Night sky — the splash gradient, deepest → lifted. Warmed 2026-07 to the
        // Newsreader refresh: indigo base (#0e0c22) instead of the old cool navy.
        static let nightDeep    = Color(hex: 0x0A0818)   // darkest corner of the sky
        static let night        = Color(hex: 0x0E0C22)   // primary background (warm indigo)
        static let nightMid     = Color(hex: 0x181240)   // mid sky
        static let nightPurple  = Color(hex: 0x231A5C)   // purple-ink, lower sky
        static let indigoLift   = Color(hex: 0x1A1440)   // periwinkle-tinted radial highlight
        static let ink          = Color(hex: 0x1C1740)   // deep ink (text on light fills)

        // The orb-lotus mark.
        static let periwinkle   = Color(hex: 0x8A7BF0)   // the orb ring — the primary brand hue (warm lavender)
        static let iris         = Color(hex: 0xA9A0F5)   // lighter lavender (the "Bro" wordmark)
        static let lavender     = Color(hex: 0xD1C4FF)   // horizon glow / soft highlight
        static let cyan         = Color(hex: 0x8FE6EE)   // aurora cyan / lotus rim (breathe accent)
        static let teal         = Color(hex: 0x6FE0E6)   // lotus teal
        static let violet       = Color(hex: 0xA68BFF)   // aurora purple

        // Neutrals & utility.
        static let whiteText    = Color(hex: 0xF5F4FF)   // primary text on dark
        static let softText     = Color(hex: 0xDFE0FF)   // secondary text / accents
        static let mutedText    = Color(hex: 0xB0A9E0)   // tertiary text
        // Brightened 0x8F88C0 → 0xA29CCC (2026-07-25 contrast audit): the old
        // value hit 3.77:1 on a raised card over the top gradient — under the
        // 4.5:1 AA floor. Computed worst-case now 4.77:1 (see ContrastTest).
        static let captionText  = Color(hex: 0xA29CCC)   // captions / labels
        static let cream        = Color(hex: 0xECEEFB)   // light "primary" button fill
        static let amber        = Color(hex: 0xF0A48C)   // warm / crisis accent (coral)
        static let rose         = Color(hex: 0xE08A9A)   // stress / caution metric
        static let mint         = Color(hex: 0x7EE0A8)   // success / connected
        static let danger       = Color(hex: 0xFF8A80)   // safety / destructive
    }

    // MARK: - Dawn swatches (the light theme's raw values)
    /// The Dawn half of the palette. Values are hand-synced with the web app's
    /// `[data-theme="dawn"]` block in `apps/app/app/globals.css` (WEB_PARITY
    /// Wave E) so a user on the phone and on app.cerebrozen.in sees the same
    /// light theme; the accents web has no token for (cyan/mint/rose/danger)
    /// are the same hues darkened until each clears AA on white.
    ///
    /// Every pairing is machine-verified in both themes by `ContrastTest` —
    /// a palette tweak that breaks legibility fails the test rather than
    /// shipping. Ratios quoted in the comments are computed, not guessed.
    enum Dawn {
        static let ground     = Color(hex: 0xFAFAFC)   // page ground (warm white)
        static let groundTop  = Color(hex: 0xECEDF5)   // darkest page paint — worst case for dark text
        static let card       = Color(hex: 0xFFFFFF)   // resting card = white
        static let cardRaised = Color(hex: 0xEFF0F7)   // lifted fill (emphasis rows, chips)
        static let line       = Color(hex: 0xDFE1EC)   // hairline

        // Text ladder — ratios on ground / groundTop / card / raised:
        static let text    = Color(hex: 0x1A1830)      // 16.55 · 14.79 · 17.25 · 15.18
        static let soft    = Color(hex: 0x45426B)      //  8.97 ·  8.02 ·  9.36 ·  8.23
        static let muted   = Color(hex: 0x5A5680)      //  6.55 ·  5.85 ·  6.82 ·  6.00
        static let muted2  = Color(hex: 0x655F8A)      //  5.66 ·  5.06 ·  5.90 ·  5.19

        // Accents as TEXT on warm white — same hues, darkened until each clears AA.
        static let lavText = Color(hex: 0x5B4BC4)      //  6.20 ·  5.54 ·  6.46 ·  5.69
        static let cyan    = Color(hex: 0x0E6E8C)      //  5.55 ·  4.96 ·  5.79 ·  5.09
        static let mint    = Color(hex: 0x1E7A5C)      //  5.04 ·  4.51 ·  5.26 ·  4.63  (tightest pair in the app)
        static let amber   = Color(hex: 0x9C4A2C)      //  5.87 ·  5.25 ·  6.12 ·  5.39
        static let rose    = Color(hex: 0xA63F57)      //  5.81 ·  5.19 ·  6.05 ·  5.32
        static let danger  = Color(hex: 0xB03A2E)      //  5.77 ·  5.16 ·  6.02 ·  5.29

        /// Lavender as a FILL on white (4.04:1 — clears the 3:1 graphics rule).
        /// Deeper than the Night fill because the brand hue is tuned for a dark
        /// ground; this is the same top stop the primary pill wears.
        static let lavFill    = Color(hex: 0x6D5FE8)
        /// Primary-button floor — white label makes 6.46:1 here, 4.72:1 on the
        /// top stop, so the CTA deepens rather than the label darkening.
        static let pillFloor  = Color(hex: 0x5B4BC4)
    }

    // MARK: - Semantic palette (what screens & components actually use)
    /// Every member resolves against ``ThemeSnapshot/isNight`` on each read.
    /// Screens are unaffected — they keep reading `Theme.Palette.…` — and the
    /// root view re-keys itself when the theme flips so the tree re-reads.
    enum Palette {
        private static var night_: Bool { ThemeSnapshot.isNight }

        // Backgrounds
        static var night: Color    { night_ ? Brand.night : Dawn.ground }
        static var nightTop: Color { night_ ? Brand.indigoLift : Dawn.groundTop }
        /// Dark ink for LIGHT fills. Deliberately theme-independent: it labels
        /// the cream CTA on constant-dark hero art, which stays dark in Dawn.
        static let ink = Brand.ink

        // Text
        static var text: Color   { night_ ? Brand.whiteText : Dawn.text }
        static var soft: Color   { night_ ? Brand.softText : Dawn.soft }
        static var muted: Color  { night_ ? Brand.mutedText : Dawn.muted }
        static var muted2: Color { night_ ? Brand.captionText : Dawn.muted2 }

        // Accents
        /// Primary brand accent (orb ring) — FILLS only.
        static var lav: Color { night_ ? Brand.periwinkle : Dawn.lavFill }
        /// Lavender AS TEXT/icon paint. The brand hue itself only reaches
        /// 3.61:1 on a raised card over the top gradient (< 4.5 AA), so text
        /// wears the lighter iris — the same in-family fix Android made
        /// (Periwinkle → text-safe variant). Fills keep the true brand hue.
        /// Dawn inverts the direction: the text sibling is *darker*, not lighter.
        static var lavText: Color { night_ ? Brand.iris : Dawn.lavText }
        static var danger: Color  { night_ ? Brand.danger : Dawn.danger }
        static var success: Color { night_ ? Brand.mint : Dawn.mint }
        static var stress: Color  { night_ ? Brand.rose : Dawn.rose }
        static var warm: Color    { night_ ? Brand.amber : Dawn.amber }
        /// Cyan as text (the "Why this works" provenance label).
        static var accentCyan: Color { night_ ? Brand.cyan : Dawn.cyan }
        /// Light "primary" fill on constant-dark art. Theme-independent for the
        /// same reason as ``ink`` — see ``primaryPill`` for the CTA that moves.
        static let cream = Brand.cream

        // Surfaces & lines. Night is translucent glass over the live aurora;
        // Dawn is solid, because a white veil over warm white is invisible.
        static var card: Color         { night_ ? Color.white.opacity(0.075) : Dawn.card }
        static var cardEmphasis: Color { night_ ? Color.white.opacity(0.12) : Dawn.cardRaised }
        static var line: Color         { night_ ? Color.white.opacity(0.13) : Dawn.line }
        /// Text-field / composer / slider surface — one step brighter than `card`.
        static var field: Color        { night_ ? Color.white.opacity(0.10) : Dawn.card }
        /// Incoming-message / inset surface (the "user bubble" indigo). A filled
        /// bubble that carries its own white label (7.03:1) in both themes.
        static let userBubble = Color(hex: 0x46568F)

        // Component pairs the two themes genuinely disagree about.
        /// The primary CTA's label. Night writes ink on cream; Dawn writes white
        /// on the lavender pill (a cream button would vanish on warm white).
        static var onPrimary: Color { night_ ? Brand.ink : .white }
        /// The raised "primary pill" fill for the small CTAs that share the
        /// big button's idiom — send button, step badges, share buttons. Night
        /// wears cream, Dawn the lavender pill. Always pair with ``onPrimary``.
        /// Use the constant ``cream``/``ink`` instead when the pill sits on
        /// constant-dark art (a hero photo, the Night-pinned funnel), where the
        /// light-on-dark reading is correct in both themes.
        static var primaryPill: Color { night_ ? Brand.cream : Dawn.lavFill }
        /// Selected-chip fill + its label. Dawn inverts to an ink pill (17.25:1)
        /// because a cream chip has nothing to sit against.
        static var chipSelectedFill: Color { night_ ? Brand.cream : Dawn.text }
        static var chipSelectedInk: Color  { night_ ? Brand.ink : .white }
        /// App-wide control tint (`.tint`). Night's near-white `soft` would be
        /// invisible on Dawn, so Dawn tints with its text-safe lavender.
        static var tint: Color { night_ ? Brand.softText : Dawn.lavText }

        // Backdrop washes — the soft halo at the top of every screen.
        static var bgWash: Color     { night_ ? Brand.softText.opacity(0.14) : Brand.periwinkle.opacity(0.10) }
        static var auroraWash: Color { night_ ? Brand.softText.opacity(0.12) : Brand.periwinkle.opacity(0.08) }
        /// Multiplier on the drifting aurora orbs. The alphas are tuned for a
        /// deep ground; at full strength on warm white they read as stains.
        static var auroraAlpha: Double { night_ ? 1.0 : 0.42 }
        /// Glow cast by raised elements (primary CTA). Night blooms light;
        /// Dawn casts a soft lavender shadow instead of a dark smear.
        static var lift: Color { night_ ? Brand.softText.opacity(0.28) : Brand.periwinkle.opacity(0.30) }
        /// The wider, stronger bloom around the brand orb. A separate alpha from
        /// ``lift`` on purpose — folding them together would have dimmed Night's
        /// orb from 0.5 to 0.28.
        static var orbGlow: Color { night_ ? Brand.softText.opacity(0.5) : Brand.periwinkle.opacity(0.35) }
    }

    /// Gentle per-section accent tints, all drawn from the brand aurora so the
    /// whole app stays in one calm family while each context shifts hue to orient.
    enum Accent {
        static let calm    = Brand.periwinkle   // home / journal / default (orb periwinkle)
        static let sleep   = Brand.violet       // deep aurora purple
        static let breathe = Brand.teal         // lotus teal
        static let warm    = Brand.amber        // crisis / SOS amber
    }

    // MARK: - Shape (corner radii — one ladder for the whole app)
    /// Centralized corner radii so every surface shares the same rounding language.
    enum Radius {
        static let chip: CGFloat    = 13   // chips, small CTAs, inline pills
        static let control: CGFloat = 16   // fields, wave, compact controls
        static let row: CGFloat     = 18   // list rows, secondary/primary buttons
        static let panel: CGFloat   = 19   // danger / callout panels
        static let banner: CGFloat  = 23   // tool banners
        static let card: CGFloat    = 24   // standard card surface
        static let hero: CGFloat    = 27   // hero / feature cards
    }

    // MARK: - Strokes (glass hairlines)
    /// The translucent edges that give the glass surfaces depth. Expressed once so
    /// every card, row and button shares the same lighting.
    enum Stroke {
        private static var night_: Bool { ThemeSnapshot.isNight }
        /// Dawn's veils are ink rather than white — a white hairline on warm
        /// white draws nothing. Same inversion Android's `Veil*` tokens make.
        private static let inkVeil = Brand.ink

        static var hairline: Color          { night_ ? Color.white.opacity(0.13) : inkVeil.opacity(0.12) }
        static var iconWell: Color          { night_ ? Color.white.opacity(0.08) : inkVeil.opacity(0.06) }
        static var glassTop: Color          { night_ ? Color.white.opacity(0.28) : inkVeil.opacity(0.10) }
        static var glassBottom: Color       { night_ ? Color.white.opacity(0.05) : inkVeil.opacity(0.04) }
        static var glassTopBright: Color    { night_ ? Color.white.opacity(0.40) : inkVeil.opacity(0.16) }
        static var glassBottomBright: Color { night_ ? Color.white.opacity(0.10) : inkVeil.opacity(0.06) }
        /// Trailing chevrons. Dawn uses the muted text role so the affordance
        /// keeps the same visual weight it has on Night.
        static var chevron: Color           { night_ ? Color.white.opacity(0.35) : Dawn.muted2 }
        /// The loading-skeleton sweep. A white sweep over a white Dawn card is
        /// invisible, so Dawn sweeps ink instead.
        static var shimmer: Color           { night_ ? Color.white.opacity(0.35) : inkVeil.opacity(0.10) }
        /// The edge on a light primary fill. Night's cream button takes a bright
        /// white bevel; Dawn's lavender pill takes a softer one.
        static var primaryEdge: Color       { night_ ? Color.white.opacity(0.7) : Color.white.opacity(0.28) }
        /// The edge on the secondary (glass) button. One step stronger than
        /// ``Theme/Palette/line``, which is why it is its own token rather than
        /// reusing it — reusing it would have quietly lightened Night's button.
        static var secondaryEdge: Color     { night_ ? Color.white.opacity(0.18) : Brand.ink.opacity(0.14) }
        // Image scrims sit on constant-dark photography in BOTH themes — a hero
        // photo does not become a light photo when the app does. Never themed.
        static let scrimTop          = Color.black.opacity(0.05)  // image scrim, light end
        static let scrimBottom       = Color.black.opacity(0.82)  // image scrim, dark end

        /// The standard top→bottom bevel used on cards and rows.
        static func bevel(emphasis: Bool = false) -> LinearGradient {
            LinearGradient(
                colors: emphasis ? [glassTopBright, glassBottomBright] : [glassTop, glassBottom],
                startPoint: .top, endPoint: .bottom)
        }
    }

    // MARK: - Gradients (named brand fills)
    enum Gradient {
        /// The primary CTA fill. Night is the light cream pill (ink label);
        /// Dawn deepens to the lavender pill with a white label, because a
        /// near-white button on warm white has no edge to stand on. The label
        /// colour travels with it as ``Palette/onPrimary``.
        static var primaryButton: LinearGradient {
            ThemeSnapshot.isNight
                ? LinearGradient(colors: [.white, Palette.cream], startPoint: .top, endPoint: .bottom)
                : LinearGradient(colors: [Dawn.lavFill, Dawn.pillFloor], startPoint: .top, endPoint: .bottom)
        }

        /// Soft lavender→periwinkle orb (welcome orb, voice orb, breathing ring).
        static let orb = RadialGradient(
            colors: [.white, Brand.lavender, Brand.periwinkle],
            center: .init(x: 0.38, y: 0.34), startRadius: 2, endRadius: 90)

        /// The "Bro" wordmark sweep (iris → periwinkle).
        static let wordmark = LinearGradient(
            colors: [Brand.iris, Brand.periwinkle], startPoint: .leading, endPoint: .trailing)

        /// Image scrim for hero/banner imagery (transparent → near-black).
        static let imageScrim = LinearGradient(
            colors: [Stroke.scrimTop, Stroke.scrimBottom], startPoint: .top, endPoint: .bottom)

        /// A tinted progress/metric fill from the soft periwinkle into a given accent.
        static func accentBar(_ color: Color) -> LinearGradient {
            LinearGradient(colors: [Palette.lav, color], startPoint: .leading, endPoint: .trailing)
        }
    }

    // MARK: - Background gradient used across the app shell
    static var background: some View {
        ZStack {
            RadialGradient(
                colors: [Palette.nightTop, Palette.night],
                center: .init(x: 0.5, y: 0.32),
                startRadius: 0, endRadius: 520
            )
            RadialGradient(
                colors: [Palette.bgWash, .clear],
                center: .top, startRadius: 0, endRadius: 220
            )
        }
        .ignoresSafeArea()
    }

    /// Soft lavender orb gradient (welcome orb, voice orb, breathing ring).
    static let orb = Gradient.orb

}

// MARK: - Animated aurora background
/// The living app backdrop: a deep night radial with three large, blurred
/// brand-tinted orbs that drift slowly for a calm sense of depth and motion —
/// the same aurora hues (periwinkle, aurora-purple, lotus-cyan) as the splash.
struct AppBackground: View {
    /// Primary orb tint — shifts the backdrop hue per section.
    var accent: Color = Theme.Palette.lav
    @State private var drift = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Theme.Palette.nightTop, Theme.Palette.night],
                center: .init(x: 0.5, y: 0.32),
                startRadius: 0, endRadius: 540
            )

            // The orb alphas are tuned against a deep ground; at full strength
            // on Dawn's warm white they read as stains rather than light, so
            // the whole aurora dims through one knob.
            let a = Theme.Palette.auroraAlpha
            aurora(accent.opacity(0.40 * a), size: 380, blur: 95,
                   from: CGSize(width: -70, height: -180), to: CGSize(width: -130, height: -240))
            aurora(Theme.Brand.violet.opacity(0.34 * a), size: 320, blur: 105,
                   from: CGSize(width: 90, height: 30), to: CGSize(width: 140, height: -60))
            aurora(Theme.Brand.cyan.opacity(0.14 * a), size: 260, blur: 85,
                   from: CGSize(width: -50, height: 320), to: CGSize(width: 70, height: 250))

            RadialGradient(
                colors: [Theme.Palette.auroraWash, .clear],
                center: .top, startRadius: 0, endRadius: 240
            )
        }
        .ignoresSafeArea()
        .onAppear { if !reduceMotion { drift = true } }   // stay still for Reduce Motion
    }

    private func aurora(_ color: Color, size: CGFloat, blur: CGFloat,
                        from: CGSize, to: CGSize) -> some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .blur(radius: blur)
            .offset(drift ? to : from)
            .scaleEffect(drift ? 1.12 : 0.95)
            .animation(.easeInOut(duration: 11).repeatForever(autoreverses: true), value: drift)
    }
}

// MARK: - Hex color helper
extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

// MARK: - Dynamic Type–aware system font
/// Applies a system font whose point size scales with the user's Dynamic Type
/// setting via `@ScaledMetric`. At the default setting the size is identical to
/// the design's fixed value, so visual fidelity is preserved while still
/// supporting larger accessibility text sizes.
struct ScaledFont: ViewModifier {
    @ScaledMetric private var size: CGFloat
    private let weight: Font.Weight
    private let design: Font.Design

    init(size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default) {
        _size = ScaledMetric(wrappedValue: size, relativeTo: .body)
        self.weight = weight
        self.design = design
    }

    func body(content: Content) -> some View {
        content.font(.system(size: size, weight: weight, design: design))
    }
}

// MARK: - Font convenience modifiers
extension View {
    /// Dynamic Type–aware system font (drop-in for `.font(.system(size:weight:))`).
    func appFont(_ size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default) -> some View {
        modifier(ScaledFont(size: size, weight: weight, design: design))
    }

    /// Serif display type (scales with Dynamic Type).
    func displayFont(_ size: CGFloat) -> some View {
        modifier(ScaledFont(size: size, weight: .medium, design: .serif))
    }

    /// Uppercase label styling (the `.meta span` / `.top p` look).
    func eyebrow() -> some View {
        self.appFont(11, weight: .heavy)
            .tracking(1.6)
            .textCase(.uppercase)
            .foregroundStyle(Theme.Palette.muted2)
    }
}
