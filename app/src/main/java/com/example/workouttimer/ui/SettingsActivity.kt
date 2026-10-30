package com.example.workouttimer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.MainActivity
import com.example.workouttimer.R
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.button.MaterialButtonToggleGroup
import com.example.workouttimer.data.WorkoutStore
import com.example.workouttimer.data.SetInput
import java.time.LocalDate
import kotlinx.coroutines.*

/**
 * Persistent chart settings and isolated test data entry, generation and confirmed deletion.
 */
class SettingsActivity : AppCompatActivity() {
    private val store by lazy { WorkoutStore.get(this) }
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val testDataPanel = findViewById<View>(R.id.panel_settings_test_data)
        val testModeSwitch = findViewById<SwitchMaterial>(R.id.switch_settings_test_mode)
        testModeSwitch.isChecked = store.testMode
        testModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            store.testMode = isChecked
            testDataPanel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        testDataPanel.visibility = if (store.testMode) View.VISIBLE else View.GONE
        val spacing = findViewById<MaterialButtonToggleGroup>(R.id.toggle_settings_spacing)
        spacing.check(if (store.perDay) R.id.button_settings_per_day else R.id.button_settings_per_session)
        spacing.addOnButtonCheckedListener { _, id, checked ->
            if (checked) store.perDay = id == R.id.button_settings_per_day
        }
        findViewById<EditText>(R.id.input_test_date).setText(LocalDate.now().toString())
        findViewById<View>(R.id.button_test_add).setOnClickListener { addRecord() }
        findViewById<View>(R.id.button_test_fill).setOnClickListener { updateRecords { store.fillTestData() } }
        findViewById<View>(R.id.button_test_clear).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.clear_test_title).setMessage(R.string.clear_test_message)
                .setPositiveButton(R.string.test_clear) { _, _ -> updateRecords { store.clearTestData() } }
                .setNegativeButton(android.R.string.cancel, null).show()
        }

        findViewById<View>(R.id.row_settings_overlay_timer).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun addRecord() {
        val date = findViewById<EditText>(R.id.input_test_date)
        val exercise = findViewById<EditText>(R.id.input_test_exercise)
        val weight = findViewById<EditText>(R.id.input_test_weight)
        val reps = findViewById<EditText>(R.id.input_test_reps)
        val rest = findViewById<EditText>(R.id.input_test_rest)
        val day = try { LocalDate.parse(date.text.toString()) } catch (_: Exception) { null }
        val restMillis = SetInput.restMillis(rest.text.toString())
        var valid = true
        if (day == null || day.year !in 1900..2100) { date.error = getString(R.string.error_date); valid = false }
        if (!SetInput.valid(exercise.text.toString(), weight.text.toString(), reps.text.toString())) {
            if (exercise.text.toString().trim().isEmpty() || exercise.text.toString().trim().length > 100) exercise.error = getString(R.string.error_exercise)
            val kg = weight.text.toString().toDoubleOrNull()
            if (kg == null || !kg.isFinite() || kg !in 0.0..10000.0) weight.error = getString(R.string.error_weight)
            if (reps.text.toString().toIntOrNull() !in 1..10000) reps.error = getString(R.string.error_reps)
            valid = false
        }
        if (restMillis == null) { rest.error = getString(R.string.error_rest); valid = false }
        if (!valid || day == null || restMillis == null) return
        val name = exercise.text.toString()
        val kg = weight.text.toString()
        val count = reps.text.toString()
        updateRecords(onSuccess = { reps.setText("") }) { store.addTestSet(day, name, kg, count, restMillis) }
    }

    private fun updateRecords(onSuccess: () -> Unit = {}, action: () -> Unit) {
        if (!store.testMode) return
        val controls = listOf(R.id.button_test_add, R.id.button_test_fill, R.id.button_test_clear,
            R.id.switch_settings_test_mode, R.id.input_test_date, R.id.input_test_exercise,
            R.id.input_test_weight, R.id.input_test_reps, R.id.input_test_rest)
        controls.forEach { findViewById<View>(it).isEnabled = false }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                onSuccess()
                Toast.makeText(this@SettingsActivity, R.string.test_saved, Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.error_test_storage, Toast.LENGTH_LONG).show()
            } finally { controls.forEach { findViewById<View>(it).isEnabled = true } }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
