package com.example.workouttimer.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.R

/** Records screen: every exercise name the user has logged. UI preview with sample data. */
class RecordsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        val list = findViewById<LinearLayout>(R.id.list_records_exercises)
        val inflater = LayoutInflater.from(this)
        for (exercise in PreviewData.exercises) {
            val row = inflater.inflate(R.layout.item_exercise_row, list, false)
            row.findViewById<TextView>(R.id.text_exercise_name).text = exercise.name
            row.findViewById<TextView>(R.id.text_exercise_meta).text =
                getString(R.string.records_meta_format, exercise.lastDone, exercise.sessions)
            row.findViewById<TextView>(R.id.text_exercise_avg).text = exercise.avgReps
            row.setOnClickListener {
                startActivity(
                    Intent(this, ExerciseDetailActivity::class.java)
                        .putExtra(ExerciseDetailActivity.EXTRA_EXERCISE, exercise.name)
                )
            }
            list.addView(row)
        }
    }
}
