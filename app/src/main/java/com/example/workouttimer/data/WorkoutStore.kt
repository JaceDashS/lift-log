package com.example.workouttimer.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local SQLite repository. Completed sets and the next draft are committed in one transaction. */
class WorkoutStore internal constructor(context: Context, databaseName: String = "workouts.db") : SQLiteOpenHelper(
    context.applicationContext, databaseName, null, 3
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        if (databaseName == "workouts.db") "workout_settings" else "$databaseName.settings", Context.MODE_PRIVATE)
    init {
        // Stop using the former test dataset without modifying its records.
        if (databaseName == "workouts.db" && prefs.contains("test_mode")) {
            prefs.edit().remove("test_mode").apply()
        }
    }

    private val resolver = context.applicationContext.contentResolver
    private val buttonOpacitySetting = MutableStateFlow(
        prefs.getInt("overlay_button_opacity", 70).coerceIn(35, 90)
    )
    val buttonOpacity = buttonOpacitySetting.asStateFlow()
    private val layoutPreview = MutableStateFlow<Int?>(null)
    val layoutPreviewDisplay = layoutPreview.asStateFlow()

    fun showLayoutPreview(displayId: Int?) {
        layoutPreview.value = displayId
    }

    fun setButtonOpacity(percent: Int) {
        val opacity = percent.coerceIn(35, 90)
        prefs.edit().putInt("overlay_button_opacity", opacity).apply()
        buttonOpacitySetting.value = opacity
    }

    val supportsTestMode = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var testMode: Boolean
        get() = supportsTestMode && prefs.getBoolean("test_mode", false)
        set(value) {
            if (supportsTestMode) prefs.edit().putBoolean("test_mode", value).apply()
        }
    var perDay: Boolean
        get() = prefs.getBoolean("per_day", true)
        set(value) { prefs.edit().putBoolean("per_day", value).apply() }
    var weightInPounds: Boolean
        get() = prefs.getBoolean("weight_in_pounds", false)
        set(value) { prefs.edit().putBoolean("weight_in_pounds", value).apply() }

    fun exercises(test: Boolean = testMode): List<String> {
        val saved = JSONArray(prefs.getString("exercises", "[]"))
        val configured = List(saved.length()) { saved.getString(it) }
        val recorded = readableDatabase.rawQuery(
            "SELECT DISTINCT t.exercise FROM sets t JOIN sessions s ON s.id=t.session_id WHERE s.test=? ORDER BY t.exercise",
            arrayOf(if (test) "1" else "0")
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return (defaultExercises + configured + recorded + listOfNotNull(active(test)?.exercise?.trim()))
            .filter(SetInput::validExercise).distinct()
    }

    @Synchronized
    fun addExercise(exercise: String) {
        require(SetInput.validExercise(exercise))
        val name = exercise.trim()
        val saved = JSONArray(prefs.getString("exercises", "[]"))
        if ((0 until saved.length()).none { saved.getString(it) == name }) {
            prefs.edit().putString("exercises", saved.put(name).toString()).apply()
        }
    }

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY, day TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER, test INTEGER NOT NULL)")
        createSets(db)
        db.execSQL("CREATE TABLE drafts (test INTEGER PRIMARY KEY, payload TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE sets RENAME TO old_sets")
            db.execSQL("DROP INDEX sets_session")
            createSets(db)
            db.execSQL("INSERT INTO sets (id, session_id, exercise, weight, reps, rest) SELECT id, session_id, exercise, weight, reps, rest FROM old_sets")
            db.execSQL("DROP TABLE old_sets")
        }
    }

    private fun createSets(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sets (id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, exercise TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER CHECK(reps > 0), rest INTEGER CHECK(rest >= 0))")
        db.execSQL("CREATE INDEX sets_session ON sets(session_id)")
    }

    fun bootCount(): Int = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)

    @Synchronized
    fun active(test: Boolean = testMode): SessionDraft? = readableDatabase.rawQuery(
        "SELECT payload FROM drafts WHERE test=?", arrayOf(if (test) "1" else "0")
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else JSONObject(cursor.getString(0)).let { j ->
            SessionDraft(j.getLong("session"), j.getBoolean("pushing"), j.getLong("wall"),
                j.getLong("elapsed"), j.getInt("boot"), j.getLong("frozen"),
                j.getString("exercise"), j.getString("weight"), j.getString("reps"),
                j.optBoolean("set_logged"),
                if (j.has("workout_started")) {
                    if (j.isNull("workout_started")) null else j.getLong("workout_started")
                } else {
                    readableDatabase.query("sessions", arrayOf("started"), "id=?",
                        arrayOf(j.getLong("session").toString()), null, null, null).use {
                        if (it.moveToFirst()) it.getLong(0) else j.getLong("wall")
                    }
                },
                j.optString("previous_exercise").takeIf { !j.isNull("previous_exercise") && it.isNotBlank() },
                j.optInt("unlogged_sets"))
        }
    }

    @Synchronized
    fun start(test: Boolean = testMode, ready: Boolean = false): SessionDraft {
        active(test)?.let { return it }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val id = insertSession(db, LocalDate.now(), now, test)
            val draft = SessionDraft(id, restStartedWall = now,
                restStartedElapsed = SystemClock.elapsedRealtime(), boot = bootCount(),
                workoutStartedAt = now.takeUnless { ready })
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
            .put("set_logged", draft.setLogged)
            .put("workout_started", draft.workoutStartedAt ?: JSONObject.NULL)
            .put("previous_exercise", draft.previousExercise ?: JSONObject.NULL)
            .put("unlogged_sets", draft.unloggedSets)
        writableDatabase.insertWithOnConflict("drafts", null, ContentValues().apply {
            put("test", if (test) 1 else 0); put("payload", json.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    @Synchronized
    fun replaceDraft(expected: SessionDraft, next: SessionDraft, test: Boolean): Boolean {
        if (active(test) != expected) return false
        val started = next.workoutStartedAt
        if (expected.workoutStartedAt == null && started != null) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.update("sessions", ContentValues().apply {
                    put("started", started)
                    put("day", Instant.ofEpochMilli(started).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                }, "id=?", arrayOf(next.sessionId.toString()))
                saveDraft(next, test)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } else {
            saveDraft(next, test)
        }
        return true
    }

    /** Saves exactly one PUSH set and starts REST atomically; repetitions clear unless [keepReps]. */
    @Synchronized
    fun completeSet(draft: SessionDraft, test: Boolean = testMode, keepReps: Boolean = false): SessionDraft {
        require(draft.pushing && !draft.setLogged && SetInput.valid(draft.exercise, draft.weight, draft.reps))
        val next = draft.rest(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(), keepReps)
        return writeSet(draft, next, test)
    }

    @Synchronized
    fun startRest(
        draft: SessionDraft,
        test: Boolean = testMode,
        reps: String = draft.reps,
        weight: String = draft.weight,
    ): SessionDraft {
        require(draft.pushing)
        val completed = draft.copy(reps = reps, weight = weight)
        val next = completed.rest(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(), keepReps = true)
        if (draft.exercise.isBlank() || draft.setLogged) {
            check(replaceDraft(draft, next, test)) { "Session changed" }
            return next
        }
        require(SetInput.valid(completed.exercise, completed.weight, completed.reps))
        return writeSet(draft, next, test, completed)
    }

    private fun writeSet(
        draft: SessionDraft,
        next: SessionDraft,
        test: Boolean,
        completed: SessionDraft = draft,
    ): SessionDraft {
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(active(test) == draft) { "Session changed" }
            insertSet(db, WorkoutSet(sessionId = completed.sessionId, exercise = completed.exercise.trim(),
                weightKg = requireNotNull(SetInput.weightKg(completed.weight, pounds = false)),
                reps = requireNotNull(SetInput.repCount(completed.reps)), restMillis = completed.recordedRestMillis))
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
    fun sessions(test: Boolean = testMode, sessionId: Long? = null): List<WorkoutSession> {
        val condition = if (sessionId == null) "s.test=?" else "s.test=? AND s.id=?"
        val dataset = if (test) "1" else "0"
        val args = if (sessionId == null) arrayOf(dataset) else arrayOf(dataset, sessionId.toString())
        val sets = mutableListOf<WorkoutSet>()
        readableDatabase.rawQuery("SELECT t.id,t.session_id,t.exercise,t.weight,t.reps,t.rest FROM sets t JOIN sessions s ON s.id=t.session_id WHERE $condition ORDER BY t.id", args).use { c ->
            while (c.moveToNext()) sets += WorkoutSet(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3),
                if (c.isNull(4)) null else c.getInt(4), if (c.isNull(5)) null else c.getLong(5))
        }
        val grouped = sets.groupBy { it.sessionId }
        val result = mutableListOf<WorkoutSession>()
        readableDatabase.rawQuery("SELECT s.id,s.day,s.started,s.ended FROM sessions s WHERE $condition ORDER BY s.day,s.started,s.id", args).use { c ->
            while (c.moveToNext()) result += WorkoutSession(c.getLong(0), LocalDate.parse(c.getString(1)),
                c.getLong(2), if (c.isNull(3)) null else c.getLong(3), test, grouped[c.getLong(0)].orEmpty())
        }
        return result
    }

    /** Manual test records on the same date share a test workout. Real records are never queried. */
    @Synchronized
    fun addTestSet(date: LocalDate, exercise: String, weight: String, reps: String, rest: Long) {
        check(supportsTestMode)
        require(SetInput.valid(exercise, weight, reps) && rest in 0..86400000)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.rawQuery("SELECT id FROM sessions WHERE test=1 AND day=? AND ended IS NOT NULL ORDER BY id LIMIT 1", arrayOf(date.toString())).use {
                if (it.moveToFirst()) it.getLong(0) else null
            } ?: insertSession(db, date, date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), true, true)
            insertSet(db, WorkoutSet(sessionId = id, exercise = exercise.trim(),
                weightKg = requireNotNull(SetInput.weightKg(weight, pounds = false)), reps = reps.toInt(), restMillis = rest))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Adds thirty days of sample workouts as a single transaction in the test dataset. */
    @Synchronized
    fun fillTestData() {
        check(supportsTestMode)
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
        check(supportsTestMode)
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

    @Synchronized
    fun updateLoggedSet(original: WorkoutSet, set: WorkoutSet, test: Boolean) {
        require(set.id == original.id && set.sessionId == original.sessionId)
        require(SetInput.validExercise(set.exercise) && set.weightKg in 0.0..10_000.0)
        require((set.reps == null || set.reps in 1..SetInput.MAX_REPS) && (set.restMillis == null || set.restMillis >= 0L))
        val values = ContentValues().apply {
            if (set.exercise != original.exercise) put("exercise", set.exercise.trim())
            if (set.weightKg != original.weightKg) put("weight", set.weightKg)
            if (set.reps != original.reps) put("reps", set.reps)
            if (set.restMillis != original.restMillis) put("rest", set.restMillis)
        }
        if (values.size() == 0) return
        val changed = writableDatabase.update("sets", values,
            "id=? AND session_id IN (SELECT id FROM sessions WHERE test=?)",
            arrayOf(set.id.toString(), if (test) "1" else "0"))
        check(changed == 1) { "Set is no longer available" }
    }

    @Synchronized
    fun updateWorkoutDate(sessionId: Long, date: LocalDate, test: Boolean) {
        require(date.year in 1900..2100)
        val changed = writableDatabase.update("sessions", ContentValues().apply {
            put("day", date.toString())
        }, "id=? AND test=?", arrayOf(sessionId.toString(), if (test) "1" else "0"))
        check(changed == 1) { "Workout is no longer available" }
    }

    companion object {
        private val defaultExercises = listOf("Bench Press", "Squat", "Lat Pulldown")
        @Volatile private var instance: WorkoutStore? = null
        fun get(context: Context): WorkoutStore = instance ?: synchronized(this) {
            instance ?: WorkoutStore(context).also { instance = it }
        }
    }
}
