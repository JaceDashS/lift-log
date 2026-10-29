package com.example.workouttimer.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.workouttimer.R

/**
 * Workout session screen. UI preview only: PUSH and REST switch the
 * visual state; no timer runs and no set is saved.
 */
class SessionActivity : AppCompatActivity() {

    private lateinit var stateChip: TextView
    private lateinit var timerText: TextView
    private lateinit var captionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        stateChip = findViewById(R.id.text_session_state)
        timerText = findViewById(R.id.text_session_timer)
        captionText = findViewById(R.id.text_session_caption)

        findViewById<TextView>(R.id.text_session_set).text = getString(
            R.string.session_set_format, PreviewData.SESSION_NEXT_SET, PreviewData.SESSION_EXERCISE
        )
        findViewById<EditText>(R.id.input_session_exercise).setText(PreviewData.SESSION_EXERCISE)
        findViewById<EditText>(R.id.input_session_weight).setText(PreviewData.SESSION_WEIGHT_KG)
        bindSetRows(findViewById(R.id.list_session_sets), PreviewData.sessionSets)

        findViewById<TextView>(R.id.button_session_rest).setOnClickListener { showRestState() }
        findViewById<TextView>(R.id.button_session_push).setOnClickListener { showPushState() }
        findViewById<TextView>(R.id.button_session_end).setOnClickListener { finish() }

        showRestState()
    }

    private fun showRestState() {
        stateChip.setText(R.string.session_state_rest)
        timerText.text = PreviewData.SESSION_REST_TIME
        timerText.setTextColor(ContextCompat.getColor(this, R.color.lift_acid))
        captionText.setText(R.string.session_rest_caption)
    }

    private fun showPushState() {
        stateChip.setText(R.string.session_state_push)
        timerText.text = PreviewData.SESSION_PUSH_TIME
        timerText.setTextColor(ContextCompat.getColor(this, R.color.lift_text_dim))
        captionText.setText(R.string.session_push_caption)
    }
}
