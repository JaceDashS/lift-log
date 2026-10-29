package com.example.workouttimer.ui

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.example.workouttimer.R

/** Fills [container] with one item_set_row per entry. */
fun bindSetRows(container: LinearLayout, entries: List<PreviewData.SetEntry>) {
    container.removeAllViews()
    val inflater = LayoutInflater.from(container.context)
    for (entry in entries) {
        val row = inflater.inflate(R.layout.item_set_row, container, false)
        row.findViewById<TextView>(R.id.text_set_no).text = entry.setNo.toString()
        row.findViewById<TextView>(R.id.text_set_exercise).text = entry.exercise
        row.findViewById<TextView>(R.id.text_set_load).text =
            container.context.getString(R.string.set_row_load_format, entry.weightKg, entry.reps)
        row.findViewById<TextView>(R.id.text_set_rest).text = entry.rest
        container.addView(row)
    }
}
