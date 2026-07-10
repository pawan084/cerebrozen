import SwiftUI

/// Branded launch splash, composed in code (so it adapts to any screen with no
/// cropping) and referencing the splash artwork: a night-sky gradient + starfield,
/// the orb-lotus mark (the real brand image, clipped to a circle and glowing),
/// the wordmark + tagline, and an aurora horizon over a mountain-lake silhouette.
struct SplashView: View {
    @State private var appear = false
    @State private var breathe = false
    @State private var shimmer = false
    @State private var drift = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack {
                LinearGradient(
                    colors: [Theme.Brand.night, Theme.Brand.nightMid, Theme.Brand.nightPurple],
                    startPoint: .top, endPoint: .bottom
                )

                Starfield(active: !reduceMotion)
                    .opacity(appear ? 1 : 0.35)
                ShootingStar(active: appear && !reduceMotion)
                    .opacity(appear ? 1 : 0)

                NightScenery(drift: drift && !reduceMotion)
                    .frame(height: geo.size.height * 0.46)
                    .frame(maxHeight: .infinity, alignment: .bottom)
                    .opacity(appear ? 1 : 0)
                    .offset(y: appear ? 0 : 18)

                VStack(spacing: 0) {
                    Spacer(minLength: geo.size.height * 0.12)

                    OrbMark(size: min(w * 0.46, 230), active: breathe && !reduceMotion)
                        .scaleEffect((reduceMotion ? false : breathe) ? 1.035 : 1.0)
                        .scaleEffect(appear ? 1 : 0.94)
                        .opacity(appear ? 1 : 0.72)
                        // Reduce Motion gets the same short fade as every
                        // sibling — not a springy bounce.
                        .animation(reduceMotion ? .easeOut(duration: 0.2)
                                                : .spring(response: 0.8, dampingFraction: 0.72),
                                   value: appear)

                    // The name is part of the brand lockup — visible from the
                    // first rendered frame, like the orb (accents fade in).
                    Wordmark(size: min(w * 0.135, 58), shimmer: shimmer && !reduceMotion)
                        .padding(.top, 22)
                        .opacity(appear ? 1 : 0.85)
                        .offset(y: appear ? 0 : 6)

                    Text("Your AI Companion\nfor Mental Wellness")
                        .multilineTextAlignment(.center)
                        .appFont(16, weight: .medium)
                        .foregroundStyle(.white.opacity(0.82))
                        .lineSpacing(3)
                        .padding(.top, 14)
                        .opacity(appear ? 1 : 0)

                    Spacer()

                    HStack(spacing: 7) {
                        NativeEffectIcon(systemImage: "heart.fill", size: 13, weight: .semibold,
                                         color: Theme.Palette.lav, effect: .pulse, active: appear)
                        Text("You matter. Always.")
                            .appFont(14, weight: .semibold).foregroundStyle(.white.opacity(0.7))
                    }
                    .padding(.bottom, geo.size.height * 0.045)
                    .opacity(appear ? 1 : 0)
                    .offset(y: appear ? 0 : 8)
                }
                .padding(.horizontal, 28)
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.easeOut(duration: reduceMotion ? 0.2 : 0.8)) { appear = true }
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.4).repeatForever(autoreverses: true)) { breathe = true }
            // One-directional sweep: a shine never travels backwards. The jump
            // back happens fully off-glyph (offset −1.2→4.2 sizes), so the
            // restart is invisible.
            withAnimation(.linear(duration: 2.2).repeatForever(autoreverses: false).delay(0.25)) { shimmer = true }
            withAnimation(.easeInOut(duration: 5.6).repeatForever(autoreverses: true)) { drift = true }
        }
    }
}

private struct OrbMark: View {
    let size: CGFloat
    var active: Bool

