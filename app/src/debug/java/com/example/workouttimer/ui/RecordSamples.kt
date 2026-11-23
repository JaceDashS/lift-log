package com.example.workouttimer.ui

import com.example.workouttimer.data.WorkoutSession
import com.example.workouttimer.data.WorkoutSet
import java.time.LocalDate
import java.time.ZoneId

private data class Sample(val daysAgo: Long, val weight: Double, val reps: Int?, val restSeconds: Long)

internal fun recordSamples(today: LocalDate = LocalDate.now()): List<WorkoutSession>? {
    val exercises = mapOf(
        "Bench Press" to listOf(
            Sample(34, 40.0, 8, 75), Sample(30, 42.5, 8, 95), Sample(23, 42.5, 10, 80),
            Sample(19, 45.0, 9, 105), Sample(13, 47.5, null, 100), Sample(10, 45.0, 11, 85),
            Sample(4, 50.0, 9, 115), Sample(1, 52.5, 10, 90), Sample(1, 50.0, 12, 80),
        ),
        "Squat" to listOf(
            Sample(32, 55.0, 8, 95), Sample(27, 60.0, 7, 110), Sample(22, 60.0, 9, 110),
            Sample(17, 62.5, 8, 90), Sample(11, 65.0, 8, 120), Sample(6, 67.5, 9, 105),
            Sample(2, 70.0, 8, 110),
        ),
        "Lat Pulldown" to listOf(
            Sample(33, 35.0, 10, 55), Sample(29, 37.5, 9, 65), Sample(24, 37.5, 12, 60),
            Sample(18, 40.0, 10, 75), Sample(12, 42.5, 9, 70), Sample(7, 42.5, 11, 65),
            Sample(2, 45.0, 10, 80),
        ),
    )
    val zone = ZoneId.systemDefault()
    return exercises.entries.flatMapIndexed { exerciseIndex, (exercise, workouts) ->
        workouts.mapIndexed { index, sample ->
            val id = -(exerciseIndex * 100L + index + 2)
            val date = today.minusDays(sample.daysAgo)
            val start = date.atTime(8 + index, 0).atZone(zone).toInstant().toEpochMilli()
            val sets = List(3) { set ->
                WorkoutSet(
                    id = id * 10 - set,
                    sessionId = id,
                    exercise = exercise,
                    weightKg = sample.weight - set * 2.5,
                    reps = sample.reps?.minus(set),
                    restMillis = if (set == 0) null else (sample.restSeconds + set * 15L) * 1000,
                )
            }
            WorkoutSession(id, date, start, start + 540_000, test = true, sets = sets)
        }
    }
}
