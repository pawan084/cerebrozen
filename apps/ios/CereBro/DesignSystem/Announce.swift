import UIKit

/// VoiceOver announcements for things that happen without the user acting.
///
/// A streamed reply arrives over seconds, and a paused tool confirmation can
/// stop the stream indefinitely. Sighted users see both immediately; a
/// VoiceOver user is told nothing unless the app says so, because neither
/// changes focus and `.updatesFrequently` only matters once the element is
/// already focused.
///
/// Uses the **attributed** form with `.high` priority rather than the plain
/// string post. A plain `.announcement` is DROPPED if VoiceOver is mid-speech —
/// which is exactly the situation here, since the user has usually just heard
/// their own message echoed. High priority queues instead of vanishing, so the
/// announcement that matters most (a reply, or a request for approval) is the
/// one least likely to survive the naive call.
enum Announce {
    /// Speak `text` if VoiceOver is on. No-op otherwise — the check keeps this
    /// free for the overwhelming majority of runs.
    static func voiceOver(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard UIAccessibility.isVoiceOverRunning, !trimmed.isEmpty else { return }
        let announcement = NSAttributedString(
            string: trimmed,
            attributes: [.accessibilitySpeechAnnouncementPriority: UIAccessibilityPriority.high]
        )
        UIAccessibility.post(notification: .announcement, argument: announcement)
    }
}
