import SwiftUI

/// The signature voice element: a layered orb that *reacts* to the conversation.
/// - idle      → gentle breathing
/// - recording → pulses with your live mic level, bright attentive tint
/// - thinking  → a rotating conic shimmer ring
/// - speaking  → pulses with the playback envelope, warm teal presence
/// Expanding ripple rings radiate while it's actively listening or speaking.
/// Honors Reduce Motion (settles to a calm static state).
struct VoiceOrb: View {
    var phase: VoiceCompanion.Phase
    var level: CGFloat = 0
    var size: CGFloat = 150
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var active: Bool {
        switch phase { case .recording, .speaking: return true; default: return false }
    }
    private var thinking: Bool { if case .thinking = phase { return true } else { return false } }

    private var tint: Color {
        switch phase {
        case .recording: return Theme.Palette.soft
        case .thinking:  return Theme.Accent.sleep
        case .speaking:  return Theme.Accent.breathe
        case .error:     return Theme.Palette.danger
        default:         return Theme.Palette.lav
        }
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let breath = (sin(t * 1.5) + 1) / 2
            let lvl = reduceMotion ? 0 : max(0, min(1, level))
            let pulse: CGFloat = active ? lvl : CGFloat(breath) * 0.22

            ZStack {
                if active && !reduceMotion {
                    ForEach(0..<3, id: \.self) { i in
                        let p = ((t * 0.55) + Double(i) / 3).truncatingRemainder(dividingBy: 1)
                        Circle()
                            .stroke(tint.opacity(0.45 * (1 - p)), lineWidth: 2)
                            .frame(width: size * (0.72 + CGFloat(p) * 1.15),
                                   height: size * (0.72 + CGFloat(p) * 1.15))
                    }
                }

                Circle().fill(gradient)
                    .frame(width: size, height: size)
                    .blur(radius: size * 0.3)
                    .opacity(0.8)
                    .scaleEffect(0.9 + pulse * 0.55)

                if thinking && !reduceMotion {
                    Circle()
                        .strokeBorder(
                            AngularGradient(colors: [.clear, tint.opacity(0.8), .white, tint.opacity(0.8), .clear],
                                            center: .center),
                            lineWidth: 4)
                        .frame(width: size * 1.06, height: size * 1.06)
                        .rotationEffect(.radians(t * 2.4))
                }

                Circle().fill(gradient)
                    .frame(width: size, height: size)
                    .scaleEffect(1 + pulse * 0.13)

                Circle()
                    .fill(RadialGradient(colors: [.white.opacity(0.95), .clear],
                                         center: .init(x: 0.36, y: 0.3),
                                         startRadius: 0, endRadius: size * 0.42))
                    .frame(width: size, height: size)
                    .scaleEffect(1 + pulse * 0.13)
            }
            .shadow(color: tint.opacity(0.55), radius: 26 + pulse * 34)
            .animation(.easeOut(duration: 0.3), value: phase)
        }
        .frame(width: size * 1.9, height: size * 1.9)
        .accessibilityHidden(true)
    }

    private var gradient: RadialGradient {
        RadialGradient(colors: [.white, Theme.Palette.soft, tint],
                       center: .init(x: 0.38, y: 0.34), startRadius: 2, endRadius: size * 0.62)
    }
}

/// Audio-reactive bar waveform. Bars rise with the live `level` and shimmer with
/// a travelling sine so it feels alive even at low input; flat & calm when idle.
struct LiveWaveform: View {
    var level: CGFloat = 0
    var active: Bool = false
    var tint: Color = Theme.Palette.lav
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let count = 13

    var body: some View {
        TimelineView(.animation(paused: reduceMotion)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            HStack(spacing: 4) {
                ForEach(0..<count, id: \.self) { i in
                    let wobble = (sin(t * 6 + Double(i) * 0.55) + 1) / 2
                    let base: CGFloat = active ? (0.28 + level * 0.95) : 0.2
                    let h = max(0.12, min(1, base * (0.45 + 0.95 * CGFloat(wobble))))
                    Capsule()
                        .fill(LinearGradient(colors: [Theme.Palette.soft, tint],
                                             startPoint: .top, endPoint: .bottom))
                        .frame(width: 5, height: 40 * h + 6)
                }
            }
            .frame(height: 46).frame(maxWidth: .infinity)
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.Palette.line))
        }
    }
}
