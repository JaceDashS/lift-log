package com.example.workouttimer.ui

import java.util.Locale

/** Elapsed milliseconds rendered as unbounded minutes and two digit seconds. */
fun duration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}

/** Compact decimal display for weights and average repetitions. */
fun decimal(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.getDefault(), "%.1f", value)
