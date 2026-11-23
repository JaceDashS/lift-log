package com.example.workouttimer.ui

import android.content.Intent
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
import androidx.core.view.isVisible
import com.example.workouttimer.R
import com.example.workouttimer.data.ExerciseTrend
import com.example.workouttimer.data.ExerciseTrend.Metric
import com.example.workouttimer.data.SetInput
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.abs
import kotlin.math.roundToLong
import com.example.workouttimer.data.WorkoutHistory
import com.example.workouttimer.data.WorkoutSession
import com.example.workouttimer.data.WorkoutStore

class RecordsActivity : AppCompatActivity() {
    private val store by lazy { WorkoutStore.get(this) }
    private val samples by lazy { recordSamples() }
    private var restoredScroll: Float? = null
    private var selectedExercise: String? = null
    private var selectedSession = -1L
    private var history = emptyList<WorkoutSession>()
    private var chartHistory = emptyList<WorkoutSession>()
    private lateinit var exerciseSelector: Spinner
    private lateinit var sessionSelector: Spinner
    private lateinit var chart: TrendChartView
    private lateinit var spacingToggle: MaterialButtonToggleGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)
        selectedExercise = savedInstanceState?.getString("exercise")
        selectedSession = savedInstanceState?.getLong("selected") ?: -1L
        exerciseSelector = findViewById(R.id.spinner_records_exercise)
        sessionSelector = findViewById(R.id.spinner_history_session)
        chart = findViewById(R.id.chart_trend)
        chart.zoomFactor = savedInstanceState?.getDouble("chart_zoom", 1.0) ?: 1.0
        chart.visibleMetrics = savedInstanceState?.getIntArray("visible_metrics")
            ?.map { Metric.entries[it] }?.toSet() ?: Metric.entries.toSet()
        bindChartLegend()

        spacingToggle = findViewById(R.id.toggle_trend_spacing)
        spacingToggle.isSaveFromParentEnabled = false
        spacingToggle.check(if (store.perDay) R.id.button_trend_per_day else R.id.button_trend_per_session)
        spacingToggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                store.perDay = id == R.id.button_trend_per_day
                bindTrend()
            }
        }
        findViewById<View>(R.id.button_edit_records).setOnClickListener {
            startActivity(Intent(this, EditRecordsActivity::class.java))
        }
        chart.onPointSelected = { index ->
            val session = chartHistory[index]
            val position = if (store.perDay) history.indexOfLast { it.date == session.date }
                else history.indexOfFirst { it.id == session.id }
            if (position >= 0) {
                selectSession(position)
                sessionSelector.setSelection(position)
            }
        }
        restoredScroll = savedInstanceState?.getFloat("chart_scroll_dp")
    }

    override fun onResume() {
        super.onResume()
        reload()
        restoredScroll?.let { chart.scrollFromLatestDp = it }
        restoredScroll = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("selected", selectedSession)
        outState.putString("exercise", selectedExercise)
        outState.putFloat("chart_scroll_dp", chart.scrollFromLatestDp)
        outState.putDouble("chart_zoom", chart.zoomFactor)
        outState.putIntArray("visible_metrics", chart.visibleMetrics.map { it.ordinal }.toIntArray())
        super.onSaveInstanceState(outState)
    }

    private fun reload() {
        val saved = store.sessions()
        val examples = if (saved.none { it.sets.isNotEmpty() }) samples else null
        findViewById<View>(R.id.button_edit_records).isVisible = examples == null
        val all = examples ?: saved
        val names = all.flatMap { it.sets }.map { it.exercise }.distinct()
        val exercises = if (examples == null) names.sorted() else names
        val initial = exercises.indexOfFirst { it == selectedExercise }.coerceAtLeast(0)
        val hasRecords = exercises.isNotEmpty()
        exerciseSelector.isVisible = hasRecords
        findViewById<View>(R.id.card_records_trend).isVisible = hasRecords
        findViewById<View>(R.id.card_records_compare).isVisible = hasRecords
        findViewById<View>(R.id.text_records_empty).isVisible = !hasRecords
        if (!hasRecords) {
            exerciseSelector.onItemSelectedListener = null
            sessionSelector.onItemSelectedListener = null
            return
        }
        bindExercise(all, exercises[initial])
        configureSpinner(exerciseSelector, exercises, initial) { position ->
            val exercise = exercises[position]
            if (exercise != selectedExercise) bindExercise(all, exercise)
        }
    }

    private fun bindExercise(all: List<WorkoutSession>, exercise: String) {
        selectedExercise = exercise
        history = WorkoutHistory.sessions(all, exercise)
        val initial = history.indexOfFirst { it.id == selectedSession }.takeIf { it >= 0 } ?: history.lastIndex
        selectedSession = history[initial].id
        bindTrend()
        bindSessionComparison(initial)
        configureSpinner(sessionSelector, history.mapIndexed { index, item ->
            getString(R.string.history_item, item.date.toString(), index + 1)
        }, initial) { position ->
            if (history[position].id != selectedSession) {
                selectSession(position)
                chart.revealSelection()
            }
        }
    }

    private fun configureSpinner(spinner: Spinner, values: List<String>, index: Int, onSelect: (Int) -> Unit) {
        spinner.onItemSelectedListener = null
        spinner.isSaveFromParentEnabled = false
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelect(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun bindTrend() {
        val day = store.perDay
        chartHistory = if (day) history.groupBy { it.date }.values.map { sessions ->
            sessions.last().copy(sets = sessions.flatMap { it.sets })
        } else history
        chart.trend = ExerciseTrend(chartHistory)
        chart.spacing = if (day) TrendChartView.Spacing.PER_DAY else TrendChartView.Spacing.PER_SESSION
        updateZeroNote()
        updateChartSelection()
        chart.scrollFromLatestDp = 0f
        chart.revealSelection()
    }

    private fun updateZeroNote() {
        findViewById<View>(R.id.text_trend_zero_baseline).isVisible =
            chart.visibleMetrics.any { chart.trend.series.getValue(it).hasLeadingZero }
    }

    private fun updateChartSelection() {
        val session = history.firstOrNull { it.id == selectedSession } ?: return
        val index = chartHistory.indexOfFirst { if (store.perDay) it.date == session.date else it.id == session.id }
        chart.selectedIndex = index
        if (index >= 0) showAverages(index, chartHistory[index])
    }

    private fun bindChartLegend() {
        val buttons = mapOf(
            Metric.WEIGHT to R.id.button_trend_weight,
            Metric.REPS to R.id.button_trend_reps,
            Metric.REST to R.id.button_trend_rest,
        )
        for ((metric, id) in buttons) {
            val button = findViewById<MaterialButton>(id)
            button.setTextColor(ContextCompat.getColor(this, metric.chartColor))
            button.isChecked = metric in chart.visibleMetrics
            button.alpha = if (button.isChecked) 1f else 0.45f
            button.addOnCheckedChangeListener { view, checked ->
                view.alpha = if (checked) 1f else 0.45f
                chart.visibleMetrics = if (checked) chart.visibleMetrics + metric else chart.visibleMetrics - metric
                updateZeroNote()
            }
        }
        updateZeroNote()
    }

    private fun selectSession(index: Int) {
        selectedSession = history[index].id
        updateChartSelection()
        bindSessionComparison(index)
    }

    private fun bindSessionComparison(index: Int) {
        val current = history[index]
        val previous = history.getOrNull(index - 1)
        findViewById<TextView>(R.id.text_compare_sessions).text = getString(R.string.history_compare,
            getString(R.string.history_item, current.date.toString(), index + 1),
            previous?.let { getString(R.string.history_item, it.date.toString(), index) } ?: getString(R.string.no_previous))
        bindComparisons(findViewById(R.id.list_compare_rows), current, previous)
    }

    private fun showAverages(index: Int, session: WorkoutSession) {
        fun average(metric: Metric) = chart.trend.series.getValue(metric).values[index]
        val missing = getString(R.string.missing_value)
        val pounds = store.weightInPounds
        val weight = average(Metric.WEIGHT)?.let { kg ->
            val unit = getString(if (pounds) R.string.weight_lb_unit else R.string.weight_kg_unit)
            val value = if (pounds) kg / SetInput.KG_PER_POUND else kg
            getString(R.string.weight_with_unit, decimal(value), unit)
        } ?: missing
        val reps = average(Metric.REPS)?.let { getString(R.string.trend_reps_value, decimal(it)) } ?: missing
        val rest = average(Metric.REST)?.let {
            getString(R.string.trend_rest_value, duration(it.roundToLong() * 1000))
        } ?: missing
        findViewById<TextView>(R.id.text_trend_selected_date).text =
            if (store.perDay) session.date.toString() else getString(R.string.history_item, session.date.toString(), index + 1)
        findViewById<TextView>(R.id.text_trend_selected_values).text =
            getString(R.string.trend_session_averages, weight, reps, rest)
    }

    private fun bindComparisons(container: LinearLayout, current: WorkoutSession, previous: WorkoutSession?) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (index in 0 until maxOf(current.sets.size, previous?.sets?.size ?: 0)) {
            val item = current.sets.getOrNull(index)
            val prev = previous?.sets?.getOrNull(index)
            val row = inflater.inflate(R.layout.item_compare_row, container, false)
            row.findViewById<TextView>(R.id.text_compare_set).text = (index + 1).toString()
            row.findViewById<TextView>(R.id.text_compare_reps).text = item?.let { recordedLoad(it) } ?: getString(R.string.missing_value)
            row.findViewById<TextView>(R.id.text_compare_reps_prev).text =
                getString(R.string.compare_prev_format, prev?.let { recordedLoad(it) } ?: getString(R.string.missing_value))
            val rest = item?.restMillis
            val previousRest = prev?.restMillis
            row.findViewById<TextView>(R.id.text_compare_rest).text = rest?.let(::duration) ?: getString(R.string.missing_value)
            row.findViewById<TextView>(R.id.text_compare_rest_prev).text = getString(
                R.string.compare_prev_format, previousRest?.let(::duration) ?: getString(R.string.missing_value)
            )
            if (item != null && prev != null) {
                if (item.reps != null && prev.reps != null) {
                    val repsDelta = item.reps - prev.reps
                    bindDelta(row.findViewById(R.id.text_compare_reps_delta), repsDelta.toLong(), abs(repsDelta).toString(), true)
                }
            }
            if (rest != null && previousRest != null) {
                val restDelta = rest / 1000 - previousRest / 1000
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
}
