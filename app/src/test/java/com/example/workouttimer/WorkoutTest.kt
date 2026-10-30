package com.example.workouttimer

import com.example.workouttimer.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorkoutTest {
    private fun draft() = SessionDraft(1, restStartedWall = 100000, restStartedElapsed = 5000, boot = 3)

    @Test fun restUsesMonotonicTimeAndPushFreezesIt() {
        val rest = draft()
        assertEquals(90000L, rest.restMillis(100, 95000, 3))
        val push = rest.push(100, 95000, 3)
        assertEquals(90000L, push.restMillis(1000000, 500000, 3))
        assertEquals(push, push.push(1000000, 500000, 3))
    }

    @Test fun rebootFallsBackToWallTimeWithoutNegativeDuration() {
        assertEquals(10000L, draft().restMillis(110000, 100, 4))
        assertEquals(0L, draft().restMillis(90000, 100, 4))
    }

    @Test fun restKeepsExerciseAndWeightButClearsRepetitions() {
        val next = draft().copy(exercise = "bcpr", weight = "60", reps = "10")
            .push(110000, 15000, 3).rest(120000, 25000, 3)
        assertFalse(next.pushing)
        assertEquals("bcpr", next.exercise)
        assertEquals("60", next.weight)
        assertEquals("", next.reps)
        assertEquals(1000L, next.restMillis(121000, 26000, 3))
    }

    @Test fun validatesUserInputAndRestDurations() {
        assertTrue(SetInput.valid("my exercise", "0", "10"))
        for (weight in listOf("", "-1", "NaN", "Infinity", "10001")) assertFalse(SetInput.valid("x", weight, "10"))
        for (reps in listOf("", "0", "-1", "2.5", "10001")) assertFalse(SetInput.valid("x", "60", reps))
        assertFalse(SetInput.valid("  ", "60", "10"))
        assertEquals(90000L, SetInput.restMillis("1:30"))
        assertEquals(90000L, SetInput.restMillis("90"))
        for (rest in listOf("-1", "1:60", "9999999999999999", "1:2:3")) assertNull(SetInput.restMillis(rest))
    }

    @Test fun historySortsDatesAndKeepsSameDayWorkoutsSeparate() {
        fun session(id: Long, day: String, reps: Int) = WorkoutSession(id, LocalDate.parse(day), id, id, false,
            listOf(WorkoutSet(sessionId = id, exercise = "bcpr", weightKg = 60.0, reps = reps, restMillis = 90000)))
        val history = WorkoutHistory.sessions(listOf(session(3, "2026-10-30", 9), session(1, "2026-10-23", 10), session(2, "2026-10-30", 8)), "bcpr")
        assertEquals(listOf(1L, 2L, 3L), history.map { it.id })
        assertEquals(9.0, WorkoutHistory.average(history.last().sets), 0.0)
        assertTrue(WorkoutHistory.sessions(history, "sqt").isEmpty())
    }
}
