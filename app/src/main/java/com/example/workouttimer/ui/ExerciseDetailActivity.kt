package com.example.workouttimer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.workouttimer.R
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.abs
import com.example.workouttimer.data.WorkoutHistory
import com.example.workouttimer.data.WorkoutSession
import com.example.workouttimer.data.WorkoutStore
import java.time.temporal.ChronoUnit

/**
 * One exercise: summary, average-reps trend and set-by-set comparison with the previous session.
 * Select any workout in the dropdown or graph to compare with its preceding workout.
 */
class ExerciseDetailActivity : AppCompatActivity() {
    private var history: List<WorkoutSession> = emptyList()
    private var selected = -1
    private lateinit var selector: Spinner
    private lateinit var chart: TrendChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_detail)

        val store = WorkoutStore.get(this)
        val name = intent.getStringExtra(EXTRA_EXERCISE).orEmpty()
        history = WorkoutHistory.sessions(store.sessions(), name)
        findViewById<TextView>(R.id.text_detail_name).text = name + if (store.testMode) getString(R.string.test_suffix) else ""
        findViewById<TextView>(R.id.text_detail_last).text = history.lastOrNull()?.date?.toString() ?: getString(R.string.empty_records)
        findViewById<TextView>(R.id.text_detail_avg).text = decimal(WorkoutHistory.average(history.flatMap { it.sets }))
        chart = findViewById(R.id.chart_trend)
        chart.points = history.map { session -> TrendChartView.Point(
            ChronoUnit.DAYS.between(history.first().date, session.date).toInt(),
            WorkoutHistory.average(session.sets).toFloat(), session.date.toString()) }
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggle_trend_spacing)
        val day = savedInstanceState?.getBoolean("per_day") ?: store.perDay
        chart.spacing = if (day) TrendChartView.Spacing.PER_DAY else TrendChartView.Spacing.PER_SESSION
        toggle.check(if (day) R.id.button_trend_per_day else R.id.button_trend_per_session)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                chart.spacing = if (checkedId == R.id.button_trend_per_day) {
                    TrendChartView.Spacing.PER_DAY
                } else {
                    TrendChartView.Spacing.PER_SESSION
                }
            }

        selector = findViewById(R.id.spinner_history_session)
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            history.mapIndexed { index, session -> getString(R.string.history_item, session.date.toString(), index + 1) })
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { select(position) }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        chart.onPointSelected = { selector.setSelection(it); select(it) }
        val restoredId = savedInstanceState?.getLong("selected", -1) ?: -1
        val initial = history.indexOfFirst { it.id == restoredId }.takeIf { it >= 0 } ?: history.lastIndex
        if (initial >= 0) { selector.setSelection(initial); select(initial) }
        else findViewById<TextView>(R.id.text_compare_sessions).setText(R.string.empty_records)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("selected", history.getOrNull(selected)?.id ?: -1)
        outState.putBoolean("per_day", chart.spacing == TrendChartView.Spacing.PER_DAY)
        super.onSaveInstanceState(outState)
    }

    private fun select(index: Int) {
        selected = index
        chart.selectedIndex = index
        val current = history.getOrNull(index) ?: return
        val previous = history.getOrNull(index - 1)
        findViewById<TextView>(R.id.text_compare_sessions).text = getString(R.string.history_compare,
            getString(R.string.history_item, current.date.toString(), index + 1),
            previous?.let { getString(R.string.history_item, it.date.toString(), index) } ?: getString(R.string.no_previous))
        bindComparisons(findViewById(R.id.list_compare_rows), current, previous)
    }

    private fun bindComparisons(container: LinearLayout, current: WorkoutSession, previous: WorkoutSession?) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (index in 0 until maxOf(current.sets.size, previous?.sets?.size ?: 0)) {
            val item = current.sets.getOrNull(index)
            val prev = previous?.sets?.getOrNull(index)
            val row = inflater.inflate(R.layout.item_compare_row, container, false)
            row.findViewById<TextView>(R.id.text_compare_set).text = (index + 1).toString()
            row.findViewById<TextView>(R.id.text_compare_reps).text = item?.let { getString(R.string.history_load, it.reps, decimal(it.weightKg)) } ?: getString(R.string.missing_value)
            row.findViewById<TextView>(R.id.text_compare_reps_prev).text =
                getString(R.string.compare_prev_format, prev?.let { getString(R.string.history_load, it.reps, decimal(it.weightKg)) } ?: getString(R.string.missing_value))
            row.findViewById<TextView>(R.id.text_compare_rest).text = item?.let { duration(it.restMillis) } ?: getString(R.string.missing_value)
            row.findViewById<TextView>(R.id.text_compare_rest_prev).text = getString(R.string.compare_prev_format, prev?.let { duration(it.restMillis) } ?: getString(R.string.missing_value))
            if (item != null && prev != null) {
                val repsDelta = item.reps - prev.reps
                bindDelta(row.findViewById(R.id.text_compare_reps_delta), repsDelta.toLong(), abs(repsDelta).toString(), true)
                val restDelta = item.restMillis / 1000 - prev.restMillis / 1000
                bindDelta(row.findViewById(R.id.text_compare_rest_delta), restDelta, duration(abs(restDelta) * 1000), false)
            }
            container.addView(row)
        }
    }

    private fun bindDelta(view: TextView, delta: Long, magnitude: String, betterWhenHigher: Boolean) {
        val colorRes = when {
            delta == 0L -> R.color.lift_text_dim
            (delta > 0) == betterWhenHigher -> R.color.lift_acid
            else -> R.color.lift_down
        }
        view.text = when {
            delta > 0 -> getString(R.string.compare_up_format, magnitude)
            delta < 0 -> getString(R.string.compare_down_format, magnitude)
            else -> getString(R.string.compare_same)
        }
        view.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    companion object {
        const val EXTRA_EXERCISE = "exercise"
    }
}
