import Foundation
import UIKit
import UserNotifications

/// Remote push (APNs) registration.
///
/// The server side of nudges has always been real — `services/notifications.py`
/// signs an ES256 JWT and posts an `alert` push to APNs — but nothing ever
/// populated `user.push_token`, so that branch was unreachable. This is the
/// missing half: get a device token from APNs and hand it to the backend.
///
/// Two deliberate rules, both inherited from `ReminderManager`:
///
/// 1. **Never prompt on our own.** `registerForRemoteNotifications()` does not
///    show UI, but a token is worthless without notification authorization —
///    and that is only ever requested from an explicit user action (the
///    Reminders toggle, or the onboarding reminder step). So registration is
///    gated on already being authorized, and is re-attempted right after the
///    user grants permission.
/// 2. **Silent under UI tests.** `-resetState YES` runs must not touch the
///    OS notification stack.
///
/// The token is cached in `UserDefaults` because it almost always arrives while
/// signed out (onboarding grants permission long before an account exists);
/// `BackendService` drains the cache on every connect, the same way it replays
/// consent, region and companion style.
enum PushRegistrar {
    private enum Key {
        static let device = "pushDeviceToken"      // last token APNs gave us
        static let synced = "pushTokenSynced"      // last token the server accepted
    }

    /// Posted when APNs hands us a token, so a session that is *already*
    /// connected can register it without waiting for the next launch.
    static let tokenReceived = Notification.Name("cerebro.pushTokenReceived")

    static var underTest: Bool {
        ProcessInfo.processInfo.arguments.contains("-resetState")
    }

    /// The device token APNs last issued, if any.
    static var deviceToken: String? {
        UserDefaults.standard.string(forKey: Key.device)
    }

    /// Ask APNs for a token, but only when notifications are already authorized
    /// (see rule 1). Safe to call repeatedly — APNs returns the current token.
    static func registerIfAuthorized() async {
        guard !underTest else { return }
        guard await ReminderManager.isAuthorized() else { return }
        await MainActor.run { UIApplication.shared.registerForRemoteNotifications() }
    }

    /// APNs delivered a token. Store it and let any live session know.
    static func didRegister(deviceToken raw: Data) {
        let token = raw.map { String(format: "%02x", $0) }.joined()
        guard !token.isEmpty else { return }
        if token != deviceToken { UserDefaults.standard.removeObject(forKey: Key.synced) }
        UserDefaults.standard.set(token, forKey: Key.device)
        NotificationCenter.default.post(name: tokenReceived, object: nil)
    }

    /// Registration failed (no APNs entitlement yet, no network, Simulator
    /// without a paired push service). Nudges fall back to web-push/email
    /// server-side, so this stays silent rather than surfacing an error.
    static func didFail(_ error: Error) {
        #if DEBUG
        print("[push] registration failed: \(error.localizedDescription)")
        #endif
    }

    /// The token still owed to the server, or nil when there's nothing new to
    /// send (no token yet, or the server already has this one).
    static func unsyncedToken() -> String? {
        guard let token = deviceToken else { return nil }
        return token == UserDefaults.standard.string(forKey: Key.synced) ? nil : token
    }

    /// Remember that the server accepted this token, so we don't re-PUT it on
    /// every connect.
    static func markSynced(_ token: String) {
        UserDefaults.standard.set(token, forKey: Key.synced)
    }

    /// Signing out invalidates the association between this device and that
    /// account — the next connect must re-register.
    static func clearSyncedMark() {
        UserDefaults.standard.removeObject(forKey: Key.synced)
    }
}

/// SwiftUI has no hook for the APNs callbacks, so the app needs a delegate.
/// It exists for exactly that, plus foreground presentation of nudges.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        if !PushRegistrar.underTest {
            UNUserNotificationCenter.current().delegate = self
        }
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushRegistrar.didRegister(deviceToken: deviceToken)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        PushRegistrar.didFail(error)
    }

    /// A nudge that lands while the app is open is still worth showing — it is
    /// the same gentle check-in prompt, and swallowing it silently would make
    /// the reminder look unreliable.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}
