package com.example.workouttimer

import android.content.Context
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
        val draft = store.start(test).copy(exercise = "bcpr", weight = "60", reps = reps)
            .push(System.currentTimeMillis(), SystemClock.elapsedRealtime(), store.bootCount())
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
        assertEquals(draft.frozenRestMillis, store.sessions(false).single().sets.single().restMillis)
    }

    @Test fun draftRestoresUnfinishedInputsAndFrozenTimer() {
        val draft = push()
        store.close()
        store = WorkoutStore(context, name)
        assertEquals(draft, store.active(false))
        assertEquals(draft.frozenRestMillis, store.active(false)!!.restMillis(Long.MAX_VALUE, Long.MAX_VALUE, -1))
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
        store.perDay = true
        push(false)
        push(true)
        store.close()
        store = WorkoutStore(context, name)
        assertTrue(store.testMode)
        assertTrue(store.perDay)
        assertNotEquals(store.active(false)!!.sessionId, store.active(true)!!.sessionId)
        assertEquals(store.active(true), store.start())
    }
}
