package com.example.workouttimer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.workouttimer.R
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.abs

/**
 * One exercise: summary, average-reps trend and set-by-set comparison with the previous session.
 * UI preview with sample data; the exercise name comes from the tapped row.
 */
class ExerciseDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_detail)

        val name = intent.getStringExtra(EXTRA_EXERCISE) ?: PreviewData.exercises.first().name
        val summary = PreviewData.exercises.firstOrNull { it.name == name } ?: PreviewData.exercises.first()
        findViewById<TextView>(R.id.text_detail_name).text = summary.name
        findViewById<TextView>(R.id.text_detail_last).text = summary.lastDone
        findViewById<TextView>(R.id.text_detail_avg).text = summary.avgReps
        findViewById<TextView>(R.id.text_compare_sessions).text = PreviewData.COMPARE_SESSIONS

        val chart = findViewById<TrendChartView>(R.id.chart_trend)
        chart.points = PreviewData.trend
        findViewById<MaterialButtonToggleGroup>(R.id.toggle_trend_spacing)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                chart.spacing = if (checkedId == R.id.button_trend_per_day) {
                    TrendChartView.Spacing.PER_DAY
                } else {
                    TrendChartView.Spacing.PER_SESSION
                }
            }

        bindComparisons(findViewById(R.id.list_compare_rows))
    }

    private fun bindComparisons(container: LinearLayout) {
        val inflater = LayoutInflater.from(this)
        for (item in PreviewData.comparisons) {
            val row = inflater.inflate(R.layout.item_compare_row, container, false)
            row.findViewById<TextView>(R.id.text_compare_set).text = item.setNo.toString()
            row.findViewById<TextView>(R.id.text_compare_reps).text = item.reps.toString()
            row.findViewById<TextView>(R.id.text_compare_reps_prev).text =
                getString(R.string.compare_prev_format, item.prevReps.toString())
            row.findViewById<TextView>(R.id.text_compare_rest).text = formatSeconds(item.restSec)

            val repsDelta = item.reps - item.prevReps
            bindDelta(
                row.findViewById(R.id.text_compare_reps_delta),
                repsDelta,
                abs(repsDelta).toString(),
                betterWhenHigher = true,
            )
            val restDelta = item.restSec - item.prevRestSec
            bindDelta(
                row.findViewById(R.id.text_compare_rest_delta),
                restDelta,
                formatSeconds(abs(restDelta)),
                betterWhenHigher = false,
            )
            container.addView(row)
        }
    }

    private fun bindDelta(view: TextView, delta: Int, magnitude: String, betterWhenHigher: Boolean) {
        val colorRes = when {
            delta == 0 -> R.color.lift_text_dim
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

    private fun formatSeconds(totalSec: Int): String =
        getString(R.string.duration_format, totalSec / SECONDS_PER_MINUTE, totalSec % SECONDS_PER_MINUTE)

    companion object {
        const val EXTRA_EXERCISE = "exercise"
        private const val SECONDS_PER_MINUTE = 60
    }
}
