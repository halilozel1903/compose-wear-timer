package io.github.halilozel1903.weartimer.core

/**
 * Turns the stream of small pixel deltas from a crown or rotating bezel into whole steps.
 * Leftover pixels carry over to the next event; a change of direction or a pause longer than
 * [idleResetMillis] drops them, so a slow turn back never jumps an extra step.
 *
 * @param stepPixels rotation for one step; about one detent on a Pixel Watch crown is 48 px.
 */
public class RotaryStepAccumulator(
    public val stepPixels: Float = DEFAULT_STEP_PIXELS,
    public val idleResetMillis: Long = DEFAULT_IDLE_RESET_MILLIS,
) {
    init {
        require(stepPixels > 0f) { "stepPixels must be positive, was $stepPixels" }
    }

    private var remainder = 0f
    private var lastEventMillis: Long? = null

    /** Adds one rotary event and returns the whole steps it completes; clockwise is positive. */
    public fun accumulate(deltaPixels: Float, eventMillis: Long): Int {
        val last = lastEventMillis
        if (last != null && eventMillis - last > idleResetMillis) remainder = 0f
        if (remainder != 0f && deltaPixels != 0f && (remainder > 0f) != (deltaPixels > 0f)) remainder = 0f
        lastEventMillis = eventMillis
        remainder += deltaPixels
        val steps = (remainder / stepPixels).toInt()
        remainder -= steps * stepPixels
        return steps
    }

    /** Forgets leftover rotation. */
    public fun reset() {
        remainder = 0f
        lastEventMillis = null
    }

    public companion object {
        public const val DEFAULT_STEP_PIXELS: Float = 48f
        public const val DEFAULT_IDLE_RESET_MILLIS: Long = 300L
    }
}

/**
 * Maps crown steps to durations with steps that grow with the value, so a turn of the crown is
 * precise for short intervals and quick for long ones. Values snap to the grid of their tier.
 *
 * The [default] stepper uses 5 s steps up to 1 minute, 15 s up to 5 minutes, 30 s up to 20 minutes,
 * 1 minute up to an hour and 5 minutes above.
 *
 * ```kotlin
 * val stepper = DurationStepper.default()
 * stepper.step(55_000, steps = 1)    // 60_000  (0:55 -> 1:00)
 * stepper.step(60_000, steps = 1)    // 75_000  (1:00 -> 1:15)
 * stepper.step(60_000, steps = -1)   // 55_000  (1:00 -> 0:55)
 * ```
 */
public class DurationStepper(
    public val minMillis: Long,
    public val maxMillis: Long,
    public val tiers: List<Tier>,
) {
    /** Values below [upToMillis] (exclusive) move in steps of [stepMillis]. The last tier covers the rest. */
    public data class Tier(val upToMillis: Long, val stepMillis: Long) {
        init {
            require(stepMillis > 0) { "stepMillis must be positive, was $stepMillis" }
        }
    }

    init {
        require(minMillis >= 0) { "minMillis must not be negative" }
        require(maxMillis > minMillis) { "maxMillis must be larger than minMillis" }
        require(tiers.isNotEmpty()) { "At least one tier is needed" }
        require(tiers.zipWithNext().all { (a, b) -> a.upToMillis < b.upToMillis }) {
            "Tiers must be sorted by upToMillis"
        }
    }

    /** The step size used at [valueMillis]. */
    public fun stepAt(valueMillis: Long): Long =
        (tiers.firstOrNull { valueMillis < it.upToMillis } ?: tiers.last()).stepMillis

    /** Moves [valueMillis] by [steps] (positive is longer), snapping to the grid and clamping. */
    public fun step(valueMillis: Long, steps: Int): Long {
        var value = valueMillis.coerceIn(minMillis, maxMillis)
        repeat(kotlin.math.abs(steps)) {
            value = if (steps > 0) {
                val step = stepAt(value)
                (value / step + 1) * step
            } else {
                // The step size just below the value, so 1:00 goes back to 0:55 and not 0:45.
                val step = stepAt((value - 1).coerceAtLeast(0L))
                ((value - 1).coerceAtLeast(0L) / step) * step
            }
            value = value.coerceIn(minMillis, maxMillis)
        }
        return value
    }

    /** Rounds [valueMillis] to the nearest grid point of its tier and clamps it. */
    public fun snap(valueMillis: Long): Long {
        val value = valueMillis.coerceIn(minMillis, maxMillis)
        val step = stepAt(value)
        val snapped = ((value + step / 2) / step) * step
        return snapped.coerceIn(minMillis, maxMillis)
    }

    /** Where [valueMillis] sits between [minMillis] and [maxMillis], `0..1`, for a picker ring. */
    public fun fraction(valueMillis: Long): Float =
        ((valueMillis - minMillis).toFloat() / (maxMillis - minMillis)).coerceIn(0f, 1f)

    public companion object {
        /** 5 s to 3 hours, with steps of 5 s, 15 s, 30 s, 1 min and 5 min. */
        public fun default(minMillis: Long = 5_000L, maxMillis: Long = 3 * 3_600_000L): DurationStepper =
            DurationStepper(
                minMillis = minMillis,
                maxMillis = maxMillis,
                tiers = listOf(
                    Tier(upToMillis = 60_000L, stepMillis = 5_000L),
                    Tier(upToMillis = 5 * 60_000L, stepMillis = 15_000L),
                    Tier(upToMillis = 20 * 60_000L, stepMillis = 30_000L),
                    Tier(upToMillis = 60 * 60_000L, stepMillis = 60_000L),
                    Tier(upToMillis = Long.MAX_VALUE, stepMillis = 5 * 60_000L),
                ),
            )

        /** Fixed steps of [stepMillis], for example whole minutes for a kitchen timer. */
        public fun uniform(stepMillis: Long, minMillis: Long, maxMillis: Long): DurationStepper =
            DurationStepper(minMillis, maxMillis, listOf(Tier(Long.MAX_VALUE, stepMillis)))
    }
}
