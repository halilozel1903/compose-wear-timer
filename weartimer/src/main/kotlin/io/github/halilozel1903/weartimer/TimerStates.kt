package io.github.halilozel1903.weartimer

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.halilozel1903.weartimer.core.CountdownTimer
import io.github.halilozel1903.weartimer.core.IntervalWorkout
import io.github.halilozel1903.weartimer.core.TimerAlert
import io.github.halilozel1903.weartimer.core.TimerAlerts
import io.github.halilozel1903.weartimer.core.TimerClock
import io.github.halilozel1903.weartimer.core.TimerController
import io.github.halilozel1903.weartimer.core.TimerStatus
import io.github.halilozel1903.weartimer.core.WorkoutController
import io.github.halilozel1903.weartimer.core.WorkoutPlan
import io.github.halilozel1903.weartimer.core.WorkoutSnapshot
import kotlinx.coroutines.delay

/**
 * `SystemClock.elapsedRealtime`: monotonic, keeps counting in deep sleep, and the time base that
 * Ongoing Activity timers use, so a saved start time is still right after process death.
 */
public val ElapsedRealtimeClock: TimerClock = TimerClock { SystemClock.elapsedRealtime() }

/**
 * Compose state for a single countdown: a core [TimerController] whose values are mirrored into
 * snapshot state, plus the time of the last tick. Create it with [rememberWearTimerState], or
 * construct it directly to share it with a service.
 */
@Stable
public class WearTimerState(
    initial: CountdownTimer,
    private val clock: TimerClock = ElapsedRealtimeClock,
    name: String = "Timer",
) {
    private val controller = TimerController(initial, clock, name)

    /** The current value. */
    public var timer: CountdownTimer by mutableStateOf(initial)
        private set

    private var nowMillis by mutableLongStateOf(clock.nowMillis())

    public val durationMillis: Long get() = timer.durationMillis
    public val remainingMillis: Long get() = timer.remainingAt(nowMillis)
    public val elapsedMillis: Long get() = timer.elapsedAt(nowMillis)

    /** `1..0`, what a depleting ring shows. */
    public val remainingFraction: Float get() = timer.remainingFractionAt(nowMillis)
    public val status: TimerStatus get() = timer.status
    public val isRunning: Boolean get() = timer.isRunning

    public fun start(): Unit = sync { controller.start() }
    public fun pause(): Unit = sync { controller.pause() }
    public fun toggle(): Unit = sync { controller.toggle() }
    public fun reset(): Unit = sync { controller.reset() }
    public fun setDuration(durationMillis: Long): Unit = sync { controller.setDuration(durationMillis) }
    public fun addTime(millis: Long): Unit = sync { controller.addTime(millis) }

    /** Reads the clock again and finishes a timer at zero, without taking its alerts. */
    public fun tick(): Unit = sync { controller.settle() }

    /** Reads the clock again and returns the alerts that came due since the last call. */
    public fun poll(): List<TimerAlert> {
        val alerts = controller.poll()
        sync {}
        return alerts
    }

    private inline fun sync(block: () -> Unit) {
        block()
        timer = controller.timer
        nowMillis = clock.nowMillis()
    }

    public companion object {
        /** Saves the duration, the status and the counted time. */
        public fun saver(clock: TimerClock = ElapsedRealtimeClock, name: String = "Timer"): Saver<WearTimerState, Any> =
            listSaver<WearTimerState, Long>(
                save = { state ->
                    val timer = state.timer
                    buildList<Long> {
                        add(timer.status.ordinal.toLong())
                        add(timer.durationMillis)
                        add(timer.accumulatedMillis)
                        add(timer.startedAtMillis ?: -1L)
                    }
                },
                restore = { saved ->
                    val status = TimerStatus.entries[saved[0].toInt()]
                    val timer = CountdownTimer(
                        durationMillis = saved[1],
                        status = status,
                        accumulatedMillis = saved[2],
                        startedAtMillis = saved[3].takeIf { status == TimerStatus.Running },
                    )
                    WearTimerState(timer, clock, name)
                },
            )
    }
}

