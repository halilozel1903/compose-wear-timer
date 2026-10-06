package io.github.halilozel1903.weartimer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.LocalContentColor

/** Small drawn icons used by the timer components, so the library needs no icon dependency. */
public enum class TimerIcon { Play, Pause, Skip, Plus, Minus, Restart }

/**
 * Draws a [TimerIcon] in [tint] (the current content color by default, so it follows the button
 * it sits in).
 */
@Composable
public fun TimerIconImage(
    icon: TimerIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(semanticsModifier)) {
        val w = this.size.width
        val h = this.size.height
        when (icon) {
            TimerIcon.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.18f)
                    lineTo(w * 0.84f, h * 0.5f)
                    lineTo(w * 0.28f, h * 0.82f)
                    close()
                }
                drawPath(path, tint)
                drawPath(path, tint, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
            }
            TimerIcon.Pause -> {
                val barWidth = w * 0.22f
                val radius = CornerRadius(barWidth / 3f)
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.18f), Size(barWidth, h * 0.64f), radius)
                drawRoundRect(tint, Offset(w * 0.56f, h * 0.18f), Size(barWidth, h * 0.64f), radius)
            }
            TimerIcon.Skip -> {
                val path = Path().apply {
                    moveTo(w * 0.16f, h * 0.2f)
                    lineTo(w * 0.62f, h * 0.5f)
                    lineTo(w * 0.16f, h * 0.8f)
                    close()
                }
                drawPath(path, tint)
                drawPath(path, tint, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
                drawRoundRect(
                    tint,
                    Offset(w * 0.68f, h * 0.2f),
                    Size(w * 0.14f, h * 0.6f),
                    CornerRadius(w * 0.05f),
                )
            }
            TimerIcon.Plus, TimerIcon.Minus -> {
                val stroke = w * 0.13f
                drawLine(tint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), stroke, StrokeCap.Round)
                if (icon == TimerIcon.Plus) {
                    drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), stroke, StrokeCap.Round)
                }
            }
            TimerIcon.Restart -> {
                val stroke = w * 0.1f
                val inset = w * 0.18f
                drawArc(
                    color = tint,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(w - 2 * inset, h - 2 * inset),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // Arrow head at the end of the arc (the -60 degree point, upper right).
                val head = Path().apply {
                    moveTo(w * 0.60f, h * 0.06f)
                    lineTo(w * 0.86f, h * 0.20f)
                    lineTo(w * 0.62f, h * 0.36f)
                    close()
                }
                drawPath(head, tint)
            }
        }
    }
}
