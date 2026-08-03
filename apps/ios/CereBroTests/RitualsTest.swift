import XCTest
@testable import CereBro

/// Pure-seam gate for the three guided routines (`Features/Tools/Rituals.swift`)
/// — the same helpers Android pins in `ScreenLogicTest`, so the two clients
/// can't drift on the decisions that aren't about rendering.
///
/// ONE-TIME SETUP (macOS): see the header of `ContrastTest.swift` — the project
/// has no unit-test target yet; adding a `CereBroTests` Unit Testing Bundle
/// picks this file up automatically (synchronized file groups).
final class RitualsTest: XCTestCase {

    // MARK: - Sequence advance

    func testNextPromptIndexAdvancesThenEndsOnTheLastPrompt() {
        XCTAssertEqual(nextPromptIndex(0, of: 6), 1)
        XCTAssertEqual(nextPromptIndex(4, of: 6), 5)
        XCTAssertNil(nextPromptIndex(5, of: 6), "the last prompt hands over rather than wrapping")
        // Defensive: an index past the end must still end, not run backwards.
        XCTAssertNil(nextPromptIndex(9, of: 6))
        XCTAssertNil(nextPromptIndex(0, of: 0), "an empty reel is already finished")
    }

    // MARK: - Ritual blocks

    func testRitualBlocksHaveUniqueIdsAndRealDurations() {
        XCTAssertEqual(ritualBlocks.count, Set(ritualBlocks.map(\.id)).count,
                       "ids are the persisted contract — a duplicate would collide in the store")
        XCTAssertTrue(ritualBlocks.allSatisfy { $0.minutes >= 1 }, "every block claims at least a minute")
    }

    /// The three blocks the sibling build shipped that this app deliberately
    /// does NOT: 4-7-8 breathing (a popularised ratio with no direct evidence),
    /// Disidentification (Psychosynthesis), and affirmation reading — generic
    /// positive self-statements lower mood in people with low self-esteem
    /// (Wood, Perunovic & Lee, Psychological Science, 2009), which is the
    /// population a wellness app selects for. Pinned so none of them can be
    /// reintroduced without someone reading this comment first.
    func testRejectedBlocksStayRejected() {
        let ids = Set(ritualBlocks.map(\.id))
        for rejected in ["478", "478_breathing", "disidentification", "affirmation"] {
            XCTAssertFalse(ids.contains(rejected), "\(rejected) was rejected on evidence grounds")
        }
    }

    func testSanitizeRitualDropsUnknownIdsAndDuplicatesKeepingOrder() {
        XCTAssertEqual(sanitizeRitual(["good", "affirmation", "settle", "good", "478"]),
                       ["good", "settle"])
        XCTAssertEqual(sanitizeRitual(["nope"]), [])
        XCTAssertEqual(sanitizeRitual([]), [])
    }

    func testRitualMinutesSumsOnlyBlocksThatExist() {
        let expected = (ritualBlocks.first { $0.id == "good" }?.minutes ?? 0)
            + (ritualBlocks.first { $0.id == "settle" }?.minutes ?? 0)
        XCTAssertEqual(ritualMinutes(["good", "settle"]), expected)
        XCTAssertEqual(ritualMinutes([]), 0)
        XCTAssertEqual(ritualMinutes(["affirmation"]), 0,
                       "an unknown id contributes nothing rather than trapping")
    }

    // MARK: - Store

    func testRitualStoreRoundTripsAndSanitizesOnTheWayOut() {
        let defaults = UserDefaults.standard
        let blocksBackup = defaults.stringArray(forKey: RitualStore.blocksKey)
        let cueBackup = defaults.string(forKey: RitualStore.cueKey)
        defer {
            defaults.set(blocksBackup, forKey: RitualStore.blocksKey)
            defaults.set(cueBackup, forKey: RitualStore.cueKey)
        }

        RitualStore.save(["good", "affirmation", "good", "settle"], cue: "  close my laptop  ")
        XCTAssertEqual(RitualStore.blocks(), ["good", "settle"])
        XCTAssertEqual(RitualStore.cue(), "close my laptop")

        defaults.removeObject(forKey: RitualStore.blocksKey)
        defaults.removeObject(forKey: RitualStore.cueKey)
        XCTAssertEqual(RitualStore.blocks(), [], "a fresh install has an empty ritual, not a phantom block")
        XCTAssertEqual(RitualStore.cue(), "")
    }

    // MARK: - Cross-client pacing

    /// The wind-down's settle step reuses `.reset` rather than defining a
    /// fourth rhythm. The asymmetry IS the exercise — a longer exhale than
    /// inhale is the part of slow breathing with real vagal-tone evidence —
    /// and Android pins the same numbers (`RESET_EXHALE_EXTRA`).
    func testResetPresetExhalesLongerThanItInhales() {
        let phases = BreathingPacer.Preset.reset.phases
        XCTAssertEqual(phases.count, 2, "reset has no holds")
        XCTAssertEqual(phases[0].seconds, 4)
        XCTAssertEqual(phases[1].seconds, 6)
        XCTAssertTrue(phases[1].seconds > phases[0].seconds)
    }
}
