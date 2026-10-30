package com.example.workouttimer.ui

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.example.workouttimer.R
import com.example.workouttimer.data.WorkoutSet

/** Fills [container] with one item_set_row per entry. */
fun bindSetRows(container: LinearLayout, entries: List<WorkoutSet>) {
    container.removeAllViews()
    val inflater = LayoutInflater.from(container.context)
    val counts = mutableMapOf<String, Int>()
    for (entry in entries) {
        val row = inflater.inflate(R.layout.item_set_row, container, false)
        val number = (counts[entry.exercise] ?: 0) + 1
        counts[entry.exercise] = number
        row.findViewById<TextView>(R.id.text_set_no).text = number.toString()
        row.findViewById<TextView>(R.id.text_set_exercise).text = entry.exercise
        row.findViewById<TextView>(R.id.text_set_load).text =
            container.context.getString(R.string.set_row_load_format, decimal(entry.weightKg), entry.reps)
        row.findViewById<TextView>(R.id.text_set_rest).text = duration(entry.restMillis)
        container.addView(row)
    }
}
