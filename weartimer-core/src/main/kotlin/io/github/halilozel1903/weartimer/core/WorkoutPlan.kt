package io.github.halilozel1903.weartimer.core

/** What a phase of a workout is for. Apps usually give each kind its own color. */
public enum class PhaseKind(public val defaultLabel: String) {
    WarmUp("Warm-up"),
    Work("Work"),
    Rest("Rest"),
    CoolDown("Cool-down"),
}

/**
 * One timed block of a workout.
 *
 * @param round the work/rest round this phase belongs to, starting at 1; 0 for warm-up and cool-down.
 * @param label the name shown on screen; defaults to the kind's [PhaseKind.defaultLabel].
 */
public data class WorkoutPhase(
    val kind: PhaseKind,
    val durationMillis: Long,
    val round: Int = 0,
    val label: String = kind.defaultLabel,
) {
    init {
        require(durationMillis > 0) { "A phase must last longer than 0 ms, was $durationMillis" }
        require(round >= 0) { "round must not be negative, was $round" }
    }
}

/**
 * An ordered list of [phases] with a [name]: a plain countdown is one phase, an interval workout is a
 * warm-up, rounds of work and rest, and a cool-down. Build interval plans with [intervals] or [tabata].
 *
 * All positions are "elapsed workout time" in milliseconds, from 0 to [totalMillis].
 */
public data class WorkoutPlan(
    val name: String,
    val phases: List<WorkoutPhase>,
) {
    init {
        require(phases.isNotEmpty()) { "A workout needs at least one phase" }
    }

    /** Start of each phase in elapsed workout time; `phaseStarts[0] == 0`. */
    public val phaseStarts: List<Long> = buildList<Long> {
        var start = 0L
        phases.forEach { phase ->
            add(start)
            start += phase.durationMillis
        }
    }

    /** Length of the whole workout. */
    public val totalMillis: Long = phases.sumOf { it.durationMillis }

    /** Number of work/rest rounds (the highest [WorkoutPhase.round]); 0 for plans without rounds. */
    public val totalRounds: Int = phases.maxOf { it.round }

    /** Time spent in [PhaseKind.Work] phases. */
    public val workMillis: Long = phases.filter { it.kind == PhaseKind.Work }.sumOf { it.durationMillis }

    /** End of phase [index] in elapsed workout time. */
    public fun phaseEnd(index: Int): Long = phaseStarts[index] + phases[index].durationMillis

    /**
     * Index of the phase running at [elapsedMillis]. A phase owns its start and not its end, so at a
     * boundary the next phase is current. At or past the end it is the last phase.
     */
    public fun phaseIndexAt(elapsedMillis: Long): Int {
        if (elapsedMillis <= 0L) return 0
        if (elapsedMillis >= totalMillis) return phases.lastIndex
        // Binary search for the last start at or before elapsedMillis.
        var low = 0
        var high = phases.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (phaseStarts[mid] <= elapsedMillis) low = mid else high = mid - 1
        }
        return low
    }

    /** Where the workout is after [elapsedMillis]: phase, round, remaining time and progress. */
    public fun snapshotAt(elapsedMillis: Long): WorkoutSnapshot {
        val elapsed = elapsedMillis.coerceIn(0L, totalMillis)
        val index = phaseIndexAt(elapsed)
        val phase = phases[index]
        val phaseElapsed = (elapsed - phaseStarts[index]).coerceIn(0L, phase.durationMillis)
        val finished = elapsed >= totalMillis
        val round = when (phase.kind) {
            PhaseKind.WarmUp -> 0
            PhaseKind.CoolDown -> totalRounds
            PhaseKind.Work, PhaseKind.Rest -> phase.round
        }
        return WorkoutSnapshot(
            phaseIndex = index,
            phase = phase,
            nextPhase = phases.getOrNull(index + 1),
            phaseElapsedMillis = phaseElapsed,
            phaseRemainingMillis = phase.durationMillis - phaseElapsed,
            elapsedMillis = elapsed,
            totalMillis = totalMillis,
            round = round,
            totalRounds = totalRounds,
            isFinished = finished,
        )
    }

    public companion object {
        /** A one phase plan: a plain countdown of [durationMillis]. */
        public fun countdown(durationMillis: Long, name: String = "Timer"): WorkoutPlan =
            WorkoutPlan(name, listOf(WorkoutPhase(PhaseKind.Work, durationMillis, round = 0, label = name)))

        /**
         * Warm-up, [rounds] of work and rest, cool-down. Zero length warm-up, rest or cool-down phases
         * are left out.
         *
         * @param restAfterLastRound adds a rest after the last work phase too (before the cool-down).
         */
        public fun intervals(
            name: String,
            workMillis: Long,
            restMillis: Long,
            rounds: Int,
            warmUpMillis: Long = 0L,
            coolDownMillis: Long = 0L,
            restAfterLastRound: Boolean = false,
        ): WorkoutPlan {
            require(rounds >= 1) { "rounds must be at least 1, was $rounds" }
            require(workMillis > 0) { "workMillis must be positive, was $workMillis" }
            require(restMillis >= 0 && warmUpMillis >= 0 && coolDownMillis >= 0) {
                "rest, warm-up and cool-down must not be negative"
            }
            val phases = buildList<WorkoutPhase> {
                if (warmUpMillis > 0) add(WorkoutPhase(PhaseKind.WarmUp, warmUpMillis))
                for (round in 1..rounds) {
                    add(WorkoutPhase(PhaseKind.Work, workMillis, round))
                    if (restMillis > 0 && (round < rounds || restAfterLastRound)) {
                        add(WorkoutPhase(PhaseKind.Rest, restMillis, round))
                    }
                }
                if (coolDownMillis > 0) add(WorkoutPhase(PhaseKind.CoolDown, coolDownMillis))
            }
            return WorkoutPlan(name, phases)
        }

        /** The classic Tabata protocol: 8 rounds of 20 s work and 10 s rest. */
        public fun tabata(
            rounds: Int = 8,
            workMillis: Long = 20_000L,
            restMillis: Long = 10_000L,
            warmUpMillis: Long = 0L,
            coolDownMillis: Long = 0L,
        ): WorkoutPlan = intervals(
            name = "Tabata",
            workMillis = workMillis,
            restMillis = restMillis,
            rounds = rounds,
            warmUpMillis = warmUpMillis,
            coolDownMillis = coolDownMillis,
        )
    }
}

/** Where a workout stands at one moment; see [WorkoutPlan.snapshotAt]. */
public data class WorkoutSnapshot(
    val phaseIndex: Int,
    val phase: WorkoutPhase,
    val nextPhase: WorkoutPhase?,
    val phaseElapsedMillis: Long,
    val phaseRemainingMillis: Long,
    val elapsedMillis: Long,
    val totalMillis: Long,
    /** Current round, 1 based; 0 during the warm-up and the total during the cool-down. */
    val round: Int,
    val totalRounds: Int,
    val isFinished: Boolean,
) {
    /** Share of the current phase that has elapsed, `0..1`. */
    public val phaseProgress: Float get() = phaseElapsedMillis.toFloat() / phase.durationMillis

    /** Share of the current phase that is left, `1..0`: what a depleting ring shows. */
    public val phaseRemainingFraction: Float get() = 1f - phaseProgress

    /** Share of the whole workout that has elapsed, `0..1`. */
    public val totalProgress: Float get() = elapsedMillis.toFloat() / totalMillis

    /** Time left in the whole workout. */
    public val remainingMillis: Long get() = totalMillis - elapsedMillis
}
