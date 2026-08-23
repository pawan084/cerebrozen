import XCTest
@testable import CereBro

/// The refusals the server sends, told apart by CODE rather than by status.
///
/// The same contract web (`tests/app/refusalCodes.test.ts`) and Android
/// (`RefusalCodesTest.kt`) pin. Three different 429s now mean three different
/// things — the free-tier cap, a daily abuse ceiling, and slowapi's "slow
/// down" — and one of the wrong answers is manipulative: offering an upgrade
/// for a ceiling that is identical on every tier is selling a fix that is not
/// for sale.
///
/// The 403 tests are the ones that matter most here. iOS collapsed EVERY 403
/// into `.unauthorized` — "Your session expired. Please sign in again." — so
/// the verification gate would have told people to sign out of an account that
/// was working perfectly well.
final class RefusalCodesTest: XCTestCase {

    private func body(_ json: String) -> [String: Any]? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    // MARK: - Parsed by code, never by status

    func testADailyCeilingIsRecognisedAndCarriesItsFeature() {
        let parsed = APIClient.dailyCeiling(from: body("""
        {"detail":{"code":"daily_ceiling","feature":"voice_tts",
        "message":"You've hit the daily limit for this.","limit":2000,
        "resets_at":"2026-08-24T00:00:00+00:00"}}
        """))
        XCTAssertEqual(parsed?.feature, "voice_tts")
        XCTAssertEqual(parsed?.limit, 2000)
        XCTAssertNotNil(parsed?.resetsAt)
    }

    func testTheFreeCapAndTheDailyCeilingDoNotParseAsEachOther() {
        // Same status, opposite remedy. A screen catching the wrong one would
        // offer to sell a fix that does not exist.
        let ceiling = """
        {"detail":{"code":"daily_ceiling","feature":"oracle_turn","limit":500}}
        """
        let cap = """
        {"detail":{"code":"free_daily_limit","limit":50,"used":50}}
        """
        XCTAssertNil(APIClient.freeLimit(from: body(ceiling)))
        XCTAssertNil(APIClient.dailyCeiling(from: body(cap)))
        XCTAssertNotNil(APIClient.dailyCeiling(from: body(ceiling)))
        XCTAssertNotNil(APIClient.freeLimit(from: body(cap)))
    }

    func testAnOrdinaryThrottleIsNeitherOfThem() {
        // slowapi's key is `error`, with no `detail`, and it means "slow down".
        let throttle = body("""
        {"error":"Rate limit exceeded: 10 per 1 minute"}
        """)
        XCTAssertNil(APIClient.freeLimit(from: throttle))
        XCTAssertNil(APIClient.dailyCeiling(from: throttle))
    }

    // MARK: - The 403 that is not a dead session

    func testTheVerificationGateIsRecognisedAndNamesItsFeature() {
        let parsed = APIClient.verificationRequired(from: body("""
        {"detail":{"code":"email_unverified","feature":"voice",
        "message":"Confirm your email address to use this."}}
        """))
        XCTAssertEqual(parsed?.feature, "voice")
        XCTAssertEqual(parsed?.message, "Confirm your email address to use this.")
    }

    func testAPlain403IsNotMistakenForAVerificationWall() {
        // A consent-gated 403 answers with a STRING detail and must not offer
        // to resend a verification email.
        let consent = body("""
        {"detail":"AI memory is switched off in your privacy settings."}
        """)
        XCTAssertNil(APIClient.verificationRequired(from: consent))
    }

    func testTheVerificationMessageIsNotTheSessionExpiredOne() {
        // The whole point of the case existing. `.unauthorized` says "Your
        // session expired. Please sign in again." — advice that would have sent
        // somebody to sign out of a perfectly good account.
        let info = VerificationRequiredInfo(
            message: "Confirm your email address to use this.", feature: "plans")
        let error = APIError.verificationRequired(info)
        XCTAssertEqual(error.errorDescription, info.message)
        XCTAssertNotEqual(error.errorDescription, APIError.unauthorized.errorDescription)
    }

    // MARK: - Copy that has to be right in every timezone

    func testTheResetIsRenderedLocallyRatherThanCalledMidnight() {
        // The window is UTC, so "midnight" is wrong for most of the world — in
        // India these clear at 05:30 local.
        let info = DailyCeilingInfo(
            message: "m", feature: "voice_tts", limit: 2000,
            resetsAt: Date(timeIntervalSince1970: 1_787_500_800))
        XCTAssertFalse(info.resetText.contains("midnight"))
        XCTAssertTrue(info.resetText.hasPrefix("resets at "))
    }

    func testWithNoInstantItNamesUTCRatherThanGuessing() {
        let info = DailyCeilingInfo(
            message: "m", feature: "voice_tts", limit: 2000, resetsAt: nil)
        XCTAssertEqual(info.resetText, "resets at midnight UTC")
    }
}
