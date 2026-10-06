package io.github.halilozel1903.weartimer.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.halilozel1903.weartimer.IntervalWorkoutState
import io.github.halilozel1903.weartimer.core.IntervalWorkout
import io.github.halilozel1903.weartimer.core.ManualClock
import io.github.halilozel1903.weartimer.core.TimerStatus
import io.github.halilozel1903.weartimer.core.WorkoutPlan

/** Screenshot scenes, see [MainActivity]. */
enum class Scene(val id: String) {
    Picker("picker"),
    Running("running"),
    Rest("rest"),
    Done("done"),
    ;

    companion object {
        fun from(id: String?): Scene? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Elapsed workout time of each scene in [tabataPlan] (1:00 warm-up, then 0:20 work and 0:10 rest
 * rounds): round 3 work with 0:12 left, round 3 rest with 0:06 left, and the end.
 */
private fun Scene.elapsedMillis(plan: WorkoutPlan): Long = when (this) {
    Scene.Picker -> 0L
    Scene.Running -> WARM_UP_MILLIS + 2 * (WORK_MILLIS + REST_MILLIS) + 8_000L
    Scene.Rest -> WARM_UP_MILLIS + 2 * (WORK_MILLIS + REST_MILLIS) + WORK_MILLIS + 4_000L
    Scene.Done -> plan.totalMillis
}

/** A scene on a clock that never moves, so every capture looks the same. */
@Composable
fun SceneScreen(scene: Scene) {
    when (scene) {
        Scene.Picker -> {
            var work by remember { mutableLongStateOf(WORK_MILLIS) }
            SetupScreen(workMillis = work, onWorkChange = { work = it }, onStart = {})
        }
        else -> {
            val state = remember(scene) {
                val clock = ManualClock(startMillis = 1_000_000L)
                val plan = tabataPlan()
                val status = if (scene == Scene.Done) TimerStatus.Finished else TimerStatus.Running
                IntervalWorkoutState(
                    IntervalWorkout.at(plan, scene.elapsedMillis(plan), status, clock.nowMillis()),
                    clock,
                )
            }
            WorkoutScreen(state)
        }
    }
}
