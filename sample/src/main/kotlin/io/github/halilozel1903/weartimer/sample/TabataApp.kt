package io.github.halilozel1903.weartimer.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import io.github.halilozel1903.weartimer.DurationPicker
import io.github.halilozel1903.weartimer.IntervalWorkoutScreen
import io.github.halilozel1903.weartimer.IntervalWorkoutState
import io.github.halilozel1903.weartimer.IntervalWorkoutTicker
import io.github.halilozel1903.weartimer.TimerIcon
import io.github.halilozel1903.weartimer.TimerIconImage
import io.github.halilozel1903.weartimer.WearTimerDefaults
import io.github.halilozel1903.weartimer.core.DurationStepper
import io.github.halilozel1903.weartimer.core.TimeFormat
import io.github.halilozel1903.weartimer.core.TimerStatus
import io.github.halilozel1903.weartimer.core.WorkoutPlan

const val WARM_UP_MILLIS = 60_000L
const val WORK_MILLIS = 20_000L
const val REST_MILLIS = 10_000L
const val COOL_DOWN_MILLIS = 60_000L
const val ROUNDS = 8

/** The fictional Tabata app's workout: 1:00 warm-up, 8 rounds of work and 0:10 rest, 1:00 cool-down. */
fun tabataPlan(workMillis: Long = WORK_MILLIS): WorkoutPlan = WorkoutPlan.intervals(
    name = "Tabata",
    workMillis = workMillis,
    restMillis = REST_MILLIS,
    rounds = ROUNDS,
    warmUpMillis = WARM_UP_MILLIS,
    coolDownMillis = COOL_DOWN_MILLIS,
)

/** The real app: pick the work interval, start, and the workout runs in [WorkoutService]. */
@Composable
fun TabataApp() {
    val context = LocalContext.current
    val session = WorkoutSession.state
    if (session == null) {
        var work by rememberSaveable { mutableLongStateOf(WORK_MILLIS) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The workout runs either way; without the permission there is just no notification.
            WorkoutSession.start(context, tabataPlan(work))
        }
        SetupScreen(
            workMillis = work,
            onWorkChange = { work = it },
            onStart = {
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    WorkoutSession.start(context, tabataPlan(work))
                }
            },
        )
    } else {
        // Back leaves a running workout to the ongoing activity; after the end it returns to the picker.
        BackHandler(enabled = session.status == TimerStatus.Finished) { WorkoutSession.stop(context) }
        WorkoutScreen(session, onRestart = { WorkoutSession.restart(context) })
    }
}

/** The work interval picker with a start button between - and +. */
@Composable
fun SetupScreen(workMillis: Long, onWorkChange: (Long) -> Unit, onStart: () -> Unit) {
    val colors = WearTimerDefaults.phaseColors()
    val stepper = remember { DurationStepper.default(minMillis = 5_000L, maxMillis = 60_000L) }
    ScreenScaffold(timeText = {}) {
        DurationPicker(
            valueMillis = workMillis,
            onValueChange = onWorkChange,
            stepper = stepper,
            label = "WORK",
            bottomLabel = "$ROUNDS ROUNDS · ${TimeFormat.compact(REST_MILLIS).uppercase()} REST",
            caption = "Work interval",
            color = colors.work,
            centerButton = {
                FilledIconButton(
                    onClick = onStart,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.work,
                        contentColor = colors.onPhase,
                    ),
                ) {
                    TimerIconImage(TimerIcon.Play, contentDescription = "Start", size = 20.dp)
                }
            },
        )
    }
}

/**
 * The workout. The screen only refreshes the time ([IntervalWorkoutTicker] without `onAlert`);
 * [WorkoutService] plays the alerts, also while the screen is off.
 */
@Composable
fun WorkoutScreen(state: IntervalWorkoutState, onRestart: () -> Unit = state::restart) {
    IntervalWorkoutTicker(state)
    ScreenScaffold(timeText = {}) {
        IntervalWorkoutScreen(state, onRestart = onRestart)
    }
}
