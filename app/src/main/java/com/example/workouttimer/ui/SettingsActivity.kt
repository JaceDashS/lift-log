package com.example.workouttimer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.R
import com.example.workouttimer.WorkoutActivity
import com.example.workouttimer.data.WorkoutStore
import com.example.workouttimer.data.SetInput

class SettingsActivity : AppCompatActivity() {
    private val store by lazy { WorkoutStore.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.button_settings_add_exercise).setOnClickListener { addExercise() }
        findViewById<View>(R.id.button_settings_layout).setOnClickListener {
            startActivity(Intent(this, WorkoutActivity::class.java))
        }
        findViewById<View>(R.id.button_settings_about).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.settings_about).setMessage(R.string.settings_about_message)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        findViewById<View>(R.id.button_settings_feedback).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JaceDashS/lift-log/issues")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_feedback_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        renderExercises()
        bindButtonOpacity()
    }

    private fun bindButtonOpacity() {
        val label = findViewById<TextView>(R.id.text_button_opacity)
        val slider = findViewById<SeekBar>(R.id.slider_button_opacity)
        val timerPreview = findViewById<View>(R.id.preview_timer_opacity)
        val buttonPreview = findViewById<View>(R.id.preview_button_opacity)
        fun render(percent: Int) {
            label.text = getString(R.string.settings_button_opacity, percent)
            timerPreview.alpha = (percent / 100f).coerceAtMost(0.8f)
            buttonPreview.alpha = percent / 100f
        }
        slider.progress = store.buttonOpacity.value
        render(slider.progress)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, fromUser: Boolean) {
                render(progress)
                if (fromUser) store.setButtonOpacity(progress)
            }

            override fun onStartTrackingTouch(view: SeekBar) = Unit
            override fun onStopTrackingTouch(view: SeekBar) = Unit
        })
    }

    private fun renderExercises() {
        findViewById<TextView>(R.id.text_settings_exercises).text = store.exercises(test = false)
            .joinToString("\n").ifEmpty { getString(R.string.settings_no_exercises) }
    }

    private fun addExercise() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setHint(R.string.session_exercise_hint)
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.add_exercise)
            .setView(input).setPositiveButton(R.string.add_exercise, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString()
                if (!SetInput.validExercise(name)) {
                    input.error = getString(R.string.error_exercise)
                } else {
                    store.addExercise(name)
                    renderExercises()
                    dialog.dismiss()
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.requestFocus()
        dialog.show()
    }
}
