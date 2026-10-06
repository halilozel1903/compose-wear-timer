package io.github.halilozel1903.weartimer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.halilozel1903.weartimer.core.DurationStepper
import io.github.halilozel1903.weartimer.core.RotaryStepAccumulator

/** Texts of a [DurationPicker], so apps can translate them. */
@Immutable
public data class DurationPickerLabels(
    val decrease: String = "Shorter",
    val increase: String = "Longer",
)

/**
 * A duration picker for a round screen: turn the crown (or rotating bezel) or tap the - and +
 * buttons. Steps grow with the value (5 s, 15 s, 30 s, 1 min, 5 min with [DurationStepper.default]),
 * each step ticks the haptics, and the ring shows the value between the stepper's limits.
 *
 * Rotary events go to the focused element, so the picker requests focus when it appears; pass your
 * own [focusRequester] when several pickers share a screen (for example in a pager).
 *
 * ```kotlin
 * var work by rememberSaveable { mutableLongStateOf(20_000L) }
 * DurationPicker(valueMillis = work, onValueChange = { work = it }, label = "WORK", caption = "Work")
 * ```
 *
 * @param label curved along the top of the ring; [bottomLabel] along the bottom, upright.
 * @param caption small text above the value.
 * @param centerButton shown between the - and + buttons, for example a start button.
 * @param content shown under the buttons.
 */
@Composable
public fun DurationPicker(
    valueMillis: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    stepper: DurationStepper = remember { DurationStepper.default() },
    label: String? = null,
    bottomLabel: String? = null,
    caption: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    labels: DurationPickerLabels = DurationPickerLabels(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    stepPixels: Float = RotaryStepAccumulator.DEFAULT_STEP_PIXELS,
    buttonSize: Dp = 36.dp,
    centerButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val accumulator = remember(stepPixels) { RotaryStepAccumulator(stepPixels) }
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(valueMillis)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val change: (Int) -> Unit = { steps ->
        val next = stepper.step(currentValue, steps)
        if (next != currentValue) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            currentOnValueChange(next)
        }
    }
    CountdownRing(
        remainingFraction = stepper.fraction(valueMillis),
        modifier = modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val steps = accumulator.accumulate(event.verticalScrollPixels, event.uptimeMillis)
                if (steps != 0) change(steps)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        color = color,
        label = label,
        labelColor = color,
        bottomLabel = bottomLabel,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TimerText(remainingMillis = valueMillis)
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { change(-1) },
                    enabled = valueMillis > stepper.minMillis,
                    modifier = Modifier.size(buttonSize),
                ) {
                    TimerIconImage(TimerIcon.Minus, contentDescription = labels.decrease, size = 18.dp)
                }
                centerButton?.invoke()
                FilledTonalIconButton(
                    onClick = { change(1) },
                    enabled = valueMillis < stepper.maxMillis,
                    modifier = Modifier.size(buttonSize),
                ) {
                    TimerIconImage(TimerIcon.Plus, contentDescription = labels.increase, size = 18.dp)
                }
            }
            content()
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}
