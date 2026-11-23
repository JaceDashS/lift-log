package com.example.workouttimer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.example.workouttimer.R
import com.example.workouttimer.data.ExerciseTrend
import com.example.workouttimer.data.ExerciseTrend.Metric
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.ceil

/** Scrolls the historical points within a fixed percentage axis. */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Spacing { PER_SESSION, PER_DAY }

    internal var trend = ExerciseTrend(emptyList())
        set(value) {
            field = value
            dates = trend.points.map { it.date.toString().takeLast(5) }
            updateRange()
        }

    var spacing = Spacing.PER_DAY
        set(value) {
            if (field == value) return
            field = value
            revealSelection()
            invalidate()
        }

    var onPointSelected: ((Int) -> Unit)? = null
    var selectedIndex = -1
        set(value) {
            field = value
            invalidate()
        }

    internal var visibleMetrics = Metric.entries.toSet()
        set(value) {
            field = value
            updateRange()
        }

    var scrollFromLatestDp: Float
        get() = (offsetFromEnd / resources.displayMetrics.density).toFloat()
        set(value) {
            offsetFromEnd = dp(value).toDouble().coerceAtLeast(0.0)
            if (width > 0) offsetFromEnd = endOffset
            invalidate()
        }

    internal var zoomFactor = 1.0
        set(value) {
            field = value
            invalidate()
        }

    private var dates = emptyList<String>()
    private var low = -10.0
    private var high = 10.0
    private var offsetFromEnd = 0.0
    private var dragging = false
    private var multiTouch = false
    private var hasValues = false

    private val plotLeft get() = dp(53f)
    private val plotRight get() = (width - dp(7f)).coerceAtLeast(plotLeft + 1f)
    private val plotWidth get() = (plotRight - plotLeft).toDouble()
    private val slotCount get() = when (spacing) {
        Spacing.PER_SESSION -> trend.points.lastIndex.toLong().coerceAtLeast(0)
        Spacing.PER_DAY -> trend.points.lastOrNull()?.dayIndex ?: 0L
    }
    private val fitPixelsPerSlot get() = plotWidth / slotCount.coerceAtLeast(1)
    private val defaultPixelsPerSlot get() = maxOf(
        dp(if (spacing == Spacing.PER_DAY) 22f else 56f).toDouble(),
        fitPixelsPerSlot
    )
    private val pixelsPerSlot get() = maxOf(fitPixelsPerSlot, defaultPixelsPerSlot * zoomFactor)
    private val maxOffset get() = (slotCount * pixelsPerSlot - plotWidth).coerceAtLeast(0.0)
    private val endOffset get() = offsetFromEnd.coerceIn(0.0, maxOffset)
    private val scrollLeft get() = maxOffset - endOffset

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_line)
        strokeWidth = dp(1f)
    }
    private val selection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_text_dim)
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_text_dim)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lift_surface)
    }
    private val colors = Metric.entries.associateWith { ContextCompat.getColor(context, it.chartColor) }
    private val strokes = mapOf(
        Metric.REPS to DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f),
        Metric.REST to DashPathEffect(floatArrayOf(dp(1f), dp(5f)), 0f),
    )
    private val percentages = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }
    private val scaleGestures = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = slotCount > 0

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focus = detector.focusX.coerceIn(plotLeft, plotRight) - plotLeft
            val anchorSlot = (scrollLeft + focus) / pixelsPerSlot
            zoomFactor = (pixelsPerSlot * detector.scaleFactor / defaultPixelsPerSlot)
                .coerceIn(fitPixelsPerSlot / defaultPixelsPerSlot, 8.0)
            val left = (anchorSlot * pixelsPerSlot - focus).coerceIn(0.0, maxOffset)
            offsetFromEnd = maxOffset - left
            invalidate()
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            dragging = false
            return trend.points.isNotEmpty()
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null || maxOffset == 0.0) return false
            if (!dragging && abs(e2.x - e1.x) <= abs(e2.y - e1.y)) return false
            dragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
            scrollByDistance(distanceX.toDouble())
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.x < plotLeft - dp(6f) || trend.points.isEmpty()) return false
            val index = visibleIndices().filter { xOf(it) in plotLeft - 1f..plotRight + 1f }.minWithOrNull(
                compareBy<Int> { abs(xOf(it) - e.x) }.thenBy { distanceToSeries(it, e.y) }
            ) ?: return false
            onPointSelected?.invoke(index)
            performClick()
            return true
        }
    })

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.trend_accessible)
    }

    fun revealSelection() {
        if (selectedIndex !in trend.points.indices || width == 0) return
        if (selectedIndex == trend.points.lastIndex) {
            offsetFromEnd = 0.0
        } else {
            val x = xOf(selectedIndex)
            val visibleX = x.coerceIn(plotLeft, plotRight)
            scrollByDistance((x - visibleX).toDouble())
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val message = when {
            trend.points.isEmpty() -> R.string.empty_records
            visibleMetrics.isEmpty() -> R.string.trend_no_visible_metrics
            !hasValues -> R.string.trend_no_metric_data
            else -> null
        }
        if (message != null) {
            canvas.drawText(context.getString(message), dp(4f), height / 2f, label)
            return
        }

        val indices = visibleIndices()
        for (value in listOf(0.0, high, low).distinct()) {
            val y = yOf(value)
            canvas.drawLine(plotLeft, y, plotRight, y, grid)
            if (value == 0.0 || abs(y - yOf(0.0)) >= label.textSize) {
                val text = if (abs(value) < 10000) percentages.format(value / 100) else "%1.0e%%".format(value)
                canvas.drawText(text, plotLeft - label.measureText(text) - dp(6f), y + label.textSize / 3, label)
            }
        }

        val checkpoint = canvas.save()
        canvas.clipRect(plotLeft - dp(5f), 0f, plotRight + dp(5f), height.toFloat())
        if (spacing == Spacing.PER_DAY && slotCount > 0) drawDayTicks(canvas)
        if (selectedIndex in trend.points.indices) {
            val x = xOf(selectedIndex)
            canvas.drawLine(x, plotTop, x, plotBottom, selection)
        }
        for (metric in visibleMetrics) {
            drawSeries(canvas, metric, indices)
        }
        canvas.restoreToCount(checkpoint)
        drawDates(canvas, indices)
    }

    private val plotTop get() = dp(12f)
    private val plotBottom get() = (height - dp(24f)).coerceAtLeast(plotTop + 1f)

    private fun yOf(value: Double): Float =
        plotBottom - ((value - low) / (high - low)).toFloat() * (plotBottom - plotTop)

    private fun drawSeries(canvas: Canvas, metric: Metric, indices: IntRange) {
        val values = trend.series.getValue(metric).changes
        line.color = colors.getValue(metric)
        line.pathEffect = strokes[metric]
        dot.color = line.color
        var previous: Int? = null
        for (index in indices) {
            val value = values[index]
            if (value == null) {
                previous = null
                continue
            }
            val x = xOf(index)
            val y = yOf(value)
            previous?.let { canvas.drawLine(xOf(it), yOf(requireNotNull(values[it])), x, y, line) }
            val radius = dp(if (index == selectedIndex) 4.5f else 3f)
            canvas.drawCircle(x, y, radius, dot)
            if (index == selectedIndex) canvas.drawCircle(x, y, dp(1.8f), hole)
            previous = index
        }
    }

    private fun drawDates(canvas: Canvas, indices: IntRange) {
        var lastLabelEnd = Float.NEGATIVE_INFINITY
        for (index in indices) {
            val x = xOf(index)
            if (x !in plotLeft..plotRight) continue
            val text = dates[index]
            val width = label.measureText(text)
            val start = (x - width / 2).coerceIn(plotLeft, (plotRight - width).coerceAtLeast(plotLeft))
            if (start < lastLabelEnd + dp(9f)) continue
            canvas.drawText(text, start, height - dp(4f), label)
            lastLabelEnd = start + width
        }
    }

    private fun drawDayTicks(canvas: Canvas) {
        val first = ceil(scrollLeft / pixelsPerSlot).toLong()
        val last = ((scrollLeft + plotWidth) / pixelsPerSlot).toLong().coerceAtMost(slotCount)
        val step = ceil(dp(8f) / pixelsPerSlot).toLong().coerceAtLeast(1)
        val firstTick = (first + step - 1) / step * step
        for (day in firstTick..last step step) {
            val x = plotLeft + (day * pixelsPerSlot - scrollLeft).toFloat()
            canvas.drawLine(x, plotBottom, x, plotBottom + dp(4f), grid)
        }
    }

    private fun distanceToSeries(index: Int, y: Float): Float = visibleMetrics
        .mapNotNull { trend.series.getValue(it).changes[index] }
        .minOfOrNull { abs(yOf(it) - y) } ?: Float.POSITIVE_INFINITY

    private fun visibleIndices(): IntRange {
        if (trend.points.isEmpty()) return IntRange.EMPTY
        if (slotCount == 0L) return trend.points.indices
        val left = firstIndexAtOrAfter(scrollLeft / pixelsPerSlot)
        val right = firstIndexAtOrAfter((scrollLeft + plotWidth + 1) / pixelsPerSlot)
        return (left - 1).coerceAtLeast(0)..right.coerceAtMost(trend.points.lastIndex)
    }

    private fun firstIndexAtOrAfter(slot: Double): Int {
        var start = 0
        var end = trend.points.size
        while (start < end) {
            val mid = start + (end - start) / 2
            if (slotOf(mid) < slot) start = mid + 1 else end = mid
        }
        return start
    }

    private fun xOf(index: Int): Float = if (slotCount == 0L) {
        (plotLeft + plotRight) / 2
    } else {
        plotLeft + (slotOf(index) * pixelsPerSlot - scrollLeft).toFloat()
    }

    private fun slotOf(index: Int): Long =
        if (spacing == Spacing.PER_DAY) trend.points[index].dayIndex else index.toLong()

    private fun updateRange() {
        val values = visibleMetrics.flatMap { trend.series.getValue(it).changes }.filterNotNull()
        hasValues = values.isNotEmpty()
        low = minOf(-10.0, values.minOrNull() ?: 0.0)
        high = maxOf(10.0, values.maxOrNull() ?: 0.0)
        invalidate()
    }

    private fun scrollByDistance(delta: Double): Boolean {
        val next = (endOffset - delta).coerceIn(0.0, maxOffset)
        if (next == endOffset) return false
        offsetFromEnd = next
        invalidate()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) multiTouch = false
        if (!multiTouch && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true
            parent?.requestDisallowInterceptTouchEvent(true)
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            gestures.onTouchEvent(cancel)
            cancel.recycle()
        }

        scaleGestures.onTouchEvent(event)
        if (multiTouch) return true
        return gestures.onTouchEvent(event) ||
            (event.actionMasked != MotionEvent.ACTION_DOWN && trend.points.isNotEmpty())
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = maxOffset > 0.0
        if (endOffset > 0.0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (endOffset < maxOffset) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id -> scrollByDistance(plotWidth)
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id -> scrollByDistance(-plotWidth)
        else -> super.performAccessibilityAction(action, arguments)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

internal val Metric.chartColor: Int get() = when (this) {
    Metric.WEIGHT -> R.color.lift_acid
    Metric.REPS -> R.color.lift_trend_reps
    Metric.REST -> R.color.lift_trend_rest
}
