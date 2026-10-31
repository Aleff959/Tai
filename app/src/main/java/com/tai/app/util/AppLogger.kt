package com.tai.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, ACTION, ERROR
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

object AppLogger {
    private const val MAX_LOGS = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun i(tag: String, message: String) {
        Log.i("TaiApp-$tag", message)
        append(LogLevel.INFO, tag, message)
    }

    fun action(tag: String, message: String) {
        Log.d("TaiApp-$tag", "[AÇÃO] $message")
        append(LogLevel.ACTION, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("TaiApp-$tag", message, throwable)
        val trace = throwable?.stackTraceToString()
        append(LogLevel.ERROR, tag, "$message ${throwable?.message ?: ""}".trim(), trace)
    }

    private fun append(level: LogLevel, tag: String, message: String, stackTrace: String? = null) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )
        val updated = (_logs.value + entry).takeLast(MAX_LOGS)
        _logs.value = updated
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsFormatted(): String {
        return _logs.value.joinToString("\n") { entry ->
            val traceStr = if (entry.stackTrace != null) "\n  --> ${entry.stackTrace.take(400)}" else ""
            "[${entry.timestamp}] [${entry.level}] [${entry.tag}]: ${entry.message}$traceStr"
        }
    }
}
