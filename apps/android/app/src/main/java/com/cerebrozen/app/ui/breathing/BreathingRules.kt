package com.cerebrozen.app.ui.breathing

enum class BreathPhaseType { Inhale, Hold, Exhale }

data class BreathPhase(val type: BreathPhaseType, val seconds: Int) {
    init { require(seconds > 0) }
}

enum class BreathPattern(
    val displayName: String,
    val description: String,
    val phases: List<BreathPhase>,
    val rounds: Int,
) {
    Box(
        "Box Breathing", "Equal counts to steady attention",
        listOf(
            BreathPhase(BreathPhaseType.Inhale, 4),
            BreathPhase(BreathPhaseType.Hold, 4),
            BreathPhase(BreathPhaseType.Exhale, 4),
            BreathPhase(BreathPhaseType.Hold, 4),
        ), 4,
    ),
    // NOT 4-7-8. That ratio has been rejected here three times on record (the
    // web wind-down 2026-07-28, the ritual builder, and Android's own reset
    // correction): it is a popularised pattern without direct evidence, and
    // every other exercise in this app carries a citation. The one part of slow
    // breathing with real vagal-tone evidence is an exhale longer than the
    // inhale — which is exactly the cross-client Reset preset (iOS
    // `BreathingPacer.Preset.reset`, `breathePhases`, web `SLOW_EXHALE`).
    // Twelve rounds of 4 in / 6 out is 120 seconds: the "two-minute reset"
    // five surfaces promise, measured rather than implied.
    Reset(
        "Two-minute reset", "In for four, out for six",
        listOf(
            BreathPhase(BreathPhaseType.Inhale, 4),
            BreathPhase(BreathPhaseType.Exhale, 6),
        ), 12,
    ),
    Coherent(
        "Coherent", "A smooth, balanced breathing rhythm",
        listOf(
            BreathPhase(BreathPhaseType.Inhale, 5),
            BreathPhase(BreathPhaseType.Exhale, 5),
        ), 6,
    ),
    Triangle(
        "Triangle", "Three even sides: inhale, hold, exhale",
        listOf(
            BreathPhase(BreathPhaseType.Inhale, 3),
            BreathPhase(BreathPhaseType.Hold, 3),
            BreathPhase(BreathPhaseType.Exhale, 3),
        ), 5,
    );

    val secondsPerRound: Int get() = phases.sumOf(BreathPhase::seconds)
    val plannedDurationSeconds: Int get() = secondsPerRound * rounds
}

data class BreathingPosition(
    val pattern: BreathPattern,
    val phaseIndex: Int = 0,
    val roundIndex: Int = 0,
) {
    val phase: BreathPhase get() = pattern.phases[phaseIndex]
    val displayRound: Int get() = roundIndex + 1
}

sealed interface AdvanceResult {
    data class Active(val position: BreathingPosition) : AdvanceResult
    data object Complete : AdvanceResult
}

/** Pure breathing state machine. Timing and Android lifecycle concerns live outside it. */
class BreathingStateMachine {
    fun start(pattern: BreathPattern): BreathingPosition = BreathingPosition(pattern)

    fun advance(current: BreathingPosition): AdvanceResult {
        val nextPhase = current.phaseIndex + 1
        if (nextPhase < current.pattern.phases.size) {
            return AdvanceResult.Active(current.copy(phaseIndex = nextPhase))
        }
        val nextRound = current.roundIndex + 1
        if (nextRound >= current.pattern.rounds) return AdvanceResult.Complete
        return AdvanceResult.Active(current.copy(phaseIndex = 0, roundIndex = nextRound))
    }
}
