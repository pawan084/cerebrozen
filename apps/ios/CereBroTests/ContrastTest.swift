import XCTest
// Needed by the theme-resolution tests at the bottom (`ThemeMode`,
// `themeMode(fromPref:)`, `resolveIsNight`). The palette gates above are
// deliberately self-contained hex pins and need nothing from the app.
@testable import CereBro

/// WCAG contrast gate for BOTH palettes (IOS_PARITY items 17 + 16 — the iOS
/// analogue of Android's ContrastTest).
///
/// ONE-TIME SETUP (macOS): the project has no unit-test target yet — in Xcode:
/// File → New → Target → Unit Testing Bundle, name it `CereBroTests`, then this
/// folder's files join it automatically (synchronized file groups).
///
/// The hex values below are a deliberate BYTE-IDENTICAL PIN of
/// `DesignSystem/Theme.swift` — change a palette and this test together, and
/// only with a computed ratio proving the change clears the gate.
///
/// History:
///   2026-07-25 (item 17) — Night audited; two roles failed and were brightened
///     in-family: captionText 0x8F88C0 → 0xA29CCC (was 3.77:1 on a raised card
///     over the top gradient; now 4.77:1), and lavender-as-text became
///     `Palette.lavText` = Brand.iris 0xA9A0F5 (the brand periwinkle 0x8A7BF0
///     only reaches 3.61:1 there; fills keep the true hue).
///   2026-07-28 (item 16) — Dawn added. Its values are hand-synced with the web
///     app's `[data-theme="dawn"]` block; the roles web has no token for
///     (cyan/mint/rose/danger) are the same hues darkened until each clears AA
///     on white. Night's pins below did NOT move — that is the point of pinning
///     them: Dawn had to land against a tested Night, not reshape it.
final class ContrastTest: XCTestCase {

    // MARK: - A palette under test
    private struct Palette {
        let name: String
        let ground: UInt32          // page floor
        let groundTop: UInt32       // top of the background gradient
        let roles: [(String, UInt32)]
        let lavFill: UInt32         // brand accent used as a FILL, never as text
        /// Every surface a text role can legally sit on.
        let surfaces: [(String, UInt32)]
    }

