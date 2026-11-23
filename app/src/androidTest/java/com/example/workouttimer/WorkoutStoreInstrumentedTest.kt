package com.example.workouttimer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttimer.data.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.time.LocalDate
import java.util.UUID

/** Runs against a unique database per test, never the installed user's workout database. */
@RunWith(AndroidJUnit4::class)
class WorkoutStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-${UUID.randomUUID()}.db"
    private lateinit var store: WorkoutStore

    @Before fun createStore() { store = WorkoutStore(context, name) }
    @After fun closeStore() {
        store.close()
        context.deleteDatabase(name)
        context.getSharedPreferences("$name.settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun push(test: Boolean = false, reps: String = "10"): SessionDraft {
        val draft = store.start(test).copy(exercise = "bcpr", weight = "60")
            .push(System.currentTimeMillis(), SystemClock.elapsedRealtime(), store.bootCount()).copy(reps = reps)
        store.saveDraft(draft, test)
        return draft
    }

    @Test fun savesExactlyOnceAndRestoresDraftAfterDatabaseReopen() {
        val draft = push()
        val next = store.completeSet(draft, false)
        assertEquals("", next.reps)
        assertEquals("bcpr", next.exercise)
        assertEquals("60", next.weight)
        try { store.completeSet(draft, false); fail("Stale PUSH must not duplicate a set") }
        catch (_: IllegalStateException) { }
        store.close()
        store = WorkoutStore(context, name)
        assertEquals(next, store.active(false))
        assertEquals(1, store.sessions(false).single().sets.size)
        assertEquals(10, store.sessions(false).single().sets.single().reps)
        assertNull(store.sessions(false).single().sets.single().restMillis)
    }

    @Test fun draftRestoresUnfinishedInputsAndFrozenTimer() {
        val draft = push()
        store.close()
        store = WorkoutStore(context, name)
        assertEquals(draft, store.active(false))
        assertEquals(draft.frozenRestMillis, store.active(false)!!.restMillis(Long.MAX_VALUE, Long.MAX_VALUE, -1))
    }

    @Test fun readyDraftAndButtonOpacitySurviveReopen() {
        val ready = store.start(false, ready = true)
        store.setButtonOpacity(55)
        store.close()
        store = WorkoutStore(context, name)
        assertEquals(ready, store.active(false))
        assertNull(store.active(false)?.workoutStartedAt)
        assertEquals(55, store.buttonOpacity.value)

        val started = ready.push(ready.restStartedWall + 10_000, 25000, store.bootCount())
        assertTrue(store.replaceDraft(ready, started, false))
        assertEquals(started, store.active(false))
        assertEquals(started.workoutStartedAt, store.sessions(false).single().startedAt)
    }

    @Test fun invalidSetDoesNotAdvanceStateOrWriteHistory() {
        val draft = push(reps = "0")
        try { store.completeSet(draft, false); fail("Invalid set must fail") }
        catch (_: IllegalArgumentException) { }
        assertEquals(draft, store.active(false))
        assertTrue(store.sessions(false).single().sets.isEmpty())
    }

    @Test fun testDeletionDoesNotTouchRealHistoryOrDraft() {
        store.completeSet(push(), false)
        val realDraft = store.active(false)
        val realHistory = store.sessions(false)
        store.completeSet(push(true), true)
        store.addTestSet(LocalDate.of(2026, 10, 1), "bcpr", "50", "9", 90000)
        store.fillTestData()
        assertTrue(store.sessions(true).flatMap { it.sets }.size >= 92)
        store.clearTestData()
        assertTrue(store.sessions(true).isEmpty())
        assertNull(store.active(true))
        assertEquals(realHistory, store.sessions(false))
        assertEquals(realDraft, store.active(false))
    }

    @Test fun manualTestSetsGroupByDayAndHistoryUsesActualAverages() {
        val first = LocalDate.of(2026, 10, 1)
        store.addTestSet(first, "bcpr", "60", "10", 90000)
        store.addTestSet(first, "bcpr", "60", "8", 100000)
        store.addTestSet(first, "sqt", "80", "5", 120000)
        store.addTestSet(first.plusDays(7), "bcpr", "60", "9", 112000)
        val sessions = store.sessions(true)
        assertEquals(2, sessions.size)
        val history = WorkoutHistory.sessions(sessions, "bcpr")
        assertEquals(2, history.first().sets.size)
        assertEquals(9.0, WorkoutHistory.average(history.first().sets), 0.0)
        assertEquals(112000L, history.last().sets.single().restMillis)
        assertTrue(store.sessions(false).isEmpty())
    }

    @Test fun endingEmptyWorkoutRemovesItAndEndingSavedWorkoutKeepsSets() {
        store.start(false)
        store.end(false)
        assertNull(store.active(false))
        assertTrue(store.sessions(false).isEmpty())
        store.completeSet(push(), false)
        store.end(false)
        assertNull(store.active(false))
        assertNotNull(store.sessions(false).single().endedAt)
        assertEquals(1, store.sessions(false).single().sets.size)
    }

    @Test fun preferencesAndDatasetsSurviveReopen() {
        store.testMode = true
        assertTrue(store.perDay)
        store.perDay = false
        push(false)
        push(true)
        store.close()
        store = WorkoutStore(context, name)
        assertTrue(store.testMode)
        assertFalse(store.perDay)
        assertNotEquals(store.active(false)!!.sessionId, store.active(true)!!.sessionId)
        assertEquals(store.active(true), store.start())
    }

    @Test fun missingRepsKeepsSetInProgressAndStaleInputsCannotDuplicateSavedSet() {
        store.addExercise(" bench ")
        val pushing = push(reps = "").copy(exercise = "bench", weight = "")
        store.saveDraft(pushing, false)
        try { store.startRest(pushing, false); fail("Expected missing reps to block rest") }
        catch (_: IllegalArgumentException) { }
        assertTrue(store.sessions(false).single().sets.isEmpty())
        assertEquals(pushing, store.active(false))
        val logged = store.startRest(pushing, false, reps = "5", weight = "62.5")
        assertFalse(logged.pushing)
        assertFalse(logged.setLogged)
        store.close()
        store = WorkoutStore(context, name)
        assertEquals(logged, store.active(false))
        assertEquals(listOf("Bench Press", "Squat", "Lat Pulldown", "bench"), store.exercises(false))
        assertEquals(5, store.sessions(false).single().sets.single().reps)
        assertEquals(62.5, store.sessions(false).single().sets.single().weightKg, 0.0)
        assertFalse(store.replaceDraft(pushing, pushing.copy(weight = "80"), false))
        try { store.startRest(pushing, false, reps = "5", weight = "62.5"); fail("Stale PUSH must not duplicate a set") }
        catch (_: IllegalStateException) { }
        assertEquals(1, store.sessions(false).single().sets.size)
    }

    @Test fun migratesExistingSetsAndDraftToAllowMissingRest() {
        val oldDatabase = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(oldDatabase, null).use { db ->
            db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY, day TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER, test INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sets (id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, exercise TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL CHECK(reps > 0), rest INTEGER NOT NULL CHECK(rest >= 0))")
            db.execSQL("CREATE INDEX sets_session ON sets(session_id)")
            db.execSQL("CREATE TABLE drafts (test INTEGER PRIMARY KEY, payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO sessions VALUES (1, '2026-10-30', 100, NULL, 0)")
            db.execSQL("INSERT INTO sets VALUES (4, 1, 'bench', 60, 9, 15000)")
            db.execSQL("INSERT INTO drafts VALUES (0, ?)", arrayOf(
                """{"session":1,"pushing":true,"wall":100,"elapsed":100,"boot":1,"frozen":14000,"exercise":"bench","weight":"60","reps":""}"""
            ))
            db.version = 1
        }
        val draft = requireNotNull(store.active(false))
        assertFalse(draft.setLogged)
        val completed = draft.copy(reps = "7")
        assertTrue(store.replaceDraft(draft, completed, false))
        store.startRest(completed, false)
        val sets = store.sessions(false).single().sets
        assertEquals(listOf(9, 7), sets.map { it.reps })
        assertEquals(listOf(15000L, null), sets.map { it.restMillis })
        assertEquals(4L, sets.first().id)
        assertEquals(8.0, requireNotNull(WorkoutHistory.averageReps(sets)), 0.0)
    }

    @Test fun recordsOnlyRestBetweenConsecutiveSetsOfTheSameExercise() {
        var resting = store.startRest(push(), false)
        store.close()
        store = WorkoutStore(context, name)
        assertEquals("bcpr", store.active(false)?.previousExercise)
        for (exercise in listOf("bcpr", "squat", "squat", "", "squat")) {
            val pushing = resting.copy(exercise = exercise)
                .push(resting.restStartedWall + 12000, resting.restStartedElapsed + 12000, store.bootCount())
            assertEquals("", pushing.reps)
            assertEquals("60", pushing.weight)
            val completed = pushing.copy(reps = "8")
            assertTrue(store.replaceDraft(resting, completed, false))
            resting = store.startRest(completed, false)
        }
        val sets = store.sessions(false).single().sets
        assertEquals(listOf(null, 12000L, null, 12000L, null), sets.map { it.restMillis })
        val trend = ExerciseTrend(WorkoutHistory.sessions(store.sessions(false), "squat"))
        assertEquals(listOf(12.0), trend.series.getValue(ExerciseTrend.Metric.REST).values)
    }

    @Test fun startingRestUsesLatestInputsAndSkipsUnselectedOrPreviouslySavedSets() {
        val pushing = push()
        val updated = pushing.copy(exercise = "overhead press", weight = "42.5", reps = "4")
        assertTrue(store.replaceDraft(pushing, updated, false))
        val resting = store.startRest(updated, false)
        assertEquals("4", resting.reps)
        val set = store.sessions(false).single().sets.single()
        assertEquals("overhead press", set.exercise)
        assertEquals(42.5, set.weightKg, 0.0)
        assertEquals(4, set.reps)

        val oldSaved = updated.copy(setLogged = true)
        store.saveDraft(oldSaved, false)
        val afterPreviouslySaved = store.startRest(oldSaved, false)
        assertEquals(0, afterPreviouslySaved.unloggedSets)
        val unselected = updated.copy(exercise = "", weight = "", reps = "")
        store.saveDraft(unselected, false)
        val afterUnselected = store.startRest(unselected, false)
        assertEquals(1, afterUnselected.unloggedSets)
        try { store.startRest(unselected, false); fail("Stale request must not count a second set") }
        catch (_: IllegalStateException) { }
        store.close()
        store = WorkoutStore(context, name)
        assertEquals(afterUnselected, store.active(false))
        assertEquals(listOf(set), store.sessions(false).single().sets)
    }

    @Test fun editingCellsUpdatesOnlyTheSelectedValueAndDataset() {
        store.startRest(push(), false)
        val original = store.sessions(false).single().sets.single()
        store.updateLoggedSet(original, original.copy(weightKg = 85.0), false)
        store.updateLoggedSet(original, original.copy(reps = null), false)
        try {
            store.updateLoggedSet(original, original.copy(exercise = "test"), true)
            fail("Editing from test mode must not change a real record")
        } catch (_: IllegalStateException) { }
        val date = LocalDate.of(2026, 10, 16)
        store.updateWorkoutDate(original.sessionId, date, false)
        val session = store.sessions(false).single()
        assertEquals(date, session.date)
        assertEquals(original.copy(weightKg = 85.0, reps = null), session.sets.single())
    }
}
