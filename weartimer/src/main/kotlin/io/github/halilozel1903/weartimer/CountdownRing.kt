package io.github.halilozel1903.weartimer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.sizeIn
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.halilozel1903.weartimer.core.PhaseKind
import io.github.halilozel1903.weartimer.core.TimeFormat

/** Colors of the phases of a workout, see [WearTimerDefaults.phaseColors]. */
@Immutable
public data class PhaseColors(
    val warmUp: Color,
    val work: Color,
    val rest: Color,
    val coolDown: Color,
    val done: Color,
    val track: Color,
    /** Text and icons drawn on a phase colored button. */
    val onPhase: Color,
) {
    /** The color of [kind]. */
    public fun of(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WarmUp -> warmUp
        PhaseKind.Work -> work
        PhaseKind.Rest -> rest
        PhaseKind.CoolDown -> coolDown
    }
}

/** Defaults for the timer components. */
public object WearTimerDefaults {
    /** How often the on screen time is refreshed while running. */
    public const val TickIntervalMillis: Long = 100L

    /** Ring stroke that reads well on a 40 to 45 mm watch. */
    public val StrokeWidth: Dp = 10.dp

    /** Stroke of the thin inner ring that shows the progress of the whole workout. */
    public val TotalStrokeWidth: Dp = 3.dp

    /** Inset from the screen edge when a ring hugs the bezel. */
    public val EdgeInset: Dp = 3.dp

    /** Size of the curved labels along the ring. */
    public val LabelFontSize: TextUnit = 13.sp

    /** Warm amber, energetic coral, calm cyan, soft violet and green for done. */
    @Composable
    public fun phaseColors(
        warmUp: Color = Color(0xFFFFC163),
        work: Color = Color(0xFFFF6B57),
        rest: Color = Color(0xFF5CD2F0),
        coolDown: Color = Color(0xFFA98BFF),
        done: Color = Color(0xFF7EDC8A),
        track: Color = MaterialTheme.colorScheme.surfaceContainer,
        onPhase: Color = Color(0xFF14171F),
    ): PhaseColors = PhaseColors(warmUp, work, rest, coolDown, done, track, onPhase)
}

/**
 * A countdown ring along the edge of a round screen. The colored arc starts at 12 o'clock and
 * shrinks counter clockwise toward it as time runs out ([remainingFraction] from 1 to 0). An optional
 * thin inner ring shows the progress of the whole workout, and curved labels sit on the top and
 * bottom of the dial (the bottom one reads upright). Put the time in [content].
 *
 * ```kotlin
 * CountdownRing(
 *     remainingFraction = snapshot.phaseRemainingFraction,
 *     color = colors.of(snapshot.phase.kind),
 *     label = "WORK",
 *     bottomLabel = "NEXT · REST 0:10",
 *     totalProgress = snapshot.totalProgress,
 * ) {
 *     TimerText(snapshot.phaseRemainingMillis)
 * }
 * ```
 */
@Composable
public fun CountdownRing(
    remainingFraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    strokeWidth: Dp = WearTimerDefaults.StrokeWidth,
    label: String? = null,
    labelColor: Color = color,
    bottomLabel: String? = null,
    bottomLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    totalProgress: Float? = null,
    totalColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    animate: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val target = remainingFraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "CountdownRing",
    )
    val shown = if (animate) animated else target
    val total = totalProgress?.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(WearTimerDefaults.EdgeInset)) {
            val stroke = strokeWidth.toPx()
            val radius = (size.minDimension - stroke) / 2f
            drawRing(trackColor, 360f, radius, stroke, round = false)
            if (shown > 0f) drawRing(color, 360f * shown, radius, stroke, round = shown < 1f)
            if (total != null) {
                val thin = WearTimerDefaults.TotalStrokeWidth.toPx()
                val innerRadius = radius - stroke / 2f - 5.dp.toPx() - thin / 2f
                drawRing(trackColor, 360f, innerRadius, thin, round = false)
                if (total > 0f) drawRing(totalColor, 360f * total, innerRadius, thin, round = total < 1f, clockwise = true)
            }
        }
        val labelInset = WearTimerDefaults.EdgeInset + strokeWidth +
            if (total != null) WearTimerDefaults.TotalStrokeWidth + 9.dp else 4.dp
        if (label != null) RingLabel(label, labelColor, labelInset, top = true)
        if (bottomLabel != null) RingLabel(bottomLabel, bottomLabelColor, labelInset, top = false)
        content()
    }
}

/**
 * A [CountdownRing] for a [WearTimerState], with the remaining time in the middle and [label] on top.
 */
@Composable
public fun CountdownRing(
    state: WearTimerState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
    bottomLabel: String? = null,
    content: @Composable BoxScope.() -> Unit = { TimerText(state.remainingMillis) },
) {
    CountdownRing(
        remainingFraction = state.remainingFraction,
        modifier = modifier,
        color = color,
        label = label,
        bottomLabel = bottomLabel,
        content = content,
    )
}

/**
 * Remaining time as `m:ss`, rounded up like a countdown (`0:01` until the time is really up), with
 * a spoken content description.
 */
@Composable
public fun TimerText(
    remainingMillis: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.numeralMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = TimeFormat.countdown(remainingMillis),
        modifier = modifier.semantics { contentDescription = TimeFormat.spoken(remainingMillis + 999L) },
        style = style,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun RingLabel(text: String, color: Color, inset: Dp, top: Boolean) {
    Box(Modifier.fillMaxSize().padding(inset)) {
        CurvedLayout(
            modifier = Modifier.fillMaxSize(),
            anchor = if (top) 270f else 90f,
            angularDirection = if (top) CurvedDirection.Angular.Normal else CurvedDirection.Angular.Reversed,
        ) {
            basicCurvedText(
                text,
                CurvedTextStyle(
                    color = color,
                    fontSize = WearTimerDefaults.LabelFontSize,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                ),
                modifier = CurvedModifier.sizeIn(maxSweepDegrees = 120f),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Draws an arc from 12 o'clock over [sweep] degrees: counter clockwise (the remaining time
 * shrinking back to the top) or [clockwise] (progress growing from the top).
 */
private fun DrawScope.drawRing(
    color: Color,
    sweep: Float,
    radius: Float,
    stroke: Float,
    round: Boolean,
    clockwise: Boolean = false,
) {
    val diameter = radius * 2f
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = if (clockwise) sweep else -sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(diameter, diameter),
        style = Stroke(width = stroke, cap = if (round) StrokeCap.Round else StrokeCap.Butt),
    )
}
