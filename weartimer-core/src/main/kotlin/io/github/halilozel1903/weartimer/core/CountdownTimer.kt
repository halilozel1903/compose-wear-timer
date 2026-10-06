package io.github.halilozel1903.weartimer.core

/** The four states of a timer. */
public enum class TimerStatus {
    /** Not started yet, or reset. Nothing has elapsed. */
    Idle,

    /** Counting down. */
    Running,

    /** Stopped part way; [CountdownTimer.start] continues where it left off. */
    Paused,

    /** Reached zero. [CountdownTimer.start] starts over. */
    Finished,
}

/**
 * An immutable countdown: a [durationMillis], a [status] and the time counted so far. It never reads
 * a clock; pass the current time (from a [TimerClock]) to every call. Elapsed time is
 * [accumulatedMillis] plus, while running, the time since [startedAtMillis].
 *
 * ```kotlin
 * var timer = CountdownTimer(durationMillis = 60_000)
 * timer = timer.start(now = 0)
 * timer.remainingAt(now = 15_000)        // 45_000
 * timer = timer.pause(now = 15_000)      // Paused, 15 s elapsed
 * timer = timer.start(now = 100_000)     // continues: 45 s left
 * timer.settle(now = 145_000).status     // Finished
 * ```
 *
 * A running timer whose time is up still reports [TimerStatus.Running] until [settle] (or any other
 * operation) is called with a time at or after its end; [isFinishedAt] answers without settling.
 */
