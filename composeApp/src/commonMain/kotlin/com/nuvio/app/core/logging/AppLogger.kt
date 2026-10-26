package com.nuvio.app.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Entry point of the in-app logger: hooks a Kermit [LogWriter] into the global logger, so every
 * `Logger`/`Logger.withTag(...)` call of the app also lands in [AppLogStore].
 *
 * Platforms call [install] once at startup (Android: `NuvioApplication`, iOS: the root view controller),
 * after the platform storage is ready.
 */
object AppLogger {
    @Volatile
    private var installed = false

    @Volatile
    private var debuggable = false

    /**
     * [debuggable] keeps every level on the system log (logcat) and always evaluated. In release
     * builds the debug/info lines are built only while "Record logs" is on: with the logger off the
     * `log.d { }` lambdas are skipped and logcat only receives warnings and errors.
     */
    fun install(debuggable: Boolean = false) {
        if (installed) return
        this.debuggable = debuggable
        Logger.setLogWriters(SystemLogWriter(platformLogWriter(), debuggable), AppLogWriter)
        installed = true
        AppLoggerSettings.ensureLoaded()
        refreshMinSeverity()
        AppLogStore.start()
    }

    internal fun refreshMinSeverity() {
        if (!installed) return
        val minSeverity = if (debuggable || AppLogStore.captureEnabled) Severity.Verbose else Severity.Warn
        Logger.setMinSeverity(minSeverity)
    }
}

/** The platform writer (logcat / OSLog), limited to warnings and errors in release builds. */
private class SystemLogWriter(
    private val delegate: LogWriter,
    private val debuggable: Boolean,
) : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean =
        (debuggable || severity >= Severity.Warn) && delegate.isLoggable(tag, severity)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        delegate.log(severity, message, tag, throwable)
    }
}

private object AppLogWriter : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean = AppLogStore.captureEnabled

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        AppLogStore.record(
            PendingAppLogEntry(
                epochMs = AppLogStore.nowEpochMs(),
                level = severity.toAppLogLevel(),
                tag = tag,
                message = message,
                throwable = throwable?.stackTraceToString(),
            ),
        )
    }
}

internal fun Severity.toAppLogLevel(): AppLogLevel =
    when (this) {
        Severity.Verbose, Severity.Debug -> AppLogLevel.DEBUG
        Severity.Info -> AppLogLevel.INFO
        Severity.Warn -> AppLogLevel.WARN
        Severity.Error, Severity.Assert -> AppLogLevel.ERROR
    }

/**
 * "Record logs" toggle (default off). Stored locally and synced with the profile inside the existing
 * `stream_badge_settings` payload (like the other Enhanced toggles), leaving the blob schema as is.
 * The logs themselves and the viewer filters are never synced.
 */
object AppLoggerSettings {
    private const val ENABLED_SYNC_KEY = "app_logger_enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        apply(AppLoggerSettingsStorage.loadEnabled() ?: false)
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_enabled.value == enabled) return
        apply(enabled)
        AppLoggerSettingsStorage.saveEnabled(enabled)
    }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        ensureLoaded()
        put(ENABLED_SYNC_KEY, encodeSyncBoolean(_enabled.value))
    }

    /** Applies the remote value only when present, so blobs from older clients keep the local choice. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val remote = payload.decodeSyncBoolean(ENABLED_SYNC_KEY) ?: return
        setEnabled(remote)
    }

    private fun apply(enabled: Boolean) {
        _enabled.value = enabled
        AppLogStore.captureEnabled = enabled
        AppLogger.refreshMinSeverity()
    }
}
