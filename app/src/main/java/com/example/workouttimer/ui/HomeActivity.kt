package com.example.workouttimer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.R
import com.example.workouttimer.data.WorkoutStore

/** Launcher screen: start a workout, open records or settings. */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.button_home_start).setOnClickListener {
            startActivity(Intent(this, SessionActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_records).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val store = WorkoutStore.get(this)
        val last = store.sessions().lastOrNull { it.sets.isNotEmpty() }
        findViewById<TextView>(R.id.text_home_last_date).text =
            (last?.date?.toString() ?: getString(R.string.empty_records)) +
                if (store.testMode) getString(R.string.test_suffix) else ""
        findViewById<TextView>(R.id.text_home_last_summary).text = last?.let {
            getString(R.string.home_summary_format, it.sets.map { set -> set.exercise }.distinct().joinToString(" · "), it.sets.size)
        } ?: getString(R.string.home_empty_hint)
        findViewById<TextView>(R.id.button_home_start).setText(
            if (store.active() != null) R.string.home_resume else R.string.home_start_workout)
    }
}
