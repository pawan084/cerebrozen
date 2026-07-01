import Foundation

/// A tiny on-device companion so signed-out / offline users still get a real
/// first session instead of silence. Deliberately simple and safe: warm,
/// reflective replies plus crisis-keyword detection that mirrors the backend
/// safety classifier's fallback terms. The cloud AI companion (once signed in)
/// is far richer — this only removes the empty-room dead end.
enum LocalCompanion {
    /// Crisis phrases mirror backend `services/safety._CRISIS_TERMS`.
    private static let crisisTerms = [
        "kill myself", "end my life", "suicide", "want to die", "better off dead",
        "no reason to live", "hurt myself", "self harm", "self-harm",
    ]

    static func isCrisis(_ text: String) -> Bool {
        let lowered = text.lowercased()
        return crisisTerms.contains { lowered.contains($0) }
    }

    /// A warm, non-clinical reflection. Never diagnoses or prescribes.
    static func reply(to text: String) -> String {
        if isCrisis(text) {
            return "I'm really glad you told me — you matter. I can't handle emergencies, "
                + "but the people on the crisis lines can help right now. You'll find them "
                + "under Urgent support. Would you like to breathe together while you reach out?"
        }
        let lowered = text.lowercased()
        if lowered.contains("anx") || lowered.contains("stress") || lowered.contains("overwhelm") {
            return "That pressure sounds heavy. What's the loudest thought right now — "
                + "and would it help to calm the body first, or unpack it?"
        }
        if lowered.contains("sleep") || lowered.contains("tired") || lowered.contains("can't sleep") {
            return "Rest is hard when the mind won't settle. Want to try a slow wind-down "
                + "breath, or name what's keeping you up?"
        }
        if lowered.contains("sad") || lowered.contains("low") || lowered.contains("lonely") || lowered.contains("alone") {
            return "Thank you for naming that — it takes courage. I'm here with you. "
                + "What would feel like a small kindness to yourself tonight?"
        }
        if lowered.contains("angry") || lowered.contains("frustrat") || lowered.contains("mad") {
            return "That frustration is valid. What happened just before it flared — "
                + "and what does it need from you right now?"
        }
        return "I hear you. Tell me a little more about what that feels like, "
            + "and we'll take it one gentle step at a time."
    }
}