    var body: some View {
        ZStack {
            Circle()
                .strokeBorder(
                    AngularGradient(colors: [.clear,
                                             Theme.Brand.cyan.opacity(0.8),
                                             .white.opacity(0.9),
                                             Theme.Brand.periwinkle.opacity(0.75),
                                             .clear],
                                    center: .center),
                    lineWidth: 2.5
                )
                .frame(width: size * 1.18, height: size * 1.18)
                .blur(radius: 0.4)
                .rotationEffect(.degrees(active ? 360 : 0))
                // Rotation loops seamlessly (360 ≡ 0); opacity must NOT share
                // the repeating transaction or it snaps back every cycle.
                .animation(.linear(duration: 4.2).repeatForever(autoreverses: false), value: active)
                .opacity(active ? 0.8 : 0.28)
                .animation(.easeOut(duration: 0.8), value: active)

            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .stroke(Theme.Brand.lavender.opacity(0.28 - Double(index) * 0.06), lineWidth: 1.2)
                    .frame(width: size * (1.08 + CGFloat(index) * 0.18),
                           height: size * (1.08 + CGFloat(index) * 0.18))
                    .scaleEffect(active ? 1.16 : 0.92)
                    .opacity(active ? 0.18 : 0.5)
                    .animation(.easeInOut(duration: 2.8 + Double(index) * 0.35).repeatForever(autoreverses: true), value: active)
            }

            Circle()
                .fill(RadialGradient(colors: [Theme.Brand.periwinkle.opacity(0.62), .clear],
                                     center: .center, startRadius: size * 0.1, endRadius: size * 0.8))
                .frame(width: size * 1.7, height: size * 1.7)
                // Constant radius: animating Gaussian blur re-renders the
                // ~390pt glow every frame; the parent's breathe scale already
                // carries the motion.
                .blur(radius: 27)

            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .shadow(color: Theme.Brand.cyan.opacity(0.2), radius: 13)
        }
    }
}

private struct Wordmark: View {
    let size: CGFloat
    var shimmer: Bool

    var body: some View {
        HStack(spacing: 0) {
            Text("Cere").foregroundStyle(.white)
            Text("Bro").foregroundStyle(Theme.Gradient.wordmark)
        }
        .font(.system(size: size, weight: .bold, design: .rounded))
        .overlay(alignment: .leading) {
            // Feather along the sweep direction so the glint has soft edges.
            LinearGradient(colors: [.clear, .white.opacity(0.7), .clear],
                           startPoint: .leading, endPoint: .trailing)
                .frame(width: size * 0.6)
                .rotationEffect(.degrees(18))
                .offset(x: shimmer ? size * 4.2 : -size * 1.2)
                .blendMode(.screen)
                .opacity(0.45)
        }
        .mask {
            HStack(spacing: 0) {
                Text("Cere")
                Text("Bro")
            }
            .font(.system(size: size, weight: .bold, design: .rounded))
        }
        .shadow(color: Theme.Brand.periwinkle.opacity(0.35), radius: 18, y: 4)
    }
}

private struct Starfield: View {
    var active: Bool
    private let count = 46

    private func h(_ i: Int, _ salt: Double) -> Double {
        let v = sin(Double(i) * 12.9898 + salt) * 43758.5453
        return v - floor(v)
    }

    var body: some View {
        TimelineView(.animation(paused: !active)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            Canvas { ctx, sz in
                for i in 0..<count {
                    let x = h(i, 0) * sz.width
                    let y = h(i, 7.1) * sz.height * 0.62
                    let r = 0.6 + h(i, 3.3) * 1.6
                    let base = 0.25 + h(i, 9.9) * 0.6
                    let twinkle = active ? (sin(t * (0.8 + h(i, 4.4)) + Double(i)) + 1) * 0.22 : 0
                    ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r, height: r)),
                             with: .color(.white.opacity(min(0.95, base + twinkle))))
                }
            }
        }
        .allowsHitTesting(false)
    }
}

private struct ShootingStar: View {
    var active: Bool
    // Anchor the cycle to appearance: wall-clock phase meant ~half of all
    // launches began with the star already mid-sky.
    @State private var born = Date()

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused: !active)) { timeline in
                let t = timeline.date.timeIntervalSince(born)
                let progress = active ? CGFloat(max(0, t).truncatingRemainder(dividingBy: 3.2) / 3.2) : 0
                let flight = max(0, min(1, (progress - 0.18) / 0.54))
                let x = geo.size.width * (-0.15 + flight * 1.25)
                let y = geo.size.height * (0.15 + flight * 0.22)
                // Ease in and out along the flight — no freeze, no hard blink.
                let fade = flight <= 0 || flight >= 1 ? 0 : min(1, min(flight / 0.12, (1 - flight) / 0.18))

                Capsule()
                    .fill(LinearGradient(colors: [.clear, .white.opacity(0.95), Theme.Brand.cyan.opacity(0.15)],
                                         startPoint: .leading, endPoint: .trailing))
                    .frame(width: geo.size.width * 0.24, height: 2)
                    .blur(radius: 0.8)
                    .rotationEffect(.degrees(-24))
                    .position(x: x, y: y)
                    .opacity(fade)
            }
        }
        .allowsHitTesting(false)
        .onAppear { born = Date() }
    }
}

