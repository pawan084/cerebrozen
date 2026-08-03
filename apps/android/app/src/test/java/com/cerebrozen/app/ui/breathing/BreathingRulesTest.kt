package com.cerebrozen.app.ui.breathing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BreathingRulesTest {
    private val machine = BreathingStateMachine()

    @Test
    fun everyPatternHasTheRequiredPhasesRoundsAndDuration() {
        assertPattern(BreathPattern.Box, listOf(Inhale(4), Hold(4), Exhale(4), Hold(4)), 4, 64)
        // Reset, not 4-7-8: that ratio is rejected here on evidence grounds (three
        // times on record). 12 rounds of in-4/out-6 is exactly the 120 seconds the
        // "two-minute reset" promises — the duration is measured, not implied.
        assertPattern(BreathPattern.Reset, listOf(Inhale(4), Exhale(6)), 12, 120)
        assertPattern(BreathPattern.Coherent, listOf(Inhale(5), Exhale(5)), 6, 60)
        assertPattern(BreathPattern.Triangle, listOf(Inhale(3), Hold(3), Exhale(3)), 5, 45)
    }

    @Test
    fun phaseTransitionMovesThroughOneRoundInOrder() {
        var position = machine.start(BreathPattern.Box)
        assertEquals(BreathPhaseType.Inhale, position.phase.type)
        position = active(machine.advance(position))
        assertEquals(BreathPhaseType.Hold, position.phase.type)
        position = active(machine.advance(position))
        assertEquals(BreathPhaseType.Exhale, position.phase.type)
        position = active(machine.advance(position))
        assertEquals(BreathPhaseType.Hold, position.phase.type)
    }

    @Test
    fun finishingLastPhaseAdvancesRoundAndEventuallyCompletes() {
        var position = machine.start(BreathPattern.Coherent)
        repeat(BreathPattern.Coherent.phases.size) { position = active(machine.advance(position)) }
        assertEquals(1, position.roundIndex)
        assertEquals(0, position.phaseIndex)

        position = machine.start(BreathPattern.Triangle)
        val transitionsBeforeCompletion = BreathPattern.Triangle.phases.size * BreathPattern.Triangle.rounds - 1
        repeat(transitionsBeforeCompletion) { position = active(machine.advance(position)) }
        assertTrue(machine.advance(position) is AdvanceResult.Complete)
    }

    private fun assertPattern(
        pattern: BreathPattern,
        phases: List<BreathPhase>,
        rounds: Int,
        duration: Int,
    ) {
        assertEquals(phases, pattern.phases)
        assertEquals(rounds, pattern.rounds)
        assertEquals(duration, pattern.plannedDurationSeconds)
    }

    private fun active(result: AdvanceResult): BreathingPosition = (result as AdvanceResult.Active).position
    private fun Inhale(seconds: Int) = BreathPhase(BreathPhaseType.Inhale, seconds)
    private fun Hold(seconds: Int) = BreathPhase(BreathPhaseType.Hold, seconds)
    private fun Exhale(seconds: Int) = BreathPhase(BreathPhaseType.Exhale, seconds)
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BreathLoopsViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @Before fun setUpDispatcher() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After fun resetDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeClock(var value: Long = 1_000L) : ElapsedRealtimeClock {
        override fun nowMillis(): Long = value
    }

    private class FakeHistory : BreathingHistoryStore {
        private val records = MutableStateFlow<List<BreathingSessionRecord>>(emptyList())
        override val history: Flow<List<BreathingSessionRecord>> = records
        override suspend fun save(record: BreathingSessionRecord) { records.value = records.value + record }
        override suspend fun clear() { records.value = emptyList() }
    }

    @Test
    fun deadlineReconcileTransitionsPhaseAndCatchesUpAfterBackgroundGap() = runTest(dispatcher) {
        val clock = FakeClock()
        val vm = viewModel(clock)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.start(BreathPattern.Box)
        runCurrent()
        assertEquals(BreathPhaseType.Inhale, vm.uiState.value.core.position?.phase?.type)

        clock.value += 4_000
        vm.reconcile()
        runCurrent()
        assertEquals(BreathPhaseType.Hold, vm.uiState.value.core.position?.phase?.type)

        clock.value += 8_000
        vm.reconcile()
        runCurrent()
        assertEquals(BreathPhaseType.Hold, vm.uiState.value.core.position?.phase?.type)
        assertEquals(3, vm.uiState.value.core.position?.phaseIndex)
        vm.stop()
        collection.cancel()
    }

    @Test
    fun stopResetsActivePositionAndDoesNotSaveHistory() = runTest(dispatcher) {
        val clock = FakeClock()
        val history = FakeHistory()
        val vm = viewModel(clock, history)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.start(BreathPattern.Reset)
        clock.value += 4_000
        vm.reconcile()
        vm.stop()
        runCurrent()

        assertEquals(BreathScreenMode.Picker, vm.uiState.value.core.mode)
        assertEquals(null, vm.uiState.value.core.position)
        assertTrue(vm.uiState.value.history.isEmpty())
        collection.cancel()
    }

    @Test
    fun pauseFreezesTheDeadlineAndResumeContinuesMidPhase() = runTest(dispatcher) {
        val clock = FakeClock()
        val vm = viewModel(clock)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.start(BreathPattern.Box)
        runCurrent()
        clock.value += 2_000
        vm.reconcile()
        runCurrent()
        assertEquals(2, vm.uiState.value.core.remainingSeconds)

        vm.pause()
        runCurrent()
        assertTrue(vm.uiState.value.core.paused)
        // A long background gap while paused must not advance anything.
        clock.value += 60_000
        vm.reconcile()
        runCurrent()
        assertEquals(BreathPhaseType.Inhale, vm.uiState.value.core.position?.phase?.type)
        assertEquals(2, vm.uiState.value.core.remainingSeconds)

        vm.resume()
        runCurrent()
        clock.value += 2_000
        vm.reconcile()
        runCurrent()
        assertEquals(BreathPhaseType.Hold, vm.uiState.value.core.position?.phase?.type)
        vm.stop()
        collection.cancel()
    }

    @Test
    fun stoppingMidSessionBanksCompletedRoundsAsPartialCredit() = runTest(dispatcher) {
        val clock = FakeClock()
        val history = FakeHistory()
        val vm = viewModel(clock, history)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.start(BreathPattern.Coherent)
        runCurrent()
        // Two full 10-second rounds plus five seconds into the third.
        clock.value += 25_000
        vm.reconcile()
        runCurrent()
        vm.stop()
        runCurrent()

        assertEquals(BreathScreenMode.Picker, vm.uiState.value.core.mode)
        val record = vm.uiState.value.history.single()
        assertEquals(BreathPattern.Coherent, record.pattern)
        assertEquals(2, record.rounds)
        assertEquals(20, record.durationSeconds)
        collection.cancel()
    }

    @Test
    fun completingAllRoundsShowsCompletionAndSavesLocally() = runTest(dispatcher) {
        val clock = FakeClock()
        val history = FakeHistory()
        val vm = viewModel(clock, history)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.start(BreathPattern.Triangle)
        runCurrent()
        clock.value += BreathPattern.Triangle.plannedDurationSeconds * 1_000L
        vm.reconcile()
        runCurrent()

        assertEquals(BreathScreenMode.Completed, vm.uiState.value.core.mode)
        assertEquals(1, vm.uiState.value.history.size)
        assertEquals(BreathPattern.Triangle, vm.uiState.value.history.single().pattern)
        assertEquals(5, vm.uiState.value.history.single().rounds)
        collection.cancel()
    }

    private fun viewModel(clock: FakeClock, history: FakeHistory = FakeHistory()): BreathLoopsViewModel =
        BreathLoopsViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            historyStore = history,
            clock = clock,
            wallClockMillis = { 123_456L },
        )
}
