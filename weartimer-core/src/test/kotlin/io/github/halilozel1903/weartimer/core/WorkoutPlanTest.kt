package io.github.halilozel1903.weartimer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutPlanTest {
    // 1:00 warm-up, 8 x (0:20 work, 0:10 rest) without a rest after the last round, 1:00 cool-down.
    private val plan = WorkoutPlan.intervals(
        name = "Tabata",
        workMillis = 20_000,
        restMillis = 10_000,
        rounds = 8,
        warmUpMillis = 60_000,
        coolDownMillis = 60_000,
    )

    @Test
    fun intervalsBuildWarmUpRoundsAndCoolDown() {
        assertEquals(1 + 8 + 7 + 1, plan.phases.size)
        assertEquals(PhaseKind.WarmUp, plan.phases.first().kind)
        assertEquals(PhaseKind.CoolDown, plan.phases.last().kind)
        assertEquals(PhaseKind.Work, plan.phases[plan.phases.lastIndex - 1].kind)
        assertEquals(60_000L + 8 * 20_000 + 7 * 10_000 + 60_000, plan.totalMillis)
        assertEquals(8, plan.totalRounds)
        assertEquals(160_000L, plan.workMillis)
    }

    @Test
    fun restAfterLastRoundAndZeroLengthPhases() {
        val withRest = WorkoutPlan.intervals("x", 20_000, 10_000, rounds = 2, restAfterLastRound = true)
        assertEquals(listOf(PhaseKind.Work, PhaseKind.Rest, PhaseKind.Work, PhaseKind.Rest), withRest.phases.map { it.kind })
        val noRest = WorkoutPlan.intervals("x", 20_000, 0, rounds = 3)
        assertEquals(3, noRest.phases.size)
        assertEquals(listOf(0L, 20_000L, 40_000L), noRest.phaseStarts)
    }

    @Test
    fun tabataDefaults() {
        val tabata = WorkoutPlan.tabata()
        assertEquals(15, tabata.phases.size)
        assertEquals(230_000L, tabata.totalMillis)
        assertEquals("Tabata", tabata.name)
    }

    @Test
    fun phaseIndexOwnsItsStartButNotItsEnd() {
        assertEquals(0, plan.phaseIndexAt(0))
        assertEquals(0, plan.phaseIndexAt(59_999))
        assertEquals(1, plan.phaseIndexAt(60_000))
        assertEquals(2, plan.phaseIndexAt(80_000))
        assertEquals(plan.phases.lastIndex, plan.phaseIndexAt(plan.totalMillis))
        assertEquals(plan.phases.lastIndex, plan.phaseIndexAt(Long.MAX_VALUE))
        assertEquals(0, plan.phaseIndexAt(-5))
    }

    @Test
    fun snapshotInTheThirdWorkPhase() {
        val snapshot = plan.snapshotAt(60_000 + 2 * 30_000 + 8_000)
        assertEquals(PhaseKind.Work, snapshot.phase.kind)
        assertEquals(3, snapshot.round)
        assertEquals(8, snapshot.totalRounds)
        assertEquals(12_000L, snapshot.phaseRemainingMillis)
        assertEquals(0.6f, snapshot.phaseRemainingFraction, 0.0001f)
        assertEquals(PhaseKind.Rest, snapshot.nextPhase?.kind)
        assertFalse(snapshot.isFinished)
    }

    @Test
    fun snapshotRoundsDuringWarmUpRestAndCoolDown() {
        assertEquals(0, plan.snapshotAt(1_000).round)
        val rest = plan.snapshotAt(60_000 + 2 * 30_000 + 24_000)
        assertEquals(PhaseKind.Rest, rest.phase.kind)
        assertEquals(3, rest.round)
        assertEquals(6_000L, rest.phaseRemainingMillis)
        assertEquals(8, plan.snapshotAt(plan.totalMillis - 1).round)
    }

    @Test
    fun snapshotAtTheEndIsFinished() {
        val done = plan.snapshotAt(plan.totalMillis + 10_000)
        assertTrue(done.isFinished)
        assertEquals(1f, done.totalProgress)
        assertEquals(0L, done.remainingMillis)
        assertEquals(0L, done.phaseRemainingMillis)
        assertNull(done.nextPhase)
    }

    @Test
    fun countdownIsASinglePhase() {
        val countdown = WorkoutPlan.countdown(90_000, name = "Plank")
        assertEquals(1, countdown.phases.size)
        assertEquals("Plank", countdown.phases.single().label)
        assertEquals(0, countdown.totalRounds)
    }

    @Test
    fun invalidPlansAreRejected() {
        assertFailsWith<IllegalArgumentException> { WorkoutPlan("x", emptyList()) }
        assertFailsWith<IllegalArgumentException> { WorkoutPlan.intervals("x", 20_000, 10_000, rounds = 0) }
        assertFailsWith<IllegalArgumentException> { WorkoutPhase(PhaseKind.Work, 0) }
    }
}
