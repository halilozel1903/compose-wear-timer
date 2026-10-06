package io.github.halilozel1903.weartimer.core

/**
 * Something the user should feel or hear: the last seconds of a phase, a new phase, the end.
 * [atMillis] is the elapsed workout time it belongs to.
 */
public sealed interface TimerAlert {
    public val atMillis: Long

    /** Higher wins when several alerts arrive at once; see [TimerAlerts.mostImportant]. */
    public val priority: Int

    /** "3", "2", "1": [secondsLeft] seconds before [phase] ends. */
    public data class CountdownTick(
        val secondsLeft: Int,
        val phase: WorkoutPhase,
        override val atMillis: Long,
    ) : TimerAlert {
        override val priority: Int get() = 1
    }

    /** [from] ended and [to] begins. */
    public data class PhaseChanged(
        val from: WorkoutPhase,
        val to: WorkoutPhase,
        override val atMillis: Long,
    ) : TimerAlert {
        override val priority: Int get() = 2
    }

    /** The last phase of [plan] ended. */
    public data class Finished(
        val plan: WorkoutPlan,
        override val atMillis: Long,
    ) : TimerAlert {
        override val priority: Int get() = 3
    }
}

/** Finding alerts on a workout's timeline. */
public object TimerAlerts {
    /** Ticks for the last 3, 2 and 1 seconds of each phase by default. */
    public const val DEFAULT_COUNTDOWN_SECONDS: Int = 3

    /**
     * Every alert of [plan] with a time in `(fromMillis, toMillis]`, in time order. Countdown ticks
     * fall strictly inside a phase, so a 2 second phase only gets "1". Each phase end gives a
     * [TimerAlert.PhaseChanged], the last one a [TimerAlert.Finished].
     */
    public fun between(
        plan: WorkoutPlan,
        fromMillis: Long,
        toMillis: Long,
        countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
    ): List<TimerAlert> {
        require(countdownSeconds >= 0) { "countdownSeconds must not be negative" }
        if (toMillis <= fromMillis) return emptyList()
        val alerts = mutableListOf<TimerAlert>()
        val first = plan.phaseIndexAt(fromMillis.coerceAtLeast(0L))
        for (index in first..plan.phases.lastIndex) {
            val start = plan.phaseStarts[index]
            if (start > toMillis) break
            val phase = plan.phases[index]
            val end = plan.phaseEnd(index)
            for (seconds in countdownSeconds downTo 1) {
                val at = end - seconds * 1_000L
                if (at > start && at > fromMillis && at <= toMillis) {
                    alerts += TimerAlert.CountdownTick(seconds, phase, at)
                }
            }
            if (end > fromMillis && end <= toMillis) {
                val next = plan.phases.getOrNull(index + 1)
                alerts += if (next == null) {
                    TimerAlert.Finished(plan, end)
                } else {
                    TimerAlert.PhaseChanged(phase, next, end)
                }
            }
        }
        return alerts
    }

    /**
     * The one alert worth playing from a batch, for example after the app was asleep and several
     * piled up: the highest [TimerAlert.priority], the latest among equals. Null for an empty list.
     */
    public fun mostImportant(alerts: List<TimerAlert>): TimerAlert? =
        alerts.maxWithOrNull(compareBy<TimerAlert>({ it.priority }, { it.atMillis }))
}

/**
 * Remembers how far alerts have been reported, so each alert is reported once while the workout
 * is polled at any rate. Jumps (skip, reset, restore) go through [jumpTo] and report nothing.
 */
public class AlertTracker(
    public val countdownSeconds: Int = TimerAlerts.DEFAULT_COUNTDOWN_SECONDS,
    startMillis: Long = 0L,
) {
    /** Elapsed workout time up to which alerts were reported. */
    public var reportedUpToMillis: Long = startMillis
        private set

    /** Alerts between the last call and [elapsedMillis]. Going backwards reports nothing. */
    public fun advance(plan: WorkoutPlan, elapsedMillis: Long): List<TimerAlert> {
        val from = reportedUpToMillis
        reportedUpToMillis = elapsedMillis
        return TimerAlerts.between(plan, from, elapsedMillis, countdownSeconds)
    }

    /** Moves to [elapsedMillis] without reporting anything in between. */
    public fun jumpTo(elapsedMillis: Long) {
        reportedUpToMillis = elapsedMillis
    }
}
