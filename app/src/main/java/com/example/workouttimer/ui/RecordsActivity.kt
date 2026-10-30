package com.example.workouttimer.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.R
import com.example.workouttimer.data.WorkoutStore
import com.example.workouttimer.data.WorkoutHistory

/** Records screen: logged exercise names and summaries from the active dataset. */
class RecordsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

    }

    override fun onResume() {
        super.onResume()
        val store = WorkoutStore.get(this)
        val sessions = store.sessions()
        val list = findViewById<LinearLayout>(R.id.list_records_exercises)
        list.removeAllViews()
        findViewById<TextView>(R.id.text_records_title).text = getString(R.string.records_title) +
            if (store.testMode) getString(R.string.test_suffix) else ""
        val inflater = LayoutInflater.from(this)
        val names = sessions.flatMap { it.sets }.map { it.exercise }.distinct().sorted()
        if (names.isEmpty()) list.addView(TextView(this).apply { setText(R.string.empty_records) })
        for (name in names) {
            val history = WorkoutHistory.sessions(sessions, name)
            val row = inflater.inflate(R.layout.item_exercise_row, list, false)
            row.findViewById<TextView>(R.id.text_exercise_name).text = name
            row.findViewById<TextView>(R.id.text_exercise_meta).text =
                getString(R.string.records_meta_format, history.last().date.toString(), history.size)
            row.findViewById<TextView>(R.id.text_exercise_avg).text = decimal(WorkoutHistory.average(history.flatMap { it.sets }))
            row.setOnClickListener {
                startActivity(
                    Intent(this, ExerciseDetailActivity::class.java)
                        .putExtra(ExerciseDetailActivity.EXTRA_EXERCISE, name)
                )
            }
            list.addView(row)
        }
    }
}
