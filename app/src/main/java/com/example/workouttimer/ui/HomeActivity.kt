package com.example.workouttimer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.workouttimer.OverlayService
import com.example.workouttimer.R
import com.example.workouttimer.WorkoutActivity
import com.example.workouttimer.data.SetInput
import com.example.workouttimer.data.WorkoutStore

/** Launcher screen: choose the next set's values, then START floats the timer over other apps. */
class HomeActivity : AppCompatActivity() {
    private val store by lazy { WorkoutStore.get(this) }
    private lateinit var exercise: EditText
    private lateinit var weight: EditText
    private lateinit var reps: EditText
    private var permissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        exercise = findViewById(R.id.input_home_exercise)
        weight = findViewById(R.id.input_home_weight)
        reps = findViewById(R.id.input_home_reps)

        findViewById<TextView>(R.id.button_home_start).setOnClickListener { start() }
        findViewById<TextView>(R.id.button_home_records).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_layout).setOnClickListener {
            startActivity(Intent(this, WorkoutActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        fillInputs()
    }

    override fun onResume() {
        super.onResume()
        val last = store.sessions().lastOrNull { it.sets.isNotEmpty() }
        findViewById<TextView>(R.id.text_home_last_date).text =
            (last?.date?.toString() ?: getString(R.string.empty_records)) +
                if (store.testMode) getString(R.string.test_suffix) else ""
        findViewById<TextView>(R.id.text_home_last_summary).text = last?.let {
            getString(R.string.home_summary_format, it.sets.map { set -> set.exercise }.distinct().joinToString(" · "), it.sets.size)
        } ?: getString(R.string.home_empty_hint)
        findViewById<TextView>(R.id.button_home_start).setText(
            if (store.active() != null) R.string.home_resume else R.string.home_start_workout)
        if (permissionRequested && Settings.canDrawOverlays(this)) {
            permissionRequested = false
            start()
        }
    }

    /** Prefills from the running workout, otherwise from the most recent logged set. */
    private fun fillInputs() {
        val draft = store.active()
        if (draft != null) {
            exercise.setText(draft.exercise)
            weight.setText(draft.weight)
            reps.setText(draft.reps)
            return
        }
        store.sessions().lastOrNull { it.sets.isNotEmpty() }?.sets?.last()?.let {
            exercise.setText(it.exercise)
            weight.setText(decimal(it.weightKg))
            reps.setText(it.reps.toString())
        }
    }

    private fun start() {
        val name = exercise.text.toString()
        val kg = weight.text.toString()
        val count = reps.text.toString()
        if (!SetInput.valid(name, kg, count)) {
            if (name.trim().isEmpty() || name.trim().length > 100) exercise.error = getString(R.string.error_exercise)
            val value = kg.toDoubleOrNull()
            if (value == null || !value.isFinite() || value !in 0.0..10000.0) weight.error = getString(R.string.error_weight)
            if (count.toIntOrNull() !in 1..10000) reps.error = getString(R.string.error_reps)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            permissionRequested = true
            Toast.makeText(this, R.string.home_overlay_permission, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            val test = store.testMode
            store.saveDraft(store.start(test).copy(exercise = name, weight = kg, reps = count), test)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_storage, Toast.LENGTH_LONG).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START_SERVICE
        })
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