    // MARK: - WCAG math
    private func luminance(_ hex: UInt32) -> Double {
        func chan(_ v: UInt32) -> Double {
            let c = Double(v) / 255
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * chan((hex >> 16) & 255) + 0.7152 * chan((hex >> 8) & 255) + 0.0722 * chan(hex & 255)
    }

    private func ratio(_ a: UInt32, _ b: UInt32) -> Double {
        let (l1, l2) = (luminance(a), luminance(b))
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /// White at `alpha` composited over a base — Night's glass surfaces.
    private func whiteOver(_ base: UInt32, _ alpha: Double) -> UInt32 {
        func chan(_ shift: UInt32) -> UInt32 {
            UInt32((255 * alpha + Double((base >> shift) & 255) * (1 - alpha)).rounded())
        }
        return (chan(16) << 16) | (chan(8) << 8) | chan(0)
    }

    // MARK: - The two pinned palettes

    /// `emphTop` (cardEmphasis over the TOP of the gradient) is Night's
    /// brightest surface — the worst case for light text.
    private var night: Palette {
        let bg: UInt32 = 0x0E0C22, top: UInt32 = 0x1A1440
        return Palette(
            name: "Night",
            ground: bg, groundTop: top,
            roles: [
                ("text", 0xF5F4FF), ("soft", 0xDFE0FF), ("muted", 0xB0A9E0),
                ("muted2", 0xA29CCC),          // brightened 2026-07-25
                ("lavText", 0xA9A0F5),         // iris — lavender as text
                ("accentCyan", 0x8FE6EE), ("success", 0x7EE0A8),
                ("warm", 0xF0A48C), ("stress", 0xE08A9A), ("danger", 0xFF8A80),
            ],
            lavFill: 0x8A7BF0,                 // brand periwinkle — FILLS only
            surfaces: [
                ("night", bg), ("nightTop", top),
                ("card", whiteOver(bg, 0.075)), ("cardTop", whiteOver(top, 0.075)),
                ("emph", whiteOver(bg, 0.12)), ("emphTop", whiteOver(top, 0.12)),
                ("field", whiteOver(bg, 0.10)),
            ])
    }

    /// Dawn's surfaces are SOLID: a white veil over warm white is invisible, so
    /// the light theme paints real fills instead of glass. `groundTop` is its
    /// darkest paint — the worst case for dark text.
    private var dawn: Palette {
        let bg: UInt32 = 0xFAFAFC, top: UInt32 = 0xECEDF5
        let card: UInt32 = 0xFFFFFF, raised: UInt32 = 0xEFF0F7
        return Palette(
            name: "Dawn",
            ground: bg, groundTop: top,
            roles: [
                ("text", 0x1A1830), ("soft", 0x45426B), ("muted", 0x5A5680),
                ("muted2", 0x655F8A), ("lavText", 0x5B4BC4),
                ("accentCyan", 0x0E6E8C), ("success", 0x1E7A5C),
                ("warm", 0x9C4A2C), ("stress", 0xA63F57), ("danger", 0xB03A2E),
            ],
            lavFill: 0x6D5FE8,
            surfaces: [
                ("ground", bg), ("groundTop", top),
                ("card", card), ("cardRaised", raised), ("field", card),
            ])
    }

    private var palettes: [Palette] { [night, dawn] }

    // MARK: - Gates

    func testEveryTextRoleClearsAAOnEverySurfaceInBothThemes() {
        for p in palettes {
            for (rn, rv) in p.roles {
                for (sn, sv) in p.surfaces {
                    XCTAssertGreaterThanOrEqual(
                        ratio(rv, sv), 4.5,
                        "\(p.name).\(rn) on \(sn) fell below WCAG AA — darken/brighten in-family and re-pin")
                }
            }
        }
    }

    func testBrandFillStaysLegalAsGraphicsOnly() {
        for p in palettes {
            // The brand hue is a FILL; as a non-text graphic it needs 3:1 (WCAG 1.4.11).
            for (sn, sv) in p.surfaces {
                XCTAssertGreaterThanOrEqual(
                    ratio(p.lavFill, sv), 3.0,
                    "\(p.name) brand lavender as a graphic on \(sn) fell below 3:1")
            }
        }
        // …and on Night this is exactly why it is NOT a text role (< 4.5 on the
        // brightest surface): the assertion documents the split's rationale.
        XCTAssertLessThan(ratio(night.lavFill, whiteOver(night.groundTop, 0.12)), 4.5)
    }

    /// The component pairs the two themes deliberately disagree about.
    func testThemedComponentInks() {
        // Night: ink label on the cream primary pill.
        XCTAssertGreaterThanOrEqual(ratio(0x1C1740, 0xECEEFB), 4.5, "Night ink on cream")
        // Dawn: white label on the lavender pill, at BOTH gradient stops.
        XCTAssertGreaterThanOrEqual(ratio(0xFFFFFF, 0x6D5FE8), 4.5, "Dawn onPrimary on the pill top")
        XCTAssertGreaterThanOrEqual(ratio(0xFFFFFF, 0x5B4BC4), 4.5, "Dawn onPrimary on the pill floor")
        // Selected chip inverts in Dawn (cream has nothing to sit against).
        XCTAssertGreaterThanOrEqual(ratio(0x1C1740, 0xECEEFB), 4.5, "Night chip ink on cream")
        XCTAssertGreaterThanOrEqual(ratio(0xFFFFFF, 0x1A1830), 4.5, "Dawn chip white on the ink pill")
        // The user chat bubble is a filled indigo in BOTH themes.
        XCTAssertGreaterThanOrEqual(ratio(0xFFFFFF, 0x46568F), 4.5, "white on the user chat bubble")
    }

    /// Night must not drift while Dawn is tuned. If this fails, someone edited a
    /// Night value — that is allowed, but only deliberately and with the ratio
    /// recomputed above, exactly as the 2026-07-25 audit did.
    func testNightPaletteIsPinnedByteIdentical() {
        let pinned: [String: UInt32] = [
            "text": 0xF5F4FF, "soft": 0xDFE0FF, "muted": 0xB0A9E0, "muted2": 0xA29CCC,
            "lavText": 0xA9A0F5, "accentCyan": 0x8FE6EE, "success": 0x7EE0A8,
            "warm": 0xF0A48C, "stress": 0xE08A9A, "danger": 0xFF8A80,
        ]
        XCTAssertEqual(night.ground, 0x0E0C22)
        XCTAssertEqual(night.groundTop, 0x1A1440)
        XCTAssertEqual(night.lavFill, 0x8A7BF0)
        for (name, hex) in pinned {
            XCTAssertEqual(Dictionary(uniqueKeysWithValues: night.roles)[name], hex,
                           "Night role \(name) moved — re-run the audit before re-pinning")
        }
    }

    // MARK: - Theme resolution (pure logic, no rendering needed)

    func testThemeModeRoundTripsThroughThePreferenceString() {
        for mode in ThemeMode.allCases {
            XCTAssertEqual(themeMode(fromPref: mode.rawValue), mode)
        }
        // Unknown / absent / a value written by an older build → follow the device.
        XCTAssertEqual(themeMode(fromPref: nil), .system)
        XCTAssertEqual(themeMode(fromPref: ""), .system)
        XCTAssertEqual(themeMode(fromPref: "twilight"), .system)
        // The persisted vocabulary is a cross-client contract with Android's
        // `ThemeMode.prefValue()` and the web app's `data-theme`.
        XCTAssertEqual(ThemeMode.system.rawValue, "system")
        XCTAssertEqual(ThemeMode.night.rawValue, "night")
        XCTAssertEqual(ThemeMode.dawn.rawValue, "dawn")
    }

    func testResolveIsNightTruthTable() {
        // forceNight (splash + the signed-out funnel) beats every preference.
        for mode in ThemeMode.allCases {
            for systemDark in [true, false] {
                XCTAssertTrue(resolveIsNight(mode: mode, systemDark: systemDark, forceNight: true),
                              "\(mode) must still paint Night while pinned")
            }
        }
        // Explicit choices ignore the device.
        XCTAssertTrue(resolveIsNight(mode: .night, systemDark: false, forceNight: false))
        XCTAssertFalse(resolveIsNight(mode: .dawn, systemDark: true, forceNight: false))
        // "Match device" follows it.
        XCTAssertTrue(resolveIsNight(mode: .system, systemDark: true, forceNight: false))
        XCTAssertFalse(resolveIsNight(mode: .system, systemDark: false, forceNight: false))
    }
}
