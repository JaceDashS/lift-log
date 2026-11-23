package com.example.workouttimer.data

import java.time.LocalDate

/** A completed set. Rest is measured between consecutive sets of the same exercise. */
data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exercise: String,
    val weightKg: Double,
    val reps: Int?,
    val restMillis: Long?,
)

/** One workout on a local calendar date, independent of other workouts on that date. */
data class WorkoutSession(
    val id: Long,
    val date: LocalDate,
    val startedAt: Long,
    val endedAt: Long?,
    val test: Boolean,
    val sets: List<WorkoutSet>,
)

/** A persistent draft; monotonic time keeps REST accurate across backgrounding and clock changes. */
data class SessionDraft(
    val sessionId: Long,
    val pushing: Boolean = false,
    val restStartedWall: Long,
    val restStartedElapsed: Long,
    val boot: Int,
    val frozenRestMillis: Long = 0,
    val exercise: String = "",
    val weight: String = "",
    val reps: String = "",
    val setLogged: Boolean = false,
    val workoutStartedAt: Long? = restStartedWall,
    val previousExercise: String? = null,
    val unloggedSets: Int = 0,
) {
    val isExerciseTransition: Boolean
        get() = previousExercise != null && exercise.isNotBlank() && exercise.trim() != previousExercise

    val recordedRestMillis: Long?
        get() = frozenRestMillis.takeIf { exercise.isNotBlank() && exercise.trim() == previousExercise }

    fun restMillis(wall: Long, elapsed: Long, bootCount: Int): Long = when {
        workoutStartedAt == null -> 0L
        pushing -> frozenRestMillis
        bootCount == boot && elapsed >= restStartedElapsed -> elapsed - restStartedElapsed
        else -> wall - restStartedWall
    }.coerceAtLeast(0)

    fun push(wall: Long, elapsed: Long, bootCount: Int): SessionDraft =
        if (pushing) this else copy(
            pushing = true, frozenRestMillis = restMillis(wall, elapsed, bootCount),
            workoutStartedAt = workoutStartedAt ?: wall, reps = "",
        )

    fun rest(wall: Long, elapsed: Long, bootCount: Int, keepReps: Boolean = false): SessionDraft = copy(
        pushing = false, restStartedWall = wall, restStartedElapsed = elapsed,
        boot = bootCount, frozenRestMillis = 0, reps = if (keepReps) reps else "", setLogged = false,
        previousExercise = exercise.trim().takeIf { it.isNotEmpty() },
        unloggedSets = unloggedSets + if (exercise.isBlank() && !setLogged) 1 else 0,
    )
}

/** Shared validation for real and manually entered test sets; zero weight supports bodyweight sets. */
object SetInput {
    const val KG_PER_POUND = 0.45359237
    const val MAX_REPS = 10000

    fun validExercise(exercise: String): Boolean = exercise.trim().length in 1..100

    fun weightKg(weight: String, pounds: Boolean): Double? =
        weight.trim().replace(',', '.').toDoubleOrNull()
            ?.let { if (pounds) it * KG_PER_POUND else it }
            ?.takeIf { it in 0.0..10000.0 }

    fun valid(exercise: String, weight: String, reps: String): Boolean {
        return validExercise(exercise) && weightKg(weight, pounds = false) != null &&
            repCount(reps) != null
    }

    fun repCount(reps: String): Int? = reps.toIntOrNull()?.takeIf { it in 1..MAX_REPS }

    /** Parses a nonnegative duration as seconds or minutes:seconds. */
    fun restMillis(text: String): Long? {
        val parts = text.trim().split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toLongOrNull()
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toLongOrNull() ?: return null
                if (minutes !in 0..1440 || seconds !in 0..59) return null
                minutes * 60 + seconds
            }
            else -> null
        } ?: return null
        return seconds.takeIf { it in 0..86400 }?.times(1000)
    }
}

/** Exercise history ordered by workout date and start time; each point is one workout average. */
object WorkoutHistory {
    fun sessions(all: List<WorkoutSession>, exercise: String): List<WorkoutSession> =
        all.map { it.copy(sets = it.sets.filter { set -> set.exercise == exercise }) }
            .filter { it.sets.isNotEmpty() }
            .sortedWith(compareBy<WorkoutSession> { it.date }.thenBy { it.startedAt }.thenBy { it.id })

    fun average(sets: List<WorkoutSet>): Double =
        averageReps(sets) ?: 0.0

    fun averageReps(sets: List<WorkoutSet>): Double? =
        sets.mapNotNull { it.reps }.average().takeUnless { it.isNaN() }
}
