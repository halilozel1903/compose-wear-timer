package io.github.halilozel1903.weartimer.core

/**
 * A mutable workout driven by a [TimerClock]: the immutable [IntervalWorkout] plus alert bookkeeping.
 * Call [poll] regularly (every 100 to 250 ms on screen, every second from a service) to get the
 * alerts that came due since the last call; each alert is reported once.
 *
 * Jumps report only what the user should notice: skipping a phase reports its [TimerAlert.PhaseChanged]
 * (or [TimerAlert.Finished]) right away and none of the skipped ticks; going back reports nothing.
 */
public class WorkoutController(
    initial: IntervalWorkout,
    private val clock: TimerClock,
    countdownSeconds: Int = TimerAlerts.DEFAULT_COUNTDOWN_SECONDS,
) {
    public constructor(
        plan: WorkoutPlan,
        clock: TimerClock,
        countdownSeconds: Int = TimerAlerts.DEFAULT_COUNTDOWN_SECONDS,
    ) : this(IntervalWorkout(plan), clock, countdownSeconds)

    /** The current value. */
    public var workout: IntervalWorkout = initial
        private set

    public val plan: WorkoutPlan get() = workout.plan
    public val status: TimerStatus get() = workout.status
    public val isRunning: Boolean get() = workout.isRunning

    private val tracker = AlertTracker(countdownSeconds, initial.elapsedAt(clock.nowMillis()))
    private val pending = mutableListOf<TimerAlert>()

    /** Where the workout is now. */
    public fun snapshot(): WorkoutSnapshot = workout.snapshotAt(clock.nowMillis())

    public fun start(): Unit = update { start(it) }
    public fun pause(): Unit = update { pause(it) }
    public fun toggle(): Unit = update { toggle(it) }

    /** Back to the start, idle. Alerts that were still waiting for [poll] are dropped. */
    public fun reset() {
        update { reset() }
        pending.clear()
    }

    public fun skipPhase(): Unit = update { skipPhase(it) }
    public fun previousPhase(): Unit = update { previousPhase(it) }
    public fun seekTo(elapsedMillis: Long): Unit = update { seekTo(elapsedMillis, it) }

    /** Replaces the workout (a new plan, restored state); reports nothing. */
    public fun replace(workout: IntervalWorkout) {
        this.workout = workout
        pending.clear()
        tracker.jumpTo(workout.elapsedAt(clock.nowMillis()))
    }

    /**
     * Moves a workout whose time is up to [TimerStatus.Finished] without reporting alerts; they stay
     * due for the next [poll]. For screens that only display the time while something else (a
     * service) plays the alerts.
     */
    public fun settle() {
        workout = workout.settle(clock.nowMillis())
    }

    /** Settles a finished workout and returns the alerts that came due since the last call. */
    public fun poll(): List<TimerAlert> {
        val now = clock.nowMillis()
        workout = workout.settle(now)
        val alerts = pending + tracker.advance(plan, workout.elapsedAt(now))
        pending.clear()
        return alerts
    }

    private inline fun update(block: IntervalWorkout.(Long) -> IntervalWorkout) {
        val now = clock.nowMillis()
        val before = workout.settle(now)
        val elapsedBefore = before.elapsedAt(now)
        // Alerts that were due before this change still count.
        pending += tracker.advance(plan, elapsedBefore)
        workout = before.block(now)
        val elapsedAfter = workout.elapsedAt(now)
        if (elapsedAfter > elapsedBefore && workout.plan == before.plan) {
            // Landing on a phase boundary (skip) reports that boundary, never the skipped ticks.
            pending += TimerAlerts.between(plan, elapsedAfter - 1, elapsedAfter, countdownSeconds = 0)
        }
        tracker.jumpTo(elapsedAfter)
    }
}

/**
 * A mutable single countdown driven by a [TimerClock], with 3-2-1 ticks and a [TimerAlert.Finished]
 * alert from [poll]. The alerts refer to a one phase [WorkoutPlan.countdown] named [name].
 */
public class TimerController(
    initial: CountdownTimer,
    private val clock: TimerClock,
    public val name: String = "Timer",
    countdownSeconds: Int = TimerAlerts.DEFAULT_COUNTDOWN_SECONDS,
) {
    public constructor(durationMillis: Long, clock: TimerClock) : this(CountdownTimer(durationMillis), clock)

    /** The current value. */
    public var timer: CountdownTimer = initial
        private set

    private var plan = WorkoutPlan.countdown(initial.durationMillis, name)
    private val tracker = AlertTracker(countdownSeconds, initial.elapsedAt(clock.nowMillis()))
    private val pending = mutableListOf<TimerAlert>()

    public val status: TimerStatus get() = timer.status
    public val remainingMillis: Long get() = timer.remainingAt(clock.nowMillis())

    public fun start(): Unit = update { start(it) }
    public fun pause(): Unit = update { pause(it) }
    public fun toggle(): Unit = update { toggle(it) }

    /** Back to the full duration, idle. Alerts that were still waiting for [poll] are dropped. */
    public fun reset() {
        update { reset() }
        pending.clear()
    }

    public fun setDuration(durationMillis: Long): Unit = update { withDuration(durationMillis, it) }
    public fun addTime(millis: Long): Unit = update { plus(millis, it) }

    /** Replaces the timer (restored state); reports nothing. */
    public fun replace(timer: CountdownTimer) {
        this.timer = timer
        plan = WorkoutPlan.countdown(timer.durationMillis, name)
        pending.clear()
        tracker.jumpTo(timer.elapsedAt(clock.nowMillis()))
    }

    /** Moves a timer whose time is up to [TimerStatus.Finished] without reporting alerts. */
    public fun settle() {
        timer = timer.settle(clock.nowMillis())
    }

    /** Settles a finished timer and returns the alerts that came due since the last call. */
    public fun poll(): List<TimerAlert> {
        val now = clock.nowMillis()
        timer = timer.settle(now)
        val alerts = pending + tracker.advance(plan, timer.elapsedAt(now))
        pending.clear()
        return alerts
    }

    private inline fun update(block: CountdownTimer.(Long) -> CountdownTimer) {
        val now = clock.nowMillis()
        val before = timer.settle(now)
        // Alerts that were due before this change still count.
        pending += tracker.advance(plan, before.elapsedAt(now))
        timer = before.block(now)
        if (plan.totalMillis != timer.durationMillis) plan = WorkoutPlan.countdown(timer.durationMillis, name)
        // Changing the timer by hand never buzzes; the next poll reports from here on.
        tracker.jumpTo(timer.elapsedAt(now))
    }
}
