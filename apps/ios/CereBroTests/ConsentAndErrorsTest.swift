import XCTest
@testable import CereBro

/// Cross-client contract pins from the 2026-08-03 iOS review — the same rules
/// the server (`test_dpdp.py`), Android and web already pin:
///   1. A fresh install has granted NOTHING (`Consent()` all-false). The old
///      four-true defaults made `Consent()` itself a phantom grant — a
///      fresh-device sign-in pushed it over the account's real choices.
///   2. A key missing from a STORED consent blob decodes as false ("never
///      asked" is not "granted") — except journal, which inherits the old
///      AI-memory umbrella the user really did decide.
///   3. Pydantic's array-shaped 422 `detail` yields the server's own sentence,
///      not "Server error (422)" (Android Session.raw: same fix, e5697f83).
final class ConsentAndErrorsTest: XCTestCase {

    // MARK: - Consent defaults

    func testAFreshInstallHasGrantedNothing() {
        let fresh = Consent()
        XCTAssertFalse(fresh.moodHistory)
        XCTAssertFalse(fresh.aiMemory)
        XCTAssertFalse(fresh.voiceStorage)
        XCTAssertFalse(fresh.modelTraining)
        XCTAssertFalse(fresh.journalMemory)
        XCTAssertFalse(fresh.sleepHistory)
    }

    func testMissingKeysInAStoredBlobDecodeAsNotGranted() throws {
        // A pre-itemization blob that only ever knew the AI-memory umbrella.
        let old = #"{"aiMemory": true}"#.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(Consent.self, from: old)
        XCTAssertTrue(decoded.aiMemory, "an explicit stored choice survives")
        XCTAssertTrue(decoded.journalMemory, "journal inherits the umbrella the user chose")
        XCTAssertFalse(decoded.moodHistory, "a category never presented cannot decode as granted")
        XCTAssertFalse(decoded.sleepHistory)
        XCTAssertFalse(decoded.voiceStorage)
        XCTAssertFalse(decoded.modelTraining)
    }

    func testExplicitStoredChoicesRoundTrip() throws {
        var chosen = Consent()
        chosen.moodHistory = true
        chosen.sleepHistory = true
        let decoded = try JSONDecoder().decode(
            Consent.self, from: JSONEncoder().encode(chosen))
        XCTAssertEqual(decoded, chosen)
    }

    // MARK: - Pydantic 422 detail parsing

    func testPydantic422SurfacesTheServersOwnSentence() {
        let body: [String: Any] = ["detail": [[
            "loc": ["body", "value"],
            "msg": "Value error, That doesn't look like an email address.",
            "type": "value_error",
        ]]]
        XCTAssertEqual(APIClient.validationMessage(from: body),
                       "That doesn't look like an email address.")
    }

    func testNonValidationShapesYieldNil() {
        XCTAssertNil(APIClient.validationMessage(from: ["detail": "plain string"]))
        XCTAssertNil(APIClient.validationMessage(from: ["detail": ["code": "free_daily_limit"]]))
        XCTAssertNil(APIClient.validationMessage(from: nil))
        XCTAssertNil(APIClient.validationMessage(from: ["detail": [[String: Any]]()]))
    }
}