/**
 * Remembers a [WearTimerState] that survives configuration changes and process death, and ticks it
 * every [tickIntervalMillis] while it runs.
 *
 * ```kotlin
 * val haptics = rememberWearHaptics()
 * val timer = rememberWearTimerState(durationMillis = 90_000, onAlert = { haptics.play(it) })
 * CountdownRing(timer, label = "PLANK")
 * Button(onClick = timer::toggle) { Text(if (timer.isRunning) "Pause" else "Start") }
 * ```
 *
 * @param onAlert called with 3-2-1 ticks and the end; leave it null when a service plays the alerts.
 */
@Composable
public fun rememberWearTimerState(
    durationMillis: Long,
    clock: TimerClock = ElapsedRealtimeClock,
    name: String = "Timer",
    tickIntervalMillis: Long = WearTimerDefaults.TickIntervalMillis,
    onAlert: ((TimerAlert) -> Unit)? = null,
): WearTimerState {
    val state = rememberSaveable(saver = WearTimerState.saver(clock, name)) {
        WearTimerState(CountdownTimer(durationMillis), clock, name)
    }
    WearTimerTicker(state, tickIntervalMillis, onAlert)
    return state
}

/**
 * Ticks a [WearTimerState] while it runs; [rememberWearTimerState] already does this. Use it for a
 * state you created yourself. When several alerts came due at once (the screen was off), only
 * [TimerAlerts.mostImportant] is delivered.
 */
@Composable
public fun WearTimerTicker(
    state: WearTimerState,
    tickIntervalMillis: Long = WearTimerDefaults.TickIntervalMillis,
    onAlert: ((TimerAlert) -> Unit)? = null,
) {
    val currentOnAlert by rememberUpdatedState(onAlert)
    LaunchedEffect(state, state.isRunning, tickIntervalMillis) {
        fun step() {
            val callback = currentOnAlert
            if (callback == null) {
                state.tick()
            } else {
                TimerAlerts.mostImportant(state.poll())?.let(callback)
            }
        }
        step()
        while (state.isRunning) {
            delay(tickIntervalMillis)
            step()
        }
    }
}

/**
 * Compose state for an interval workout: a core [WorkoutController] mirrored into snapshot state.
 * Read [snapshot] for the phase, round, remaining time and progress. Create it with
 * [rememberIntervalWorkoutState], or construct it directly to share it with a foreground service.
 */
