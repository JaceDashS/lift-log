package com.example.workouttimer.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class ExerciseTrend(sessions: List<WorkoutSession>) {
    enum class Metric { WEIGHT, REPS, REST }

    data class Point(val date: LocalDate, val dayIndex: Long)

    class Series(val values: List<Double?>) {
        // A zero load or rest is a real measurement, but cannot be a percentage denominator.
        val baseline = values.firstOrNull { it != null && it > 0.0 }
        val hasLeadingZero = baseline != null && values.firstOrNull { it != null } == 0.0
        val changes = values.map { value ->
            when {
                value == null -> null
                baseline == null -> 0.0
                else -> ((value / baseline - 1.0) * 100.0).takeIf { it.isFinite() }
            }
        }
    }

    val points = sessions.map { session ->
        Point(session.date, ChronoUnit.DAYS.between(sessions.first().date, session.date))
    }

    val series = mapOf(
        Metric.WEIGHT to Series(sessions.map { session -> session.sets.map { it.weightKg }.average() }),
        Metric.REPS to Series(sessions.map { WorkoutHistory.averageReps(it.sets) }),
        Metric.REST to Series(sessions.map { session ->
            session.sets.mapNotNull { it.restMillis }.average().takeIf { it.isFinite() }?.div(1000.0)
        }),
    )
}
