package com.example.workouttimer.data

import java.time.LocalDate

/** A completed set. Rest is the pause immediately before this set. */
data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exercise: String,
    val weightKg: Double,
    val reps: Int,
    val restMillis: Long,
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
) {
    fun restMillis(wall: Long, elapsed: Long, bootCount: Int): Long = when {
        pushing -> frozenRestMillis
        bootCount == boot && elapsed >= restStartedElapsed -> elapsed - restStartedElapsed
        else -> wall - restStartedWall
    }.coerceAtLeast(0)

    fun push(wall: Long, elapsed: Long, bootCount: Int): SessionDraft =
        if (pushing) this else copy(
            pushing = true, frozenRestMillis = restMillis(wall, elapsed, bootCount)
        )

    fun rest(wall: Long, elapsed: Long, bootCount: Int): SessionDraft = copy(
        pushing = false, restStartedWall = wall, restStartedElapsed = elapsed,
        boot = bootCount, frozenRestMillis = 0, reps = ""
    )
}

/** Shared validation for real and manually entered test sets; zero weight supports bodyweight sets. */
object SetInput {
    fun valid(exercise: String, weight: String, reps: String): Boolean {
        val kg = weight.toDoubleOrNull()
        val count = reps.toIntOrNull()
        return exercise.trim().isNotEmpty() && exercise.trim().length <= 100 &&
            kg != null && kg.isFinite() && kg >= 0 && kg <= 10000 &&
            count != null && count in 1..10000
    }

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
        if (sets.isEmpty()) 0.0 else sets.map { it.reps }.average()
}
