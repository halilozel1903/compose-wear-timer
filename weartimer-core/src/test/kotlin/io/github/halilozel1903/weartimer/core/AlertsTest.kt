package io.github.halilozel1903.weartimer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertsTest {
    // 0:20 work, 0:10 rest, 0:20 work.
    private val plan = WorkoutPlan.intervals("Test", workMillis = 20_000, restMillis = 10_000, rounds = 2)

    @Test
    fun ticksComeInTheLastThreeSecondsOfAPhase() {
        val alerts = TimerAlerts.between(plan, 16_000, 19_500)
        assertEquals(listOf(3, 2, 1), alerts.map { (it as TimerAlert.CountdownTick).secondsLeft })
        assertEquals(listOf(17_000L, 18_000L, 19_000L), alerts.map { it.atMillis })
    }

    @Test
    fun phaseEndGivesAPhaseChange() {
        val alerts = TimerAlerts.between(plan, 19_500, 20_000)
        val change = assertIs<TimerAlert.PhaseChanged>(alerts.single())
        assertEquals(PhaseKind.Work, change.from.kind)
        assertEquals(PhaseKind.Rest, change.to.kind)
    }

    @Test
    fun rangeIsExclusiveAtTheStartAndInclusiveAtTheEnd() {
        assertTrue(TimerAlerts.between(plan, 20_000, 20_500).isEmpty())
        assertTrue(TimerAlerts.between(plan, 5_000, 5_000).isEmpty())
        assertTrue(TimerAlerts.between(plan, 6_000, 5_000).isEmpty())
    }

    @Test
    fun lastPhaseEndsWithFinished() {
        val alerts = TimerAlerts.between(plan, 45_000, 60_000)
        assertEquals(4, alerts.size)
        assertIs<TimerAlert.Finished>(alerts.last())
    }

    @Test
    fun aWholeWorkoutInOneCallIsInTimeOrder() {
        val alerts = TimerAlerts.between(plan, -1, plan.totalMillis)
        // 3 ticks + change, 3 ticks + change, 3 ticks + finished.
        assertEquals(12, alerts.size)
        assertEquals(alerts.sortedBy { it.atMillis }, alerts)
    }

    @Test
    fun shortPhasesOnlyGetTicksInsideThem() {
        val short = WorkoutPlan("Short", listOf(WorkoutPhase(PhaseKind.Work, 2_000), WorkoutPhase(PhaseKind.Rest, 3_000)))
        val ticks = TimerAlerts.between(short, 0, 2_000).filterIsInstance<TimerAlert.CountdownTick>()
        assertEquals(listOf(1), ticks.map { it.secondsLeft })
        assertTrue(TimerAlerts.between(plan, 0, 20_000, countdownSeconds = 0).single() is TimerAlert.PhaseChanged)
    }

    @Test
    fun mostImportantPrefersFinishedThenTheLatestChange() {
        val all = TimerAlerts.between(plan, 0, plan.totalMillis)
        assertIs<TimerAlert.Finished>(TimerAlerts.mostImportant(all))
        val firstHalf = TimerAlerts.between(plan, 0, 31_000)
        assertEquals(30_000L, TimerAlerts.mostImportant(firstHalf)?.atMillis)
        assertNull(TimerAlerts.mostImportant(emptyList()))
    }

    @Test
    fun trackerReportsEachAlertOnce() {
        val tracker = AlertTracker()
        assertEquals(1, tracker.advance(plan, 17_500).size)
        assertEquals(0, tracker.advance(plan, 17_900).size)
        assertEquals(3, tracker.advance(plan, 20_000).size)
        tracker.jumpTo(45_000)
        assertEquals(4, tracker.advance(plan, 60_000).size)
        assertEquals(0, tracker.advance(plan, 10_000).size)
        assertEquals(10_000L, tracker.reportedUpToMillis)
    }

    @Test
    fun hapticPatternsMatchTheAlert() {
        val all = TimerAlerts.between(plan, 0, plan.totalMillis)
        assertEquals(HapticPatterns.Tick, HapticPatterns.forAlert(all.first()))
        val toRest = all.filterIsInstance<TimerAlert.PhaseChanged>().first()
        assertEquals(HapticPatterns.RestStarts, HapticPatterns.forAlert(toRest))
        val toWork = all.filterIsInstance<TimerAlert.PhaseChanged>().last()
        assertEquals(HapticPatterns.WorkStarts, HapticPatterns.forAlert(toWork))
        assertEquals(HapticPatterns.Finished, HapticPatterns.forAlert(all.last()))
    }

    @Test
    fun pulsesBuildAnAndroidWaveform() {
        val pattern = HapticPattern.pulses(100L, 200L, gapMillis = 50L, amplitude = 180)
        assertEquals(listOf(0L, 100L, 50L, 200L), pattern.timings)
        assertEquals(listOf(0, 180, 0, 180), pattern.amplitudes)
        assertEquals(350L, pattern.durationMillis)
    }
}
