package com.nuvio.app.core.logging

/** Local, device-only file where the log entries are persisted (one JSON object per line). */
internal expect object AppLogFileStorage {
    fun readText(): String?
    fun append(text: String)
    fun overwrite(text: String)
    fun clear()
}

/** Local storage of the "record logs" toggle (also synced with the profile, see [AppLoggerSettings]). */
internal expect object AppLoggerSettingsStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
}

internal expect object AppLogPlatform {
    /** True where the app can read its own system log (Android logcat). */
    val supportsSystemLogImport: Boolean

    /** Reads the app's own system log lines; empty where unsupported. Blocking: call off the main thread. */
    fun readSystemLog(): List<PendingAppLogEntry>

    /** Formats [epochMs] in the device time zone with an ICU/SimpleDateFormat [pattern]. */
    fun formatTimestamp(epochMs: Long, pattern: String): String
}
