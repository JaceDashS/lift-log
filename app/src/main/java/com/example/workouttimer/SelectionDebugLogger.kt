package com.example.workouttimer

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object SelectionDebugLogger {
    private const val TAG = "SelectionDebug"
    private const val LOG_FILE_NAME = "selection-debug.log"
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun log(context: Context, source: String, message: String) {
        val line = "${timestampFormatter.format(LocalDateTime.now())} [$source] $message"
        Log.d(TAG, line)
        runCatching {
            File(context.filesDir, LOG_FILE_NAME).appendText(line + System.lineSeparator())
        }
    }

    fun logPath(context: Context): String {
        return File(context.filesDir, LOG_FILE_NAME).absolutePath
    }

    fun clear(context: Context): String {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
        return file.absolutePath
    }
}