private struct NightScenery: View {
    var drift: Bool

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            ZStack {
                Ellipse()
                    .fill(LinearGradient(colors: [Theme.Brand.cyan.opacity(0.5), .clear],
                                         startPoint: .leading, endPoint: .trailing))
                    .frame(width: w * 1.1, height: h * 0.5)
                    .blur(radius: 38)
                    .offset(x: drift ? -w * 0.08 : -w * 0.18, y: drift ? h * 0.08 : h * 0.12)
                Ellipse()
                    .fill(LinearGradient(colors: [.clear, Theme.Brand.violet.opacity(0.5)],
                                         startPoint: .leading, endPoint: .trailing))
                    .frame(width: w * 1.1, height: h * 0.5)
                    .blur(radius: 40)
                    .offset(x: drift ? w * 0.1 : w * 0.2, y: drift ? h * 0.11 : h * 0.16)

                Circle()
                    .fill(RadialGradient(colors: [Theme.Brand.lavender, Theme.Brand.violet.opacity(0.0)],
                                         center: .center, startRadius: 1, endRadius: w * 0.28))
                    .frame(width: w * 0.6, height: w * 0.6)
                    .position(x: w * 0.5, y: h * 0.62)
                Rectangle()
                    .fill(LinearGradient(colors: [Theme.Brand.lavender.opacity(0.5), .clear],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(width: w * 0.06, height: h * 0.34)
                    .blur(radius: 3)
                    .position(x: w * 0.5, y: h * 0.8)

                MountainRidge(peaks: [0.30, 0.52, 0.34, 0.6, 0.4, 0.5, 0.32])
                    .fill(LinearGradient(colors: [Color(hex: 0x2A2570), Color(hex: 0x191550)],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(height: h * 0.5).frame(maxHeight: .infinity, alignment: .bottom)
                    .offset(y: h * 0.04)
                MountainRidge(peaks: [0.18, 0.4, 0.22, 0.46, 0.26, 0.42, 0.2])
                    .fill(LinearGradient(colors: [Color(hex: 0x151138), Color(hex: 0x0C0A24)],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(height: h * 0.42).frame(maxHeight: .infinity, alignment: .bottom)

                LinearGradient(colors: [Color(hex: 0x141A52).opacity(0.0), Color(hex: 0x0B0A28)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: h * 0.26).frame(maxHeight: .infinity, alignment: .bottom)

                LakeShimmer(active: drift)
                    .frame(height: h * 0.22)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .allowsHitTesting(false)
    }
}

private struct LakeShimmer: View {
    var active: Bool

    var body: some View {
        TimelineView(.animation(paused: !active)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            Canvas { ctx, sz in
                for i in 0..<6 {
                    let y = sz.height * (0.18 + CGFloat(i) * 0.115)
                    let width = sz.width * (0.18 + CGFloat(i) * 0.055)
                    let x = sz.width * 0.5 + CGFloat(sin(t * 0.8 + Double(i))) * 18
                    let rect = CGRect(x: x - width / 2, y: y, width: width, height: 1.4)
                    ctx.fill(Path(roundedRect: rect, cornerRadius: 1),
                             with: .color(Theme.Brand.lavender.opacity(0.18 - Double(i) * 0.015)))
                }
            }
        }
        .blendMode(.screen)
    }
}

private struct MountainRidge: Shape {
    let peaks: [CGFloat]

    func path(in rect: CGRect) -> Path {
        var p = Path()
        guard peaks.count > 1 else { return p }
        let step = rect.width / CGFloat(peaks.count - 1)
        func y(_ i: Int) -> CGFloat { rect.maxY - peaks[i] * rect.height }
        p.move(to: CGPoint(x: 0, y: rect.maxY))
        p.addLine(to: CGPoint(x: 0, y: y(0)))
        for i in 1..<peaks.count {
            let x = step * CGFloat(i)
            let midX = x - step / 2
            p.addQuadCurve(to: CGPoint(x: x, y: y(i)), control: CGPoint(x: midX, y: y(i - 1)))
        }
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}
