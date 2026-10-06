package io.github.halilozel1903.weartimer.core

/**
 * A monotonic millisecond clock. Nothing in this module reads the time on its own: the immutable
 * values ([CountdownTimer], [IntervalWorkout]) take `nowMillis` in every call, and the controllers
 * ([TimerController], [WorkoutController]) ask a [TimerClock]. Tests, previews and screenshots use
 * [ManualClock]; Android code uses `SystemClock.elapsedRealtime`.
 */
public fun interface TimerClock {
    /** The current time in milliseconds. Only differences between two readings matter. */
    public fun nowMillis(): Long

    public companion object {
        /** A JVM monotonic clock based on [System.nanoTime]. */
        public val Monotonic: TimerClock = TimerClock { System.nanoTime() / 1_000_000L }
    }
}

/** A clock that only moves when told to. Handy for tests, previews and screenshots. */
public class ManualClock(startMillis: Long = 0L) : TimerClock {
    private var now: Long = startMillis

    override fun nowMillis(): Long = now

    /** Moves the clock forward by [millis] (must not be negative) and returns the new time. */
    public fun advanceBy(millis: Long): Long {
        require(millis >= 0) { "A clock can't go back in time (advanceBy($millis))" }
        now += millis
        return now
    }

    /** Sets the clock to [millis]; it must not be earlier than the current time. */
    public fun set(millis: Long) {
        require(millis >= now) { "A clock can't go back in time ($now -> $millis)" }
        now = millis
    }
}