public data class CountdownTimer(
    val durationMillis: Long,
    val status: TimerStatus = TimerStatus.Idle,
    val accumulatedMillis: Long = 0L,
    val startedAtMillis: Long? = null,
) {
    init {
        require(durationMillis > 0) { "durationMillis must be positive, was $durationMillis" }
        require(accumulatedMillis in 0..durationMillis) {
            "accumulatedMillis must be in 0..$durationMillis, was $accumulatedMillis"
        }
        require((status == TimerStatus.Running) == (startedAtMillis != null)) {
            "startedAtMillis must be set exactly when the timer is running"
        }
    }

    public val isRunning: Boolean get() = status == TimerStatus.Running

    /** Time counted at [nowMillis], between 0 and [durationMillis]. */
    public fun elapsedAt(nowMillis: Long): Long {
        val started = startedAtMillis ?: return accumulatedMillis
        val running = (nowMillis - started).coerceAtLeast(0L)
        return (accumulatedMillis + running).coerceAtMost(durationMillis)
    }

    /** Time left at [nowMillis], between 0 and [durationMillis]. */
    public fun remainingAt(nowMillis: Long): Long = durationMillis - elapsedAt(nowMillis)

    /** Share of the duration that has elapsed, `0..1`. */
    public fun progressAt(nowMillis: Long): Float = elapsedAt(nowMillis).toFloat() / durationMillis

    /** Share of the duration that is left, `1..0`; what a depleting countdown ring shows. */
    public fun remainingFractionAt(nowMillis: Long): Float = 1f - progressAt(nowMillis)

    /** True when the timer is finished or, while running, has reached zero at [nowMillis]. */
    public fun isFinishedAt(nowMillis: Long): Boolean =
        status == TimerStatus.Finished || (isRunning && elapsedAt(nowMillis) >= durationMillis)

    /** Starts or resumes. A finished timer starts over from the full duration. */
    public fun start(nowMillis: Long): CountdownTimer = when (status) {
        TimerStatus.Running -> settle(nowMillis)
        TimerStatus.Finished -> copy(status = TimerStatus.Running, accumulatedMillis = 0L, startedAtMillis = nowMillis)
        TimerStatus.Idle, TimerStatus.Paused -> copy(status = TimerStatus.Running, startedAtMillis = nowMillis)
    }

    /** Pauses a running timer. If its time is already up, it finishes instead. */
    public fun pause(nowMillis: Long): CountdownTimer {
        if (!isRunning) return this
        val elapsed = elapsedAt(nowMillis)
        return if (elapsed >= durationMillis) {
            finished()
        } else {
            copy(status = TimerStatus.Paused, accumulatedMillis = elapsed, startedAtMillis = null)
        }
    }

    /** Pauses when running, otherwise starts. */
    public fun toggle(nowMillis: Long): CountdownTimer = if (isRunning) pause(nowMillis) else start(nowMillis)

    /** Back to [TimerStatus.Idle] with nothing elapsed; keeps the duration. */
    public fun reset(): CountdownTimer = CountdownTimer(durationMillis)

    /** Moves a running timer whose time is up to [TimerStatus.Finished]; returns other timers as they are. */
    public fun settle(nowMillis: Long): CountdownTimer =
        if (isRunning && elapsedAt(nowMillis) >= durationMillis) finished() else this

    /**
     * Jumps to [elapsedMillis] (clamped to the duration) and keeps running or paused. Jumping to the
     * end finishes the timer; jumping an idle timer forward pauses it there.
     */
    public fun seekTo(elapsedMillis: Long, nowMillis: Long): CountdownTimer {
        val target = elapsedMillis.coerceIn(0L, durationMillis)
        if (target >= durationMillis) return finished()
        return when (status) {
            TimerStatus.Running -> copy(accumulatedMillis = target, startedAtMillis = nowMillis)
            TimerStatus.Idle -> if (target == 0L) this else copy(status = TimerStatus.Paused, accumulatedMillis = target)
            TimerStatus.Paused, TimerStatus.Finished ->
                copy(status = TimerStatus.Paused, accumulatedMillis = target, startedAtMillis = null)
        }
    }

    /**
     * Changes the duration, for example from a picker. Time already counted is kept and clamped; a
     * timer that would have nothing left finishes, a finished timer with time left again pauses.
     */
    public fun withDuration(newDurationMillis: Long, nowMillis: Long): CountdownTimer {
        require(newDurationMillis > 0) { "durationMillis must be positive, was $newDurationMillis" }
        val elapsed = elapsedAt(nowMillis)
        if (status == TimerStatus.Idle) return CountdownTimer(newDurationMillis)
        if (elapsed >= newDurationMillis) {
            return CountdownTimer(newDurationMillis, TimerStatus.Finished, newDurationMillis)
        }
        return when (status) {
            TimerStatus.Running -> CountdownTimer(newDurationMillis, TimerStatus.Running, elapsed, nowMillis)
            else -> CountdownTimer(newDurationMillis, TimerStatus.Paused, elapsed)
        }
    }

    /** Adds [millis] to the duration ("+1 min"); a finished timer resumes running with the extra time. */
    public fun plus(millis: Long, nowMillis: Long): CountdownTimer {
        require(millis > 0) { "millis must be positive, was $millis" }
        val elapsed = elapsedAt(nowMillis)
        return when (status) {
            TimerStatus.Finished -> CountdownTimer(durationMillis + millis, TimerStatus.Running, elapsed, nowMillis)
            TimerStatus.Running -> CountdownTimer(durationMillis + millis, TimerStatus.Running, elapsed, nowMillis)
            else -> copy(durationMillis = durationMillis + millis)
        }
    }

    private fun finished(): CountdownTimer =
        CountdownTimer(durationMillis, TimerStatus.Finished, durationMillis, null)

    public companion object {
        /** A timer paused at [elapsedMillis], for previews, screenshots and restoring saved state. */
        public fun paused(durationMillis: Long, elapsedMillis: Long): CountdownTimer =
            CountdownTimer(durationMillis, TimerStatus.Paused, elapsedMillis.coerceIn(0L, durationMillis))

        /** A timer that has been running for [elapsedMillis] at [nowMillis]. */
        public fun running(durationMillis: Long, elapsedMillis: Long, nowMillis: Long): CountdownTimer =
            CountdownTimer(durationMillis, TimerStatus.Running, elapsedMillis.coerceIn(0L, durationMillis), nowMillis)
    }
}
