package io.github.halilozel1903.weartimer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountdownTimerTest {
    private val minute = 60_000L

    @Test
    fun idleTimerHasNothingElapsed() {
        val timer = CountdownTimer(minute)
        assertEquals(TimerStatus.Idle, timer.status)
        assertEquals(0L, timer.elapsedAt(5_000))
        assertEquals(minute, timer.remainingAt(5_000))
        assertEquals(1f, timer.remainingFractionAt(5_000))
    }

    @Test
    fun runningTimerCountsDownWithTheClock() {
        val timer = CountdownTimer(minute).start(nowMillis = 1_000)
        assertEquals(TimerStatus.Running, timer.status)
        assertEquals(45_000L, timer.remainingAt(16_000))
        assertEquals(0.25f, timer.progressAt(16_000))
    }

    @Test
    fun pauseKeepsTheElapsedTimeAndResumeContinues() {
        var timer = CountdownTimer(minute).start(0)
        timer = timer.pause(20_000)
        assertEquals(TimerStatus.Paused, timer.status)
        assertNull(timer.startedAtMillis)
        assertEquals(40_000L, timer.remainingAt(999_999))
        timer = timer.start(100_000)
        assertEquals(30_000L, timer.remainingAt(110_000))
    }

    @Test
    fun toggleSwitchesBetweenRunningAndPaused() {
        var timer = CountdownTimer(minute).toggle(0)
        assertTrue(timer.isRunning)
        timer = timer.toggle(10_000)
        assertEquals(TimerStatus.Paused, timer.status)
        assertEquals(10_000L, timer.elapsedAt(50_000))
    }

    @Test
    fun elapsedNeverPassesTheDuration() {
        val timer = CountdownTimer(minute).start(0)
        assertEquals(minute, timer.elapsedAt(10 * minute))
        assertEquals(0L, timer.remainingAt(10 * minute))
        assertTrue(timer.isFinishedAt(minute))
        assertFalse(timer.isFinishedAt(minute - 1))
    }

    @Test
    fun settleFinishesARunningTimerAtZero() {
        val timer = CountdownTimer(minute).start(0)
        assertEquals(timer, timer.settle(minute - 1))
        val finished = timer.settle(minute)
        assertEquals(TimerStatus.Finished, finished.status)
        assertEquals(minute, finished.accumulatedMillis)
    }

    @Test
    fun pausingAfterZeroFinishes() {
        val timer = CountdownTimer(minute).start(0).pause(2 * minute)
        assertEquals(TimerStatus.Finished, timer.status)
    }

    @Test
    fun startingAFinishedTimerStartsOver() {
        val timer = CountdownTimer(minute).start(0).settle(minute).start(5 * minute)
        assertEquals(TimerStatus.Running, timer.status)
        assertEquals(minute, timer.remainingAt(5 * minute))
    }

    @Test
    fun resetKeepsTheDuration() {
        val timer = CountdownTimer(minute).start(0).pause(10_000).reset()
        assertEquals(CountdownTimer(minute), timer)
    }

    @Test
    fun seekToMovesWithinTheTimer() {
        val running = CountdownTimer(minute).start(0).seekTo(50_000, nowMillis = 5_000)
        assertEquals(TimerStatus.Running, running.status)
        assertEquals(9_000L, running.remainingAt(6_000))
        assertEquals(TimerStatus.Finished, running.seekTo(minute, 6_000).status)
        val idle = CountdownTimer(minute).seekTo(30_000, 0)
        assertEquals(TimerStatus.Paused, idle.status)
        assertEquals(30_000L, idle.elapsedAt(0))
    }

    @Test
    fun withDurationKeepsElapsedTime() {
        val paused = CountdownTimer(minute).start(0).pause(20_000)
        val longer = paused.withDuration(2 * minute, 30_000)
        assertEquals(100_000L, longer.remainingAt(30_000))
        val shorter = paused.withDuration(10_000, 30_000)
        assertEquals(TimerStatus.Finished, shorter.status)
        assertEquals(CountdownTimer(5_000), CountdownTimer(minute).withDuration(5_000, 0))
    }

    @Test
    fun plusExtendsAndRevivesAFinishedTimer() {
        val finished = CountdownTimer(minute).start(0).settle(minute)
        val extended = finished.plus(minute, nowMillis = 70_000)
        assertEquals(TimerStatus.Running, extended.status)
        assertEquals(minute, extended.remainingAt(70_000))
        assertEquals(30_000L, extended.remainingAt(100_000))
    }

    @Test
    fun invalidValuesAreRejected() {
        assertFailsWith<IllegalArgumentException> { CountdownTimer(0) }
        assertFailsWith<IllegalArgumentException> { CountdownTimer(minute, accumulatedMillis = minute + 1) }
        assertFailsWith<IllegalArgumentException> { CountdownTimer(minute, TimerStatus.Running) }
    }

    @Test
    fun manualClockOnlyMovesForward() {
        val clock = ManualClock(100)
        assertEquals(350L, clock.advanceBy(250))
        clock.set(400)
        assertEquals(400L, clock.nowMillis())
        assertFailsWith<IllegalArgumentException> { clock.set(399) }
        assertFailsWith<IllegalArgumentException> { clock.advanceBy(-1) }
    }
}
