package io.github.halilozel1903.weartimer.core

/** Formats durations for small screens and screen readers. Negative values count as zero. */
public object TimeFormat {
    /**
     * `m:ss` below an hour, `h:mm:ss` above, with whole seconds rounded down: `0:05`, `12:34`,
     * `1:02:03`. Use it for elapsed time.
     */
    public fun clock(millis: Long): String = clockOfSeconds(millis.coerceAtLeast(0L) / 1_000L)

    /**
     * Like [clock], but rounds up to the next whole second, the way countdowns read: with 0.4 s left
     * it still shows `0:01`, and `0:00` only once the time is up.
     */
    public fun countdown(remainingMillis: Long): String {
        val millis = remainingMillis.coerceAtLeast(0L)
        return clockOfSeconds((millis + 999L) / 1_000L)
    }

    /** Short form for lists and summaries: `45s`, `4m`, `4m 30s`, `1h 5m`. */
    public fun compact(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            minutes > 0 -> if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /** For content descriptions: `4 minutes 30 seconds`, `1 hour`, `0 seconds`. */
    public fun spoken(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        val parts = buildList<String> {
            if (hours > 0) add(plural(hours, "hour"))
            if (minutes > 0) add(plural(minutes, "minute"))
            if (seconds > 0 || isEmpty()) add(plural(seconds, "second"))
        }
        return parts.joinToString(" ")
    }

    private fun clockOfSeconds(totalSeconds: Long): String {
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
        } else {
            "$minutes:${seconds.twoDigits()}"
        }
    }

    private fun Long.twoDigits(): String = toString().padStart(2, '0')

    private fun plural(count: Long, unit: String): String = if (count == 1L) "1 $unit" else "$count ${unit}s"
}
