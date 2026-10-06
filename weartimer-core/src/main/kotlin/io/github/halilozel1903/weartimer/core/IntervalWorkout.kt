package io.github.halilozel1903.weartimer.core

/**
 * An immutable running workout: a [plan] and a [timer] over the plan's total length. Like
 * [CountdownTimer] it never reads a clock; pass the current time to every call.
 *
 * ```kotlin
 * var workout = IntervalWorkout(WorkoutPlan.tabata())
 * workout = workout.start(now)
 * val snapshot = workout.snapshotAt(now)   // phase, round 3/8, 0:12 left, total progress
 * workout = workout.skipPhase(now)         // straight to the next phase
 * ```
 */
public data class IntervalWorkout(
    val plan: WorkoutPlan,
    val timer: CountdownTimer = CountdownTimer(plan.totalMillis),
) {
    init {
        require(timer.durationMillis == plan.totalMillis) {
            "The timer must last as long as the plan (${timer.durationMillis} != ${plan.totalMillis})"
        }
    }

    public val status: TimerStatus get() = timer.status
    public val isRunning: Boolean get() = timer.isRunning

    public fun elapsedAt(nowMillis: Long): Long = timer.elapsedAt(nowMillis)

    public fun snapshotAt(nowMillis: Long): WorkoutSnapshot = plan.snapshotAt(timer.elapsedAt(nowMillis))

    public fun start(nowMillis: Long): IntervalWorkout = copy(timer = timer.start(nowMillis))
    public fun pause(nowMillis: Long): IntervalWorkout = copy(timer = timer.pause(nowMillis))
    public fun toggle(nowMillis: Long): IntervalWorkout = copy(timer = timer.toggle(nowMillis))
    public fun reset(): IntervalWorkout = copy(timer = timer.reset())
    public fun settle(nowMillis: Long): IntervalWorkout = copy(timer = timer.settle(nowMillis))

    /** Jumps to [elapsedMillis] of the workout, keeping it running or paused. */
    public fun seekTo(elapsedMillis: Long, nowMillis: Long): IntervalWorkout =
        copy(timer = timer.seekTo(elapsedMillis, nowMillis))

    /** Ends the current phase now: the next phase starts, or the workout finishes after the last one. */
    public fun skipPhase(nowMillis: Long): IntervalWorkout {
        if (timer.isFinishedAt(nowMillis)) return settle(nowMillis)
        val index = plan.phaseIndexAt(elapsedAt(nowMillis))
        return seekTo(plan.phaseEnd(index), nowMillis)
    }

    /**
     * Goes back like a music player: to the start of the current phase, or to the previous phase when
     * less than [graceMillis] of the current one has passed. A finished workout goes back to
     * the start of its last phase, paused.
     */
    public fun previousPhase(nowMillis: Long, graceMillis: Long = 2_000L): IntervalWorkout {
        if (timer.isFinishedAt(nowMillis)) {
            return settle(nowMillis).seekTo(plan.phaseStarts[plan.phases.lastIndex], nowMillis)
        }
        val elapsed = elapsedAt(nowMillis)
        val index = plan.phaseIndexAt(elapsed)
        val intoPhase = elapsed - plan.phaseStarts[index]
        val target = if (intoPhase < graceMillis && index > 0) plan.phaseStarts[index - 1] else plan.phaseStarts[index]
        return seekTo(target, nowMillis)
    }

    public companion object {
        /** A workout positioned at [elapsedMillis] with [status], for previews, screenshots and restoring. */
        public fun at(
            plan: WorkoutPlan,
            elapsedMillis: Long,
            status: TimerStatus,
            nowMillis: Long,
        ): IntervalWorkout {
            val total = plan.totalMillis
            val elapsed = elapsedMillis.coerceIn(0L, total)
            val timer = when {
                status == TimerStatus.Finished || elapsed >= total ->
                    CountdownTimer(total, TimerStatus.Finished, total)
                status == TimerStatus.Running -> CountdownTimer.running(total, elapsed, nowMillis)
                status == TimerStatus.Idle && elapsed == 0L -> CountdownTimer(total)
                else -> CountdownTimer.paused(total, elapsed)
            }
            return IntervalWorkout(plan, timer)
        }
    }
}
