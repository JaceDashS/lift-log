package com.example.workouttimer.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.example.workouttimer.R
import com.example.workouttimer.data.SessionDraft
import com.example.workouttimer.data.SetInput
import com.example.workouttimer.data.WorkoutStore

/** Persistent workout screen: PUSH freezes rest, REST validates and records the finished set. */
class SessionActivity : AppCompatActivity() {
    private val store by lazy { WorkoutStore.get(this) }
    private lateinit var draft: SessionDraft
    private var test = false
    private var binding = false
    private lateinit var exercise: EditText
    private lateinit var weight: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { updateTimer(); handler.postDelayed(this, 200) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)
        test = store.testMode
        draft = store.start(test)
        exercise = findViewById(R.id.input_session_exercise)
        weight = findViewById(R.id.input_session_weight)
        exercise.setText(draft.exercise)
        weight.setText(draft.weight)
        listOf(exercise, weight).forEach { input ->
            input.doAfterTextChanged {
                if (!binding) {
                    draft = draft.copy(exercise = exercise.text.toString(), weight = weight.text.toString())
                    persist { store.saveDraft(draft, test) }
                }
            }
        }
        findViewById<TextView>(R.id.button_session_push).setOnClickListener {
            val next = draft.push(System.currentTimeMillis(), SystemClock.elapsedRealtime(), store.bootCount())
            if (persist { store.saveDraft(next, test) }) { draft = next; render() }
        }
        findViewById<TextView>(R.id.button_session_rest).setOnClickListener { promptForReps() }
        findViewById<TextView>(R.id.button_session_end).setOnClickListener { endSession() }
        render()
    }

    override fun onResume() {
        super.onResume()
        val current = store.active(test)
        if (current == null || current.sessionId != draft.sessionId) { finish(); return }
        if (current != draft) {
            draft = current
            binding = true
            exercise.setText(draft.exercise)
            weight.setText(draft.weight)
            binding = false
            render()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun promptForReps(onSaved: () -> Unit = {}) {
        val current = draft
        fun startRest(reps: String): Boolean {
            if (!persist { draft = store.startRest(current, test, reps) }) return false
            render()
            return true
        }
        if (current.exercise.isBlank() || current.setLogged) {
            if (startRest("")) onSaved()
            return
        }
        if (!SetInput.validExercise(current.exercise)) {
            exercise.error = getString(R.string.error_exercise)
            return
        }
        if (SetInput.weightKg(current.weight, pounds = false) == null) {
            weight.error = getString(R.string.error_weight)
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setHint(R.string.overlay_reps_label)
            requestFocus()
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.overlay_reps_label).setView(input)
            .setPositiveButton(R.string.start_rest, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reps = input.text.toString().trim()
                if (SetInput.repCount(reps) == null) {
                    input.error = getString(R.string.error_reps)
                } else if (startRest(reps)) {
                    dialog.dismiss()
                    onSaved()
                }
            }
        }
        dialog.show()
    }

    private fun endSession() {
        if (!draft.pushing) { finishWorkout(); return }
        AlertDialog.Builder(this).setTitle(R.string.end_title).setMessage(R.string.end_message)
            .setPositiveButton(R.string.save_and_end) { _, _ -> promptForReps(::finishWorkout) }
            .setNeutralButton(R.string.discard_and_end) { _, _ -> finishWorkout() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun finishWorkout() {
        if (persist { store.end(test) }) finish()
    }

    private fun render() {
        findViewById<TextView>(R.id.text_session_state).text = getString(
            if (draft.pushing) R.string.session_state_push else R.string.session_state_rest
        ) + if (test) getString(R.string.test_suffix) else ""
        findViewById<TextView>(R.id.text_session_caption).setText(
            if (draft.pushing) R.string.session_push_caption else R.string.session_rest_caption
        )
        findViewById<TextView>(R.id.text_session_timer).setTextColor(ContextCompat.getColor(this,
            if (draft.pushing) R.color.lift_text_dim else R.color.lift_acid))
        findViewById<TextView>(R.id.button_session_push).isEnabled = !draft.pushing
        findViewById<TextView>(R.id.button_session_rest).isEnabled = draft.pushing
        val sets = store.sessions(test).firstOrNull { it.id == draft.sessionId }?.sets.orEmpty()
        findViewById<TextView>(R.id.text_session_set).text = getString(R.string.session_set_format,
            sets.size + draft.unloggedSets + 1, draft.exercise.ifBlank { getString(R.string.session_title) })
        bindSetRows(findViewById(R.id.list_session_sets), sets)
        updateTimer()
    }

    private fun updateTimer() {
        findViewById<TextView>(R.id.text_session_timer).text = if (draft.pushing) getString(R.string.workout_go) else duration(
            draft.restMillis(System.currentTimeMillis(), SystemClock.elapsedRealtime(), store.bootCount()))
    }

    private fun persist(action: () -> Unit): Boolean = try {
        action(); true
    } catch (_: Exception) {
        Toast.makeText(this, R.string.error_storage, Toast.LENGTH_LONG).show(); false
    }
}