@Stable
public class IntervalWorkoutState(
    initial: IntervalWorkout,
    private val clock: TimerClock = ElapsedRealtimeClock,
) {
    public constructor(plan: WorkoutPlan, clock: TimerClock = ElapsedRealtimeClock) : this(IntervalWorkout(plan), clock)

    private val controller = WorkoutController(initial, clock)

    /** The current value. */
    public var workout: IntervalWorkout by mutableStateOf(initial)
        private set

    private var nowMillis by mutableLongStateOf(clock.nowMillis())

    public val plan: WorkoutPlan get() = workout.plan
    public val status: TimerStatus get() = workout.status
    public val isRunning: Boolean get() = workout.isRunning

    /** Phase, round, remaining time and progress as of the last tick. */
    public val snapshot: WorkoutSnapshot get() = workout.snapshotAt(nowMillis)

    public fun start(): Unit = sync { controller.start() }
    public fun pause(): Unit = sync { controller.pause() }
    public fun toggle(): Unit = sync { controller.toggle() }
    public fun reset(): Unit = sync { controller.reset() }

    /** Starts over from the first phase. */
    public fun restart(): Unit = sync {
        controller.reset()
        controller.start()
    }

    public fun skipPhase(): Unit = sync { controller.skipPhase() }
    public fun previousPhase(): Unit = sync { controller.previousPhase() }
    public fun seekTo(elapsedMillis: Long): Unit = sync { controller.seekTo(elapsedMillis) }

    /** Reads the clock again and finishes a workout at its end, without taking its alerts. */
    public fun tick(): Unit = sync { controller.settle() }

    /** Reads the clock again and returns the alerts that came due since the last call. */
    public fun poll(): List<TimerAlert> {
        val alerts = controller.poll()
        sync {}
        return alerts
    }

    private inline fun sync(block: () -> Unit) {
        block()
        workout = controller.workout
        nowMillis = clock.nowMillis()
    }

    public companion object {
        /** Saves the status and the counted time; the [plan] itself comes from the caller. */
        public fun saver(plan: WorkoutPlan, clock: TimerClock = ElapsedRealtimeClock): Saver<IntervalWorkoutState, Any> =
            listSaver<IntervalWorkoutState, Long>(
                save = { state ->
                    val timer = state.workout.timer
                    buildList<Long> {
                        add(timer.status.ordinal.toLong())
                        add(timer.accumulatedMillis)
                        add(timer.startedAtMillis ?: -1L)
                    }
                },
                restore = { saved ->
                    val status = TimerStatus.entries[saved[0].toInt()]
                    val timer = CountdownTimer(
                        durationMillis = plan.totalMillis,
                        status = status,
                        accumulatedMillis = saved[1].coerceIn(0L, plan.totalMillis),
                        startedAtMillis = saved[2].takeIf { status == TimerStatus.Running },
                    )
                    IntervalWorkoutState(IntervalWorkout(plan, timer), clock)
                },
            )
    }
}

/**
 * Remembers an [IntervalWorkoutState] for [plan] that survives configuration changes and process
 * death, and ticks it while it runs. A different plan gives a fresh state.
 *
 * ```kotlin
 * val haptics = rememberWearHaptics()
 * val workout = rememberIntervalWorkoutState(WorkoutPlan.tabata(), onAlert = { haptics.play(it) })
 * IntervalWorkoutScreen(workout)
 * ```
 *
 * @param initial where to begin, for example `IntervalWorkout.at(...)` in previews.
 * @param onAlert called with 3-2-1 ticks, phase changes and the end; leave it null when a
 *   foreground service plays the alerts.
 */
@Composable
public fun rememberIntervalWorkoutState(
    plan: WorkoutPlan,
    clock: TimerClock = ElapsedRealtimeClock,
    initial: IntervalWorkout = IntervalWorkout(plan),
    tickIntervalMillis: Long = WearTimerDefaults.TickIntervalMillis,
    onAlert: ((TimerAlert) -> Unit)? = null,
): IntervalWorkoutState {
    require(initial.plan == plan) { "initial must use the same plan" }
    val state = key(plan) {
        rememberSaveable(saver = IntervalWorkoutState.saver(plan, clock)) {
            IntervalWorkoutState(initial, clock)
        }
    }
    IntervalWorkoutTicker(state, tickIntervalMillis, onAlert)
    return state
}

/**
 * Ticks an [IntervalWorkoutState] while it runs; [rememberIntervalWorkoutState] already does this.
 * Use it for a state you created yourself (for example one shared with a service). When several
 * alerts came due at once, only [TimerAlerts.mostImportant] is delivered.
 */
@Composable
public fun IntervalWorkoutTicker(
    state: IntervalWorkoutState,
    tickIntervalMillis: Long = WearTimerDefaults.TickIntervalMillis,
    onAlert: ((TimerAlert) -> Unit)? = null,
) {
    val currentOnAlert by rememberUpdatedState(onAlert)
    LaunchedEffect(state, state.isRunning, tickIntervalMillis) {
        fun step() {
            val callback = currentOnAlert
            if (callback == null) {
                state.tick()
            } else {
                TimerAlerts.mostImportant(state.poll())?.let(callback)
            }
        }
        step()
        while (state.isRunning) {
            delay(tickIntervalMillis)
            step()
        }
    }
}
