package io.github.halilozel1903.weartimer.sample

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.halilozel1903.weartimer.ElapsedRealtimeClock
import io.github.halilozel1903.weartimer.IntervalWorkoutState
import io.github.halilozel1903.weartimer.core.WorkoutPlan

/**
 * The one workout of the app, shared by the screen and [WorkoutService]. It lives as long as the
 * process; the foreground service keeps the process alive while the workout runs.
 */
object WorkoutSession {
    var state: IntervalWorkoutState? by mutableStateOf(null)
        private set

    fun start(context: Context, plan: WorkoutPlan) {
        state = IntervalWorkoutState(plan, ElapsedRealtimeClock).also { it.start() }
        WorkoutService.start(context)
    }

    fun restart(context: Context) {
        state?.restart() ?: return
        WorkoutService.start(context)
    }

    fun stop(context: Context) {
        state = null
        WorkoutService.stop(context)
    }
}
