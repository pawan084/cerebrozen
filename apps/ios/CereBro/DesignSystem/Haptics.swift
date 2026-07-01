import UIKit

/// Lightweight haptic feedback used across tactile moments (tap-to-talk, a reply
/// arriving, completing a breath). Generators are created on demand and the calls
/// are no-ops on devices without a Taptic Engine, so this is always safe to call.
enum Haptics {
    static func tap(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        let g = UIImpactFeedbackGenerator(style: style)
        g.prepare()
        g.impactOccurred()
    }

    static func soft(intensity: CGFloat = 0.7) {
        let g = UIImpactFeedbackGenerator(style: .soft)
        g.impactOccurred(intensity: intensity)
    }

    static func selection() {
        UISelectionFeedbackGenerator().selectionChanged()
    }

    static func success() { notify(.success) }
    static func warning() { notify(.warning) }
    static func error()   { notify(.error) }

    private static func notify(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        UINotificationFeedbackGenerator().notificationOccurred(type)
    }
}
