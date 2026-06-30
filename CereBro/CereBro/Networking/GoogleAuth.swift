import AuthenticationServices
import UIKit

/// Google Sign-In via the system browser (`ASWebAuthenticationSession`) — no
/// third-party SDK. Runs Google's OAuth *implicit* flow to obtain an ID token,
/// which the backend verifies at `/auth/google`.
///
/// Config: set your OAuth **iOS client ID** as `GIDClientID` in Info.plist (and
/// add the reversed-client-id URL scheme). Until then `isConfigured` is false and
/// the flow surfaces a friendly "not set up yet" error — the same graceful
/// degradation as Sign in with Apple before its capability is enabled.
enum GoogleAuthError: LocalizedError {
    case notConfigured, cancelled, failed
    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Google sign-in isn't set up yet (add your Google client ID)."
        case .cancelled:     return "Sign-in cancelled."
        case .failed:        return "Couldn't complete Google sign-in."
        }
    }
}

@MainActor
final class GoogleAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    /// OAuth iOS client ID from Info.plist (`GIDClientID`). Empty → not configured.
    static var clientID: String { Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String ?? "" }
    static var isConfigured: Bool { !clientID.isEmpty }

    private var session: ASWebAuthenticationSession?

    /// Present Google's consent screen and return the resulting ID token.
    func signIn() async throws -> String {
        let clientID = Self.clientID
        guard !clientID.isEmpty else { throw GoogleAuthError.notConfigured }

        let scheme = Self.reversedClientID(clientID)
        let redirect = "\(scheme):/oauth2redirect"
        var comp = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        comp.queryItems = [
            .init(name: "client_id", value: clientID),
            .init(name: "redirect_uri", value: redirect),
            .init(name: "response_type", value: "id_token"),
            .init(name: "scope", value: "openid email profile"),
            .init(name: "nonce", value: UUID().uuidString),
            .init(name: "prompt", value: "select_account"),
        ]
        guard let url = comp.url else { throw GoogleAuthError.failed }

        return try await withCheckedThrowingContinuation { cont in
            let s = ASWebAuthenticationSession(url: url, callbackURLScheme: scheme) { callback, error in
                if let error {
                    let cancelled = (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin
                    cont.resume(throwing: cancelled ? GoogleAuthError.cancelled : GoogleAuthError.failed)
                    return
                }
                guard let callback, let token = Self.idToken(from: callback) else {
                    cont.resume(throwing: GoogleAuthError.failed); return
                }
                cont.resume(returning: token)
            }
            s.presentationContextProvider = self
            self.session = s
            if !s.start() { cont.resume(throwing: GoogleAuthError.failed) }
        }
    }

    /// "1234-abc.apps.googleusercontent.com" → "com.googleusercontent.apps.1234-abc"
    private static func reversedClientID(_ id: String) -> String {
        let suffix = ".apps.googleusercontent.com"
        guard id.hasSuffix(suffix) else { return id }
        return "com.googleusercontent.apps.\(id.dropLast(suffix.count))"
    }

    /// The implicit flow returns parameters in the URL fragment.
    private static func idToken(from url: URL) -> String? {
        guard let fragment = URLComponents(url: url, resolvingAgainstBaseURL: false)?.fragment else { return nil }
        for pair in fragment.split(separator: "&") {
            let kv = pair.split(separator: "=", maxSplits: 1)
            if kv.count == 2, kv[0] == "id_token" { return String(kv[1]).removingPercentEncoding }
        }
        return nil
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        return scene?.keyWindow ?? ASPresentationAnchor()
    }
}
