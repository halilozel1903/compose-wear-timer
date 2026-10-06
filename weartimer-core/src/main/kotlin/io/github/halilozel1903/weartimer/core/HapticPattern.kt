package io.github.halilozel1903.weartimer.core

/**
 * A vibration waveform in the shape Android's `VibrationEffect.createWaveform(timings, amplitudes, -1)`
 * expects: [timings] alternate off and on durations in milliseconds, starting with an off delay, and
 * [amplitudes] holds the strength (0 to 255) of each segment, 0 for the off ones.
 */
public data class HapticPattern(
    val timings: List<Long>,
    val amplitudes: List<Int>,
) {
    init {
        require(timings.isNotEmpty()) { "A pattern needs at least one segment" }
        require(timings.size == amplitudes.size) { "timings and amplitudes must have the same size" }
        require(timings.all { it >= 0 }) { "timings must not be negative" }
        require(amplitudes.all { it in 0..255 }) { "amplitudes must be in 0..255" }
    }

    /** How long the pattern takes. */
    public val durationMillis: Long get() = timings.sum()

    public companion object {
        /** Pulses of the given lengths separated by [gapMillis] pauses: `pulses(120, 120, gapMillis = 80)`. */
        public fun pulses(vararg pulseMillis: Long, gapMillis: Long = 90L, amplitude: Int = 255): HapticPattern {
            require(pulseMillis.isNotEmpty()) { "At least one pulse is needed" }
            val timings = buildList<Long> {
                pulseMillis.forEachIndexed { index, pulse ->
                    add(if (index == 0) 0L else gapMillis)
                    add(pulse)
                }
            }
            val amplitudes = buildList<Int> {
                repeat(pulseMillis.size) {
                    add(0)
                    add(amplitude)
                }
            }
            return HapticPattern(timings, amplitudes)
        }
    }
}

/**
 * The default feel of each [TimerAlert], distinct enough to tell apart without looking at the watch:
 * a short soft tick for 3-2-1, two strong pulses when work starts, one long soft pulse for rest,
 * and three long pulses at the end.
 */
public object HapticPatterns {
    public val Tick: HapticPattern = HapticPattern.pulses(40L, amplitude = 140)
    public val WorkStarts: HapticPattern = HapticPattern.pulses(120L, 120L, gapMillis = 80L)
    public val RestStarts: HapticPattern = HapticPattern.pulses(350L, amplitude = 160)
    public val PhaseStarts: HapticPattern = HapticPattern.pulses(200L, amplitude = 220)
    public val Finished: HapticPattern = HapticPattern.pulses(400L, 400L, 600L, gapMillis = 150L)

    /** The pattern for [alert]. */
    public fun forAlert(alert: TimerAlert): HapticPattern = when (alert) {
        is TimerAlert.CountdownTick -> Tick
        is TimerAlert.PhaseChanged -> when (alert.to.kind) {
            PhaseKind.Work -> WorkStarts
            PhaseKind.Rest -> RestStarts
            PhaseKind.WarmUp, PhaseKind.CoolDown -> PhaseStarts
        }
        is TimerAlert.Finished -> Finished
    }
}
