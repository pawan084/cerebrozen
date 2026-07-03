import AuthenticationServices
import SwiftUI

/// The shared sign-in / create-account form: Apple → Google → email, with the
/// dev server plumbing behind a collapsed disclosure (DEBUG only). Used by the
/// Cloud Sync screen and embedded directly in onboarding's account step.
struct AuthForm: View {
    enum Mode { case signIn, signUp }

    @EnvironmentObject var backend: BackendService
    // Empty by default — production users start with a clean form. Demo
    // credentials + the local server are pre-filled only in DEBUG (onAppear),
    // and only for the sign-in tab (never into a create-account form).
    @State private var email = ""
    @State private var password = ""
    @State private var name = ""
    @State private var mode: Mode
    @State private var googleAuth = GoogleAuth()
    @State private var googleMessage: String?
    @State private var resetMessage: String?

    private let initialMode: Mode
    /// Under UITests (`-resetState`) the password field opts out of AutoFill
    /// (.oneTimeCode) so iOS never presents the system "Save Password?" sheet,
    /// which would swallow the suite's taps. Real users get full AutoFill.
    private static let underTest = ProcessInfo.processInfo.arguments.contains("-resetState")

    /// `initialMode` picks the segmented tab the form opens on — onboarding's
    /// account step embeds with `.signUp`; Cloud Sync defaults to `.signIn`.
    init(initialMode: Mode = .signIn) {
        self.initialMode = initialMode
        _mode = State(initialValue: initialMode)
    }

    var body: some View {
        VStack(spacing: 12) {
            // Social sign-in first — the modern pattern. (Apple is required by the
            // App Store wherever a third-party sign-in like Google is offered.)
            SignInWithAppleButton(.signIn) { request in
                request.requestedScopes = [.fullName, .email]
            } onCompletion: { result in
                handleApple(result)
            }
            .signInWithAppleButtonStyle(.white)
            .frame(height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .accessibilityIdentifier("Sign in with Apple")

            googleButton
            if let googleMessage {
                Text(googleMessage).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(spacing: 8) {
                Rectangle().fill(Theme.Palette.line).frame(height: 1)
                Text("or use email").appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
                Rectangle().fill(Theme.Palette.line).frame(height: 1)
            }

            Picker("", selection: $mode) {
                Text("Sign in").tag(Mode.signIn)
                Text("Create account").tag(Mode.signUp)
            }
            .pickerStyle(.segmented)
            .accessibilityLabel("Sign in or create account")

            if mode == .signUp {
                field("Name", text: $name)
            }
            field("Email", text: $email, keyboard: .emailAddress)
            field("Password", text: $password, secure: true)

            PrimaryButton(title: mode == .signIn ? "Continue with email" : "Create my account",
                          systemImage: "envelope.fill") {
                Task {
                    if mode == .signIn {
                        await backend.signIn(email: email, password: password)
                    } else {
                        await backend.signUp(email: email, password: password, name: name)
                    }
                }
            }

            if mode == .signIn {
                Button {
                    Task {
                        try? await backend.requestPasswordReset(email: email)
                        resetMessage = "If that email exists, a reset link is on its way."
                    }
                } label: {
                    Text("Forgot password?").appFont(12, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                }
                .buttonStyle(.pressable)
                .disabled(email.isEmpty)
                if let resetMessage {
                    Text(resetMessage).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    // Modern "Continue with Google" button (white, matching the Apple button).
    private var googleButton: some View {
        Button { signInWithGoogle() } label: {
            HStack(spacing: 8) {
                Image(systemName: "g.circle.fill").appFont(18, weight: .bold)
                Text("Continue with Google").appFont(15, weight: .semibold)
            }
            .foregroundStyle(Theme.Palette.ink)
            .frame(maxWidth: .infinity).frame(height: 50)
            .background(.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.pressable)
        .accessibilityIdentifier("Continue with Google")
    }

    /// Run Google's OAuth, then exchange the ID token with the backend.
    private func signInWithGoogle() {
        googleMessage = nil
        Task {
            do {
                let token = try await googleAuth.signIn()
                await backend.signInWithGoogle(idToken: token, name: "")
            } catch GoogleAuthError.cancelled {
                // user backed out — no message
            } catch {
                googleMessage = (error as? LocalizedError)?.errorDescription ?? "Google sign-in failed."
            }
        }
    }

    /// Extract Apple's identity token and exchange it with the backend.
    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        guard case let .success(auth) = result,
              let cred = auth.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else { return }
        let fullName = [cred.fullName?.givenName, cred.fullName?.familyName]
            .compactMap { $0 }.joined(separator: " ")
        Task {
            await backend.signInWithApple(identityToken: token, name: fullName)
        }
    }

    private func field(_ label: String, text: Binding<String>,
                       secure: Bool = false, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).appFont(11.5).foregroundStyle(Theme.Palette.muted)
            Group {
                if secure {
                    SecureField(label, text: text)
                        .textContentType(Self.underTest ? .oneTimeCode : .password)
                        .accessibilityIdentifier(label)
                } else {
                    TextField(label, text: text)
                        .keyboardType(keyboard)
                        .textContentType(keyboard == .emailAddress ? .emailAddress : nil)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier(label)
                }
            }
            .foregroundStyle(Theme.Palette.text)
            .padding(.horizontal, 14).frame(height: 48)
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.Palette.line))
        }
    }
}
