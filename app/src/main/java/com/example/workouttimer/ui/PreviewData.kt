package com.example.workouttimer.ui

/**
 * Fixed sample content for the UI preview screens.
 * Nothing here is read from or written to storage.
 */
object PreviewData {

    data class SetEntry(
        val setNo: Int,
        val exercise: String,
        val weightKg: String,
        val reps: Int,
        val rest: String,
    )

    data class ExerciseSummary(
        val name: String,
        val lastDone: String,
        val sessions: Int,
        val avgReps: String,
    )

    /** One set compared with the same set number in the previous session. */
    data class SetComparison(
        val setNo: Int,
        val reps: Int,
        val prevReps: Int,
        val restSec: Int,
        val prevRestSec: Int,
    )

    const val LAST_SESSION_DATE = "Tue, Oct 28"
    const val LAST_SESSION_SUMMARY = "bcpr · sqt · 14 sets · 52 min"

    const val SESSION_REST_TIME = "01:24"
    const val SESSION_PUSH_TIME = "00:00"
    const val SESSION_EXERCISE = "bcpr"
    const val SESSION_WEIGHT_KG = "60"
    const val SESSION_NEXT_SET = 3

    val sessionSets = listOf(
        SetEntry(1, "bcpr", "60", 10, "1:30"),
        SetEntry(2, "bcpr", "60", 9, "1:52"),
    )

    val exercises = listOf(
        ExerciseSummary("bcpr", "Oct 28", 12, "9.4"),
        ExerciseSummary("sqt", "Oct 28", 9, "7.8"),
        ExerciseSummary("dl", "Oct 24", 6, "5.2"),
        ExerciseSummary("ohp", "Oct 21", 4, "8.0"),
    )

    /** Average reps per session; dayIndex counts days from Oct 1. */
    val trend = listOf(
        TrendChartView.Point(0, 8.0f, "Oct 1"),
        TrendChartView.Point(3, 8.5f, ""),
        TrendChartView.Point(10, 8.8f, ""),
        TrendChartView.Point(13, 9.0f, ""),
        TrendChartView.Point(14, 8.6f, ""),
        TrendChartView.Point(20, 9.2f, ""),
        TrendChartView.Point(23, 9.8f, ""),
        TrendChartView.Point(27, 9.4f, "Oct 28"),
    )

    const val COMPARE_SESSIONS = "Oct 28  vs  Oct 24 · 60 kg"

    val comparisons = listOf(
        SetComparison(1, 10, 10, 90, 85),
        SetComparison(2, 9, 10, 112, 90),
        SetComparison(3, 8, 9, 125, 95),
        SetComparison(4, 8, 7, 120, 130),
    )
}
