package com.example.workouttimer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.workouttimer.R

/**
 * Line chart of average reps per session.
 *
 * [Spacing.PER_SESSION] (default) gives every session its own slot, so every slot has a dot.
 * [Spacing.PER_DAY] gives every calendar day a slot: days without a session have no dot,
 * but the line still joins the sessions on either side.
 */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Spacing { PER_SESSION, PER_DAY }

    /** [dayIndex] counts days from the first session; [label] is drawn under the first and last point. */
    data class Point(val dayIndex: Int, val value: Float, val label: String)

    var spacing: Spacing = Spacing.PER_SESSION
        set(value) {
            field = value
            invalidate()
        }

    var points: List<Point> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_acid)
        style = Paint.Style.STROKE
        strokeWidth = dp(LINE_WIDTH_DP)
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_acid)
    }
    private val dotHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_surface)
    }
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.lift_line)
        strokeWidth = dp(GRID_WIDTH_DP)
    }
    private val dayTickPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.lift_line)
        strokeWidth = dp(GRID_WIDTH_DP)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_text_dim)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, LABEL_SIZE_SP, resources.displayMetrics
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val labelHeight = labelPaint.textSize + dp(LABEL_GAP_DP)
        val left = dp(AXIS_WIDTH_DP)
        val right = width - dp(EDGE_DP)
        val top = dp(EDGE_DP)
        val bottom = height - labelHeight

        val minValue = points.minOf { it.value } - VALUE_PADDING
        val maxValue = points.maxOf { it.value } + VALUE_PADDING
        fun yOf(value: Float) = bottom - (value - minValue) / (maxValue - minValue) * (bottom - top)

        val lastDay = points.last().dayIndex.coerceAtLeast(1)
        fun xOf(index: Int, point: Point): Float {
            val fraction = when (spacing) {
                Spacing.PER_SESSION -> index.toFloat() / (points.size - 1)
                Spacing.PER_DAY -> point.dayIndex.toFloat() / lastDay
            }
            return left + fraction * (right - left)
        }

        for (value in listOf(maxValue - VALUE_PADDING, minValue + VALUE_PADDING)) {
            val y = yOf(value)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(formatValue(value), 0f, y + labelPaint.textSize / 3, labelPaint)
        }
        if (spacing == Spacing.PER_DAY) {
            for (day in 0..lastDay) {
                val x = left + day.toFloat() / lastDay * (right - left)
                canvas.drawLine(x, bottom, x, bottom - dp(DAY_TICK_DP), dayTickPaint)
            }
        }

        val coords = points.mapIndexed { index, point -> xOf(index, point) to yOf(point.value) }
        for (i in 1 until coords.size) {
            val (x0, y0) = coords[i - 1]
            val (x1, y1) = coords[i]
            canvas.drawLine(x0, y0, x1, y1, linePaint)
        }
        for ((x, y) in coords) {
            canvas.drawCircle(x, y, dp(DOT_RADIUS_DP), dotPaint)
            canvas.drawCircle(x, y, dp(DOT_HOLE_RADIUS_DP), dotHolePaint)
        }

        val labelY = height.toFloat() - dp(EDGE_DP)
        canvas.drawText(points.first().label, coords.first().first - dp(DOT_RADIUS_DP), labelY, labelPaint)
        val lastLabel = points.last().label
        canvas.drawText(lastLabel, right - labelPaint.measureText(lastLabel), labelY, labelPaint)
    }

    private fun formatValue(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val LINE_WIDTH_DP = 3f
        const val GRID_WIDTH_DP = 1f
        const val DOT_RADIUS_DP = 5f
        const val DOT_HOLE_RADIUS_DP = 2f
        const val DAY_TICK_DP = 6f
        const val AXIS_WIDTH_DP = 28f
        const val EDGE_DP = 6f
        const val LABEL_GAP_DP = 12f
        const val LABEL_SIZE_SP = 11f
        const val VALUE_PADDING = 0.5f
    }
}
