import Foundation

/// Anonymous, first-party product analytics (the 2026-07-04 analytics
/// decision: measure the funnel without breaking the privacy promise).
///
/// Fire-and-forget counts to OUR backend only — zero third-party SDKs. An
/// event is an allowlisted name plus one enumerable step string, keyed by a
/// random per-install id that is never tied to the account (the /events
/// endpoint takes no auth on purpose). Users can switch it off in Privacy &
/// Memory ("Anonymous usage stats"); UI tests (`-resetState`) never send.
enum Analytics {
    private static let idKey = "cerebro_anon_id"
    /// Mirrors AppState.usageStatsOn (same UserDefaults key) so tracking works
    /// from places without an AppState reference.
    static let enabledKey = "usageStatsOn"

    private static let underTest = ProcessInfo.processInfo.arguments.contains("-resetState")

    private static var enabled: Bool {
        UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true
    }

    private static var anonId: String {
        if let id = UserDefaults.standard.string(forKey: idKey) { return id }
        let id = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        UserDefaults.standard.set(id, forKey: idKey)
        return id
    }

    /// Best-effort and non-blocking; failures are dropped silently — analytics
    /// must never affect the product.
    static func track(_ name: String, step: String = "") {
        guard enabled, !underTest else { return }
        let id = anonId
        Task.detached(priority: .utility) {
            await APIClient.shared.sendEvent(name: name, step: step, anonId: id)
        }
    }
}
