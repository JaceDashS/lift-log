package com.example.workouttimer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject
import java.time.LocalDate

/** Local SQLite repository. Completed sets and the next draft are committed in one transaction. */
class WorkoutStore internal constructor(context: Context, databaseName: String = "workouts.db") : SQLiteOpenHelper(
    context.applicationContext, databaseName, null, 1
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        if (databaseName == "workouts.db") "workout_settings" else "$databaseName.settings", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver

    var testMode: Boolean
        get() = prefs.getBoolean("test_mode", false)
        set(value) { prefs.edit().putBoolean("test_mode", value).apply() }
    var perDay: Boolean
        get() = prefs.getBoolean("per_day", false)
        set(value) { prefs.edit().putBoolean("per_day", value).apply() }

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY, day TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER, test INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sets (id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, exercise TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL CHECK(reps > 0), rest INTEGER NOT NULL CHECK(rest >= 0))")
        db.execSQL("CREATE INDEX sets_session ON sets(session_id)")
        db.execSQL("CREATE TABLE drafts (test INTEGER PRIMARY KEY, payload TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun bootCount(): Int = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)

    @Synchronized
    fun active(test: Boolean = testMode): SessionDraft? = readableDatabase.rawQuery(
        "SELECT payload FROM drafts WHERE test=?", arrayOf(if (test) "1" else "0")
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else JSONObject(cursor.getString(0)).let { j ->
            SessionDraft(j.getLong("session"), j.getBoolean("pushing"), j.getLong("wall"),
                j.getLong("elapsed"), j.getInt("boot"), j.getLong("frozen"),
                j.getString("exercise"), j.getString("weight"), j.getString("reps"))
        }
    }

    @Synchronized
    fun start(test: Boolean = testMode): SessionDraft {
        active(test)?.let { return it }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val id = insertSession(db, LocalDate.now(), now, test)
            val draft = SessionDraft(id, restStartedWall = now,
                restStartedElapsed = SystemClock.elapsedRealtime(), boot = bootCount())
            saveDraft(draft, test)
            db.setTransactionSuccessful()
            return draft
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun saveDraft(draft: SessionDraft, test: Boolean = testMode) {
        val json = JSONObject().put("session", draft.sessionId).put("pushing", draft.pushing)
            .put("wall", draft.restStartedWall).put("elapsed", draft.restStartedElapsed)
            .put("boot", draft.boot).put("frozen", draft.frozenRestMillis)
            .put("exercise", draft.exercise).put("weight", draft.weight).put("reps", draft.reps)
        writableDatabase.insertWithOnConflict("drafts", null, ContentValues().apply {
            put("test", if (test) 1 else 0); put("payload", json.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    /** Saves exactly one PUSH set and starts REST atomically; repetitions clear unless [keepReps]. */
    @Synchronized
    fun completeSet(draft: SessionDraft, test: Boolean = testMode, keepReps: Boolean = false): SessionDraft {
        require(draft.pushing && SetInput.valid(draft.exercise, draft.weight, draft.reps))
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(active(test) == draft) { "Session changed" }
            insertSet(db, WorkoutSet(sessionId = draft.sessionId, exercise = draft.exercise.trim(),
                weightKg = draft.weight.toDouble(), reps = draft.reps.toInt(), restMillis = draft.frozenRestMillis))
            val next = draft.rest(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())
                .let { if (keepReps) it.copy(reps = draft.reps) else it }
            saveDraft(next, test)
            db.setTransactionSuccessful()
            return next
        } finally { db.endTransaction() }
    }

    /** Ends the active workout, retaining completed sets and deleting only its draft. */
    @Synchronized
    fun end(test: Boolean = testMode) {
        val draft = active(test) ?: return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("sessions", ContentValues().apply { put("ended", System.currentTimeMillis()) },
                "id=?", arrayOf(draft.sessionId.toString()))
            db.delete("drafts", "test=?", arrayOf(if (test) "1" else "0"))
            db.delete("sessions", "id=? AND NOT EXISTS(SELECT 1 FROM sets WHERE session_id=?)",
                arrayOf(draft.sessionId.toString(), draft.sessionId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun sessions(test: Boolean = testMode): List<WorkoutSession> {
        val sets = mutableListOf<WorkoutSet>()
        readableDatabase.rawQuery("SELECT t.id,t.session_id,t.exercise,t.weight,t.reps,t.rest FROM sets t JOIN sessions s ON s.id=t.session_id WHERE s.test=? ORDER BY t.id", arrayOf(if (test) "1" else "0")).use { c ->
            while (c.moveToNext()) sets += WorkoutSet(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getInt(4), c.getLong(5))
        }
        val grouped = sets.groupBy { it.sessionId }
        val result = mutableListOf<WorkoutSession>()
        readableDatabase.rawQuery("SELECT id,day,started,ended FROM sessions WHERE test=? ORDER BY day,started,id", arrayOf(if (test) "1" else "0")).use { c ->
            while (c.moveToNext()) result += WorkoutSession(c.getLong(0), LocalDate.parse(c.getString(1)),
                c.getLong(2), if (c.isNull(3)) null else c.getLong(3), test, grouped[c.getLong(0)].orEmpty())
        }
        return result
    }

    /** Manual test records on the same date share a test workout. Real records are never queried. */
    @Synchronized
    fun addTestSet(date: LocalDate, exercise: String, weight: String, reps: String, rest: Long) {
        require(SetInput.valid(exercise, weight, reps) && rest in 0..86400000)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.rawQuery("SELECT id FROM sessions WHERE test=1 AND day=? AND ended IS NOT NULL ORDER BY id LIMIT 1", arrayOf(date.toString())).use {
                if (it.moveToFirst()) it.getLong(0) else null
            } ?: insertSession(db, date, date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), true, true)
            insertSet(db, WorkoutSet(sessionId = id, exercise = exercise.trim(), weightKg = weight.toDouble(), reps = reps.toInt(), restMillis = rest))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Adds thirty days of sample workouts as a single transaction in the test dataset. */
    @Synchronized
    fun fillTestData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (day in 0 until 30 step 3) {
                for (exercise in listOf("bcpr", "sqt", "dl")) {
                    repeat(3) { addTestSet(LocalDate.now().minusDays(day.toLong()), exercise,
                        if (exercise == "dl") "80" else "60", kotlin.random.Random.nextInt(6, 13).toString(),
                        kotlin.random.Random.nextLong(60, 151) * 1000) }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Deletes only test sessions and their cascaded sets. Call after explicit UI confirmation. */
    @Synchronized
    fun clearTestData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("drafts", "test=1", null)
            db.delete("sessions", "test=1", null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun insertSession(db: SQLiteDatabase, day: LocalDate, started: Long, test: Boolean, ended: Boolean = false): Long =
        db.insertOrThrow("sessions", null, ContentValues().apply {
            put("day", day.toString()); put("started", started); put("test", if (test) 1 else 0)
            if (ended) put("ended", started)
        })

    private fun insertSet(db: SQLiteDatabase, set: WorkoutSet) {
        db.insertOrThrow("sets", null, ContentValues().apply {
            put("session_id", set.sessionId); put("exercise", set.exercise); put("weight", set.weightKg)
            put("reps", set.reps); put("rest", set.restMillis)
        })
    }

    companion object {
        @Volatile private var instance: WorkoutStore? = null
        fun get(context: Context): WorkoutStore = instance ?: synchronized(this) {
            instance ?: WorkoutStore(context).also { instance = it }
        }
    }
}
