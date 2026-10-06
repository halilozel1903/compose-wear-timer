package io.github.halilozel1903.weartimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.halilozel1903.weartimer.core.TimeFormat
import io.github.halilozel1903.weartimer.core.WorkoutPlan
import io.github.halilozel1903.weartimer.core.WorkoutSnapshot

/** Texts of an [IntervalWorkoutScreen], so apps can translate them. */
@Immutable
public data class IntervalWorkoutLabels(
    val round: String = "ROUND",
    val rounds: String = "ROUNDS",
    val next: String = "NEXT",
    val pause: String = "Pause",
    val resume: String = "Resume",
    val skip: String = "Skip phase",
    val complete: String = "Workout complete",
    val work: String = "work",
    val restart: String = "Restart",
) {
    /** `ROUND 3/8`, or `8 ROUNDS` before the first round. */
    public fun roundText(round: Int, totalRounds: Int): String = when {
        totalRounds == 0 -> ""
        round == 0 -> "$totalRounds $rounds"
        else -> "${this.round} $round/$totalRounds"
    }
}

/**
 * A complete interval workout screen for a round watch: the phase name curved along the top in the
 * phase color, a countdown ring for the phase with a thin ring for the whole workout, the round
 * (`ROUND 3/8`), the remaining time, pause/resume and skip buttons, and the next phase on the bottom
 * bezel. When the workout ends it shows [doneContent], a summary by default.
 *
 * ```kotlin
 * val workout = rememberIntervalWorkoutState(WorkoutPlan.tabata(), onAlert = { haptics.play(it) })
 * ScreenScaffold(timeText = {}) { IntervalWorkoutScreen(workout) }
 * ```
 */
@Composable
public fun IntervalWorkoutScreen(
    state: IntervalWorkoutState,
    modifier: Modifier = Modifier,
    colors: PhaseColors = WearTimerDefaults.phaseColors(),
    labels: IntervalWorkoutLabels = IntervalWorkoutLabels(),
    onPauseResume: () -> Unit = state::toggle,
    onSkip: () -> Unit = state::skipPhase,
    onRestart: () -> Unit = state::restart,
    doneContent: @Composable BoxScope.(WorkoutSnapshot) -> Unit = { snapshot ->
        WorkoutDoneContent(state.plan, snapshot, colors = colors, labels = labels, onRestart = onRestart)
    },
) {
    IntervalWorkoutScreen(
        plan = state.plan,
        snapshot = state.snapshot,
        isRunning = state.isRunning,
        modifier = modifier,
        colors = colors,
        labels = labels,
        onPauseResume = onPauseResume,
        onSkip = onSkip,
        doneContent = doneContent,
    )
}

/**
 * The stateless [IntervalWorkoutScreen]: draws [snapshot] of [plan]. Useful for previews and for
 * apps that keep the workout somewhere else.
 */
@Composable
public fun IntervalWorkoutScreen(
    plan: WorkoutPlan,
    snapshot: WorkoutSnapshot,
    isRunning: Boolean,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    colors: PhaseColors = WearTimerDefaults.phaseColors(),
    labels: IntervalWorkoutLabels = IntervalWorkoutLabels(),
    buttonSize: Dp = 44.dp,
    doneContent: @Composable BoxScope.(WorkoutSnapshot) -> Unit = { done ->
        WorkoutDoneContent(plan, done, colors = colors, labels = labels, onRestart = null)
    },
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (snapshot.isFinished) {
            doneContent(snapshot)
        } else {
            WorkoutRunningContent(snapshot, isRunning, onPauseResume, onSkip, colors, labels, buttonSize)
        }
    }
}

@Composable
private fun WorkoutRunningContent(
    snapshot: WorkoutSnapshot,
    isRunning: Boolean,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    colors: PhaseColors,
    labels: IntervalWorkoutLabels,
    buttonSize: Dp,
) {
    val phaseColor = colors.of(snapshot.phase.kind)
    val next = snapshot.nextPhase
    CountdownRing(
        remainingFraction = snapshot.phaseRemainingFraction,
        color = phaseColor,
        trackColor = colors.track,
        label = snapshot.phase.label.uppercase(),
        bottomLabel = next?.let { "${labels.next} · ${it.label.uppercase()} ${TimeFormat.countdown(it.durationMillis)}" },
        totalProgress = snapshot.totalProgress,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val roundText = labels.roundText(snapshot.round, snapshot.totalRounds)
            if (roundText.isNotEmpty()) {
                Text(
                    text = roundText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TimerText(remainingMillis = snapshot.phaseRemainingMillis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = onPauseResume,
                    modifier = Modifier.size(buttonSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = phaseColor,
                        contentColor = colors.onPhase,
                    ),
                ) {
                    TimerIconImage(
                        icon = if (isRunning) TimerIcon.Pause else TimerIcon.Play,
                        contentDescription = if (isRunning) labels.pause else labels.resume,
                        size = 20.dp,
                    )
                }
                FilledTonalIconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(buttonSize),
                ) {
                    TimerIconImage(TimerIcon.Skip, contentDescription = labels.skip, size = 20.dp)
                }
            }
        }
    }
}

/**
 * The summary shown when a workout is done: a full ring in the done color, the plan name on top,
 * the total time, rounds and work time, and a restart button when [onRestart] is set.
 */
@Composable
public fun WorkoutDoneContent(
    plan: WorkoutPlan,
    snapshot: WorkoutSnapshot,
    modifier: Modifier = Modifier,
    colors: PhaseColors = WearTimerDefaults.phaseColors(),
    labels: IntervalWorkoutLabels = IntervalWorkoutLabels(),
    onRestart: (() -> Unit)? = null,
) {
    CountdownRing(
        remainingFraction = 1f,
        modifier = modifier,
        color = colors.done,
        trackColor = colors.track,
        label = plan.name.uppercase(),
        animate = false,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = labels.complete,
                style = MaterialTheme.typography.titleMedium,
                color = colors.done,
                textAlign = TextAlign.Center,
            )
            Text(
                text = TimeFormat.clock(snapshot.totalMillis),
                style = MaterialTheme.typography.numeralMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val details = buildList<String> {
                if (snapshot.totalRounds > 0) add("${snapshot.totalRounds} ${labels.rounds.lowercase()}")
                if (plan.workMillis > 0) add("${TimeFormat.clock(plan.workMillis)} ${labels.work}")
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRestart != null) {
                Spacer(Modifier.height(6.dp))
                FilledTonalIconButton(onClick = onRestart, modifier = Modifier.size(40.dp)) {
                    TimerIconImage(TimerIcon.Restart, contentDescription = labels.restart, size = 20.dp)
                }
            }
        }
    }
}
