package com.example.workouttimer.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttimer.data.ExerciseTrend
import com.example.workouttimer.data.WorkoutHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrendChartGestureTest {
    @Test
    fun pinchChangesTimeScaleWithoutSelectingAPoint() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val chart = TrendChartView(instrumentation.targetContext)
            chart.trend = ExerciseTrend(WorkoutHistory.sessions(recordSamples().orEmpty(), "Bench Press"))
            val density = chart.resources.displayMetrics.density
            chart.layout(0, 0, (320 * density).toInt(), (180 * density).toInt())
            var taps = 0
            chart.onPointSelected = { taps++ }

            pinch(chart, listOf(.11f, .22f, .33f, .41f))
            val expanded = chart.zoomFactor
            assertTrue("Spreading should show a shorter time range", expanded > 1)

            pinch(chart, listOf(.41f, .33f, .22f, .11f, .04f))
            assertTrue("Pinching should show a longer time range", chart.zoomFactor < 1)
            assertEquals(0, taps)
        }
    }

    private fun pinch(chart: TrendChartView, fractions: List<Float>) {
        val spans = fractions.map { it * chart.width }
        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime

        fun send(action: Int, vararg positions: Float) {
            val properties = Array(positions.size) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(positions.size) { index ->
                MotionEvent.PointerCoords().apply {
                    x = positions[index]
                    y = chart.height / 2f
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                downTime, eventTime, action, positions.size, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            chart.dispatchTouchEvent(event)
            event.recycle()
            eventTime += 20
        }

        val center = chart.width * .59f
        send(MotionEvent.ACTION_DOWN, center - spans.first())
        val secondFinger = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        send(MotionEvent.ACTION_POINTER_DOWN or secondFinger, center - spans.first(), center + spans.first())
        for (span in spans.drop(1)) {
            send(MotionEvent.ACTION_MOVE, center - span, center + span)
        }
        send(MotionEvent.ACTION_POINTER_UP or secondFinger, center - spans.last(), center + spans.last())
        send(MotionEvent.ACTION_UP, center - spans.last())
    }
}
