import XCTest

/// WCAG contrast gate for the Night palette (IOS_PARITY item 17 — the iOS
/// analogue of Android's ContrastTest).
///
/// ONE-TIME SETUP (macOS): the project has no unit-test target yet — in Xcode:
/// File → New → Target → Unit Testing Bundle, name it `CereBroTests`, then this
/// folder's files join it automatically (synchronized file groups).
///
/// The hex values below are a deliberate BYTE-IDENTICAL PIN of
/// `DesignSystem/Theme.swift` — change the palette and this test together, and
/// only with a computed ratio proving the change clears the gate.
/// Ratios were first computed 2026-07-25, when two text roles failed and were
/// brightened in-family:
///   captionText 0x8F88C0 → 0xA29CCC (was 3.77:1 on a raised card over the
///     top gradient; now 4.77:1)
///   lavender-as-text → Palette.lavText = Brand.iris 0xA9A0F5 (the brand
///     periwinkle 0x8A7BF0 only reaches 3.61:1 there; fills keep the true hue)
final class ContrastTest: XCTestCase {

    // MARK: pinned palette (Theme.swift mirror)
    private let night: UInt32 = 0x0E0C22
    private let nightTop: UInt32 = 0x1A1440
    private let text: UInt32 = 0xF5F4FF
    private let soft: UInt32 = 0xDFE0FF
    private let muted: UInt32 = 0xB0A9E0
    private let muted2: UInt32 = 0xA29CCC        // brightened 2026-07-25
    private let lavFill: UInt32 = 0x8A7BF0       // brand periwinkle — FILLS only
    private let lavText: UInt32 = 0xA9A0F5       // iris — lavender as text
    private let cyan: UInt32 = 0x8FE6EE
    private let mint: UInt32 = 0x7EE0A8
    private let amber: UInt32 = 0xF0A48C
    private let rose: UInt32 = 0xE08A9A
    private let danger: UInt32 = 0xFF8A80
    private let cream: UInt32 = 0xECEEFB
    private let ink: UInt32 = 0x1C1740
    private let userBubble: UInt32 = 0x46568F

    // MARK: WCAG math
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

    /// White at `alpha` composited over a base — the glass surfaces.
    private func whiteOver(_ base: UInt32, _ alpha: Double) -> UInt32 {
        func chan(_ shift: UInt32) -> UInt32 {
            UInt32((255 * alpha + Double((base >> shift) & 255) * (1 - alpha)).rounded())
        }
        return (chan(16) << 16) | (chan(8) << 8) | chan(0)
    }

    /// Every surface a text role can legally sit on. `emphTop` (cardEmphasis
    /// composited over the TOP of the background gradient) is the brightest —
    /// the worst case for light text.
    private var surfaces: [(String, UInt32)] {
        [("night", night), ("nightTop", nightTop),
         ("card", whiteOver(night, 0.075)), ("cardTop", whiteOver(nightTop, 0.075)),
         ("emph", whiteOver(night, 0.12)), ("emphTop", whiteOver(nightTop, 0.12)),
         ("field", whiteOver(night, 0.10))]
    }

    // MARK: gates
    func testEveryTextRoleClearsAAOnEverySurface() {
        let roles: [(String, UInt32)] = [
            ("text", text), ("soft", soft), ("muted", muted), ("muted2", muted2),
            ("lavText", lavText), ("cyan", cyan), ("mint", mint),
            ("amber", amber), ("rose", rose), ("danger", danger),
        ]
        for (rn, rv) in roles {
            for (sn, sv) in surfaces {
                XCTAssertGreaterThanOrEqual(
                    ratio(rv, sv), 4.5,
                    "\(rn) on \(sn) fell below WCAG AA — brighten in-family and re-pin")
            }
        }
    }

    func testLavenderFillStaysLegalAsGraphicsOnly() {
        // The brand hue is a FILL; as a non-text graphic it needs 3:1 (WCAG 1.4.11).
        for (sn, sv) in surfaces {
            XCTAssertGreaterThanOrEqual(ratio(lavFill, sv), 3.0,
                                        "brand lavender as a graphic on \(sn) fell below 3:1")
        }
        // …and this is exactly why it is NOT a text role (< 4.5 on the
        // brightest surface): the assertion documents the split's rationale.
        XCTAssertLessThan(ratio(lavFill, whiteOver(nightTop, 0.12)), 4.5)
    }

    func testButtonAndBubbleInks() {
        XCTAssertGreaterThanOrEqual(ratio(ink, cream), 4.5, "ink on the cream primary button")
        XCTAssertGreaterThanOrEqual(ratio(0xFFFFFF, userBubble), 4.5, "white on the user chat bubble")
    }
}
