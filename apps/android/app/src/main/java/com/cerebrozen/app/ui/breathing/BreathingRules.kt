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
    FourSevenEight(
        "4-7-8", "A longer exhale for settling",
        listOf(
            BreathPhase(BreathPhaseType.Inhale, 4),
            BreathPhase(BreathPhaseType.Hold, 7),
            BreathPhase(BreathPhaseType.Exhale, 8),
        ), 4,
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
