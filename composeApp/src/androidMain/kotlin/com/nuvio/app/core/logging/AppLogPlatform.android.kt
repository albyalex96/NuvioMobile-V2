package com.nuvio.app.core.logging

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal actual object AppLogFileStorage {
    private var file: File? = null

    fun initialize(context: Context) {
        file = File(File(context.filesDir, "app_logs"), "app_log.jsonl")
    }

    actual fun readText(): String? = file?.takeIf { it.isFile }?.readText()

    actual fun append(text: String) {
        val target = file ?: return
        target.parentFile?.mkdirs()
        target.appendText(text)
    }

    actual fun overwrite(text: String) {
        val target = file ?: return
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }

    actual fun clear() {
        file?.delete()
    }
}

internal actual object AppLoggerSettingsStorage {
    private const val PREFERENCES_NAME = "nuvio_app_logger"
    private const val ENABLED_KEY = "app_logger_enabled"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(ENABLED_KEY)) prefs.getBoolean(ENABLED_KEY, false) else null
        }

    actual fun saveEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(ENABLED_KEY, enabled)?.apply()
    }
}

internal actual object AppLogPlatform {
    actual val supportsSystemLogImport: Boolean = true

    /** `threadtime` format: `MM-DD HH:MM:SS.mmm  PID  TID L TAG: message`. */
    private val threadTimeLine = Regex(
        """^(\d\d)-(\d\d)\s+(\d\d):(\d\d):(\d\d)\.(\d{3})\s+\d+\s+\d+\s+([VDIWEFA])\s+(.*?)\s*:\s?(.*)$""",
    )

    fun initialize(context: Context) {
        AppLogFileStorage.initialize(context)
        AppLoggerSettingsStorage.initialize(context)
        initializeAppLogExportDirectory(context)
    }

    /** Only the app's own process is readable (no permission needed since Android 4.1). */
    actual fun readSystemLog(): List<PendingAppLogEntry> {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}",
        ).redirectErrorStream(true).start()
        val now = System.currentTimeMillis()
        val lines = process.inputStream.bufferedReader().useLines { sequence ->
            sequence.mapNotNull { parseLine(it, now) }.toList()
        }
        process.waitFor()
        return lines
    }

    private fun parseLine(line: String, now: Long): PendingAppLogEntry? {
        val match = threadTimeLine.matchEntire(line) ?: return null
        val values = match.groupValues
        val calendar = Calendar.getInstance().apply {
            set(Calendar.MONTH, values[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, values[2].toInt())
            set(Calendar.HOUR_OF_DAY, values[3].toInt())
            set(Calendar.MINUTE, values[4].toInt())
            set(Calendar.SECOND, values[5].toInt())
            set(Calendar.MILLISECOND, values[6].toInt())
            // logcat omits the year: a date "in the future" belongs to the previous year.
            if (timeInMillis > now + 60_000L) add(Calendar.YEAR, -1)
        }
        val level = when (values[7]) {
            "V", "D" -> AppLogLevel.DEBUG
            "I" -> AppLogLevel.INFO
            "W" -> AppLogLevel.WARN
            else -> AppLogLevel.ERROR
        }
        return PendingAppLogEntry(
            epochMs = calendar.timeInMillis,
            level = level,
            tag = values[8].trim(),
            message = values[9],
            source = AppLogSource.SYSTEM,
        )
    }

    actual fun formatTimestamp(epochMs: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(epochMs))
}
