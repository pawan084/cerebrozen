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
        // Night sky — the splash gradient, deepest → lifted.
        static let nightDeep    = Color(hex: 0x060814)   // darkest corner of the sky
        static let night        = Color(hex: 0x080B22)   // primary background
        static let nightMid     = Color(hex: 0x121A47)   // mid sky
        static let nightPurple  = Color(hex: 0x1C1652)   // purple-ink, lower sky
        static let indigoLift   = Color(hex: 0x232C66)   // periwinkle-tinted radial highlight
        static let ink          = Color(hex: 0x171D43)   // deep ink (text on light fills)

        // The orb-lotus mark.
        static let periwinkle   = Color(hex: 0x6F7BF7)   // the orb ring — the primary brand hue
        static let iris         = Color(hex: 0x8FA8FF)   // lighter blue (the "Bro" wordmark)
        static let lavender     = Color(hex: 0xCBB6FF)   // horizon glow / soft highlight
        static let cyan         = Color(hex: 0x36C7F5)   // aurora cyan / lotus rim
        static let teal         = Color(hex: 0x4FD8E0)   // lotus teal
        static let violet       = Color(hex: 0x9B6BFF)   // aurora purple

        // Neutrals & utility.
        static let whiteText    = Color(hex: 0xF4F6FF)   // primary text on dark
        static let softText     = Color(hex: 0xDFE5FF)   // secondary text / accents
        static let mutedText    = Color(hex: 0xAEB6E0)   // tertiary text
        static let captionText  = Color(hex: 0x838CBB)   // captions / labels
        static let cream        = Color(hex: 0xECEEFB)   // light "primary" button fill
        static let amber        = Color(hex: 0xE6A98C)   // warm / crisis accent
        static let rose         = Color(hex: 0xE0A0AD)   // stress / caution metric
        static let mint         = Color(hex: 0x9AD6B4)   // success / connected
        static let danger       = Color(hex: 0xFF8A80)   // safety / destructive
    }

    // MARK: - Semantic palette (what screens & components actually use)
    enum Palette {
        // Backgrounds
        static let night    = Brand.night          // deepest background
        static let nightTop = Brand.indigoLift     // radial highlight at top of screens
        static let ink      = Brand.ink            // dark ink (on light buttons)

        // Text
        static let text     = Brand.whiteText      // primary text on dark
        static let soft     = Brand.softText       // secondary text / accents
        static let muted    = Brand.mutedText      // tertiary text
        static let muted2   = Brand.captionText    // captions / labels

        // Accents
        static let lav      = Brand.periwinkle     // primary brand accent (orb ring)
        static let danger   = Brand.danger         // safety / destructive
        static let success  = Brand.mint           // connected / healthy state
        static let stress   = Brand.rose           // stress / caution metric accent
        static let cream    = Brand.cream          // light "primary" button fill

        // Surfaces & lines (translucent glass over the night backdrop)
        static let card         = Color.white.opacity(0.075)
        static let cardEmphasis = Color.white.opacity(0.12)
        static let line         = Color.white.opacity(0.13)
        /// Text-field / composer / slider surface — one step brighter than `card`.
        static let field        = Color.white.opacity(0.10)
        /// Incoming-message / inset surface (the "user bubble" indigo).
        static let userBubble   = Color(hex: 0x46568F)
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
        static let hairline          = Color.white.opacity(0.13)  // default 1px edge
        static let iconWell          = Color.white.opacity(0.08)  // circular icon backdrops
        static let glassTop          = Color.white.opacity(0.28)  // top of a glass bevel
        static let glassBottom       = Color.white.opacity(0.05)  // bottom of a glass bevel
        static let glassTopBright    = Color.white.opacity(0.40)  // emphasized bevel top
        static let glassBottomBright = Color.white.opacity(0.10)
        static let chevron           = Color.white.opacity(0.35)  // trailing chevrons
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
        /// Light "primary" button fill (white → cream).
        static let primaryButton = LinearGradient(
            colors: [.white, Palette.cream], startPoint: .top, endPoint: .bottom)

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
                colors: [Palette.soft.opacity(0.14), .clear],
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

            aurora(accent.opacity(0.40), size: 380, blur: 95,
                   from: CGSize(width: -70, height: -180), to: CGSize(width: -130, height: -240))
            aurora(Theme.Brand.violet.opacity(0.34), size: 320, blur: 105,
                   from: CGSize(width: 90, height: 30), to: CGSize(width: 140, height: -60))
            aurora(Theme.Brand.cyan.opacity(0.14), size: 260, blur: 85,
                   from: CGSize(width: -50, height: 320), to: CGSize(width: 70, height: 250))

            RadialGradient(
                colors: [Theme.Palette.soft.opacity(0.12), .clear],
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
