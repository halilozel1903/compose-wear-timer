package io.github.halilozel1903.weartimer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ControllersTest {
    private val plan = WorkoutPlan.intervals("Test", workMillis = 20_000, restMillis = 10_000, rounds = 2, warmUpMillis = 5_000)
    private val clock = ManualClock(1_000_000)

    @Test
    fun workoutRunsThroughItsPhases() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(10_000)
        val snapshot = controller.snapshot()
        assertEquals(PhaseKind.Work, snapshot.phase.kind)
        assertEquals(1, snapshot.round)
        assertEquals(15_000L, snapshot.phaseRemainingMillis)
    }

    @Test
    fun pollReportsAlertsOnceAndFinishes() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(5_000)
        assertIs<TimerAlert.PhaseChanged>(controller.poll().last())
        assertTrue(controller.poll().isEmpty())
        clock.advanceBy(plan.totalMillis)
        val alerts = controller.poll()
        assertIs<TimerAlert.Finished>(alerts.last())
        assertEquals(TimerStatus.Finished, controller.status)
    }

    @Test
    fun pausedWorkoutDoesNotMove() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(1_000)
        controller.pause()
        clock.advanceBy(60_000)
        assertEquals(1_000L, controller.snapshot().elapsedMillis)
        assertTrue(controller.poll().isEmpty())
    }

    @Test
    fun skipReportsThePhaseChangeButNotTheSkippedTicks() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(1_000)
        controller.skipPhase()
        val alerts = controller.poll()
        val change = assertIs<TimerAlert.PhaseChanged>(alerts.single())
        assertEquals(PhaseKind.Work, change.to.kind)
        assertEquals(5_000L, controller.snapshot().elapsedMillis)
        assertTrue(controller.isRunning)
    }

    @Test
    fun skippingTheLastPhaseFinishes() {
        val controller = WorkoutController(plan, clock)
        controller.seekTo(plan.totalMillis - 5_000)
        controller.skipPhase()
        assertEquals(TimerStatus.Finished, controller.status)
        assertIs<TimerAlert.Finished>(controller.poll().single())
    }

    @Test
    fun settleFinishesWithoutConsumingAlerts() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(plan.totalMillis + 1_000)
        controller.settle()
        assertEquals(TimerStatus.Finished, controller.status)
        assertIs<TimerAlert.Finished>(controller.poll().last())
    }

    @Test
    fun alertsDueBeforeAPauseAreKept() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(5_500)
        controller.pause()
        assertIs<TimerAlert.PhaseChanged>(controller.poll().last())
    }

    @Test
    fun previousPhaseRestartsOrGoesBack() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(12_000)
        assertEquals(4, controller.poll().size)
        controller.previousPhase()
        assertEquals(5_000L, controller.snapshot().elapsedMillis)
        controller.previousPhase()
        assertEquals(0L, controller.snapshot().elapsedMillis)
        assertTrue(controller.poll().isEmpty())
    }

    @Test
    fun resetGoesBackToIdle() {
        val controller = WorkoutController(plan, clock)
        controller.start()
        clock.advanceBy(30_000)
        controller.reset()
        assertEquals(TimerStatus.Idle, controller.status)
        assertEquals(0L, controller.snapshot().elapsedMillis)
        assertTrue(controller.poll().isEmpty())
    }

    @Test
    fun restoredWorkoutDoesNotReplayOldAlerts() {
        val workout = IntervalWorkout.at(plan, elapsedMillis = 40_000, status = TimerStatus.Running, nowMillis = clock.nowMillis())
        val controller = WorkoutController(workout, clock)
        assertTrue(controller.poll().isEmpty())
        assertEquals(2, controller.snapshot().round)
    }

    @Test
    fun intervalWorkoutAtBuildsEachStatus() {
        val now = clock.nowMillis()
        assertEquals(TimerStatus.Finished, IntervalWorkout.at(plan, plan.totalMillis, TimerStatus.Running, now).status)
        assertEquals(TimerStatus.Paused, IntervalWorkout.at(plan, 10_000, TimerStatus.Paused, now).status)
        assertEquals(TimerStatus.Idle, IntervalWorkout.at(plan, 0, TimerStatus.Idle, now).status)
    }

    @Test
    fun timerControllerTicksAndFinishes() {
        val controller = TimerController(10_000, clock)
        controller.start()
        clock.advanceBy(8_500)
        assertEquals(2, controller.poll().size)
        clock.advanceBy(2_000)
        val last = controller.poll()
        assertEquals(2, last.size)
        val finished = assertIs<TimerAlert.Finished>(last.last())
        assertEquals("Timer", finished.plan.name)
        assertEquals(TimerStatus.Finished, controller.status)
        assertEquals(0L, controller.remainingMillis)
    }

    @Test
    fun timerControllerAddTimeAndDuration() {
        val controller = TimerController(10_000, clock)
        controller.start()
        clock.advanceBy(10_000)
        controller.poll()
        controller.addTime(60_000)
        assertEquals(TimerStatus.Running, controller.status)
        assertEquals(60_000L, controller.remainingMillis)
        controller.pause()
        controller.setDuration(30_000)
        assertEquals(20_000L, controller.remainingMillis)
        assertTrue(controller.poll().isEmpty())
    }
}
