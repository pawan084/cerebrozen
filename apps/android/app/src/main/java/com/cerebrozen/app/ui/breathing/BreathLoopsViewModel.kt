package com.cerebrozen.app.ui.breathing

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

fun interface ElapsedRealtimeClock { fun nowMillis(): Long }

enum class BreathScreenMode { Picker, Active, Completed }

data class BreathLoopsCoreState(
    val mode: BreathScreenMode = BreathScreenMode.Picker,
    val selectedPattern: BreathPattern = BreathPattern.Box,
    val position: BreathingPosition? = null,
    val remainingSeconds: Int = BreathPattern.Box.phases.first().seconds,
    val voiceEnabled: Boolean = true,
    val paused: Boolean = false,
)

data class BreathLoopsUiState(
    val core: BreathLoopsCoreState = BreathLoopsCoreState(),
    val history: List<BreathingSessionRecord> = emptyList(),
)

class BreathLoopsViewModel(
    application: Application,
    private val historyStore: BreathingHistoryStore = DataStoreBreathingHistory(application),
    private val clock: ElapsedRealtimeClock = ElapsedRealtimeClock(SystemClock::elapsedRealtime),
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) : AndroidViewModel(application) {
    private val machine = BreathingStateMachine()
    private val core = MutableStateFlow(BreathLoopsCoreState())
    private var phaseDeadlineMillis = 0L
    private var pausedRemainingMillis = 0L
    private var ticker: Job? = null

    val uiState: StateFlow<BreathLoopsUiState> = combine(core, historyStore.history) { state, history ->
        BreathLoopsUiState(state, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreathLoopsUiState())

    fun select(pattern: BreathPattern) {
        if (core.value.mode != BreathScreenMode.Active) {
            core.value = core.value.copy(
                selectedPattern = pattern,
                position = null,
                remainingSeconds = pattern.phases.first().seconds,
            )
        }
    }

    fun start(pattern: BreathPattern = core.value.selectedPattern) {
        ticker?.cancel()
        val position = machine.start(pattern)
        phaseDeadlineMillis = clock.nowMillis() + position.phase.seconds * 1_000L
        core.value = core.value.copy(
            mode = BreathScreenMode.Active,
            selectedPattern = pattern,
            position = position,
            remainingSeconds = position.phase.seconds,
            paused = false,
        )
        ticker = viewModelScope.launch {
            while (core.value.mode == BreathScreenMode.Active) {
                reconcile()
                delay(TICK_MILLIS)
            }
        }
    }

    /** Reconciles against a monotonic deadline, including multiple phases missed
     * while the app was backgrounded or the process thread was suspended. */
    fun reconcile() {
        var state = core.value
        if (state.mode != BreathScreenMode.Active || state.paused) return
        var position = state.position ?: return
        val now = clock.nowMillis()
        while (now >= phaseDeadlineMillis) {
            when (val next = machine.advance(position)) {
                AdvanceResult.Complete -> {
                    complete(position.pattern)
                    return
                }
                is AdvanceResult.Active -> {
                    position = next.position
                    phaseDeadlineMillis += position.phase.seconds * 1_000L
                }
            }
        }
        val remaining = ceil((phaseDeadlineMillis - now) / 1_000.0).toInt().coerceAtLeast(1)
        state = core.value
        core.value = state.copy(position = position, remainingSeconds = remaining)
    }

    /** Pausing freezes the monotonic deadline; resuming restores it against the
     * current clock, so a session paused for an hour picks up mid-phase exactly
     * where it stopped — including through backgrounding (reconcile no-ops while
     * paused). */
    fun pause() {
        val state = core.value
        if (state.mode != BreathScreenMode.Active || state.paused) return
        pausedRemainingMillis = (phaseDeadlineMillis - clock.nowMillis()).coerceAtLeast(0L)
        core.value = state.copy(paused = true)
    }

    fun resume() {
        val state = core.value
        if (state.mode != BreathScreenMode.Active || !state.paused) return
        phaseDeadlineMillis = clock.nowMillis() + pausedRemainingMillis
        core.value = state.copy(paused = false)
    }

    fun togglePause() {
        if (core.value.paused) resume() else pause()
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        val state = core.value
        val pattern = state.selectedPattern
        val fullRounds = if (state.mode == BreathScreenMode.Active) state.position?.roundIndex ?: 0 else 0
        if (fullRounds >= 1) {
            viewModelScope.launch {
                historyStore.save(
                    BreathingSessionRecord(
                        completedAtEpochMillis = wallClockMillis(),
                        pattern = pattern,
                        durationSeconds = pattern.secondsPerRound * fullRounds,
                        rounds = fullRounds,
                    ),
                )
            }
        }
        core.value = state.copy(
            mode = BreathScreenMode.Picker,
            position = null,
            remainingSeconds = pattern.phases.first().seconds,
            paused = false,
        )
    }

    fun done() {
        val pattern = core.value.selectedPattern
        core.value = core.value.copy(
            mode = BreathScreenMode.Picker,
            position = null,
            remainingSeconds = pattern.phases.first().seconds,
        )
    }

    fun toggleVoice() {
        core.value = core.value.copy(voiceEnabled = !core.value.voiceEnabled)
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    private fun complete(pattern: BreathPattern) {
        ticker?.cancel()
        ticker = null
        core.value = core.value.copy(
            mode = BreathScreenMode.Completed,
            position = null,
            remainingSeconds = 0,
        )
        viewModelScope.launch {
            historyStore.save(
                BreathingSessionRecord(
                    completedAtEpochMillis = wallClockMillis(),
                    pattern = pattern,
                    durationSeconds = pattern.plannedDurationSeconds,
                    rounds = pattern.rounds,
                ),
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TICK_MILLIS = 100L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.AndroidViewModelFactory(application) {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BreathLoopsViewModel(application) as T
                }
            }
    }
}
