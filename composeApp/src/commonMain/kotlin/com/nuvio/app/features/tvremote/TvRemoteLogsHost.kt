package com.nuvio.app.features.tvremote

import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.AppLogEntry
import com.nuvio.app.core.logging.AppLogPlatform
import com.nuvio.app.core.logging.AppLogStore
import com.nuvio.app.core.logging.AppLoggerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * TV side of the "TV logs" page of the phone: answers [TV_REMOTE_PATH_LOGS] requests.
 * [AppLogStore] publishes its entries only while observed, so the host keeps a subscription
 * open while a phone is reading them (closed after [IDLE_RELEASE_MS] without requests).
 */
object TvRemoteLogsHost {
    private val log = Logger.withTag("TvRemoteLogs")
    private const val IDLE_RELEASE_MS = 60_000L
    private const val FIRST_LOAD_WAIT_MS = 1_500L
    private const val MAX_LIMIT = 5_000
    /** Keeps one response within a couple of MB. */
    private const val MAX_RESPONSE_CHARS = 2_000_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var subscription: Job? = null
    private var lastRequestMs = 0L

    suspend fun handle(request: TvRemoteLogsRequest): TvRemoteLogsResponse {
        AppLoggerSettings.ensureLoaded()
        keepSubscribed()
        var imported: Int? = null
        var reset = false
        when (request.action) {
            TvRemoteLogsAction.EnableRecording -> {
                log.i { "Log recording turned on from the phone" }
                withContext(Dispatchers.Main) { AppLoggerSettings.setEnabled(true) }
            }
            TvRemoteLogsAction.DisableRecording -> {
                log.i { "Log recording turned off from the phone" }
                withContext(Dispatchers.Main) { AppLoggerSettings.setEnabled(false) }
            }
            TvRemoteLogsAction.Clear -> {
                log.i { "Logs cleared from the phone" }
                AppLogStore.clear()
                withTimeoutOrNull(FIRST_LOAD_WAIT_MS) { AppLogStore.entries.first { it.isEmpty() } }
                reset = true
            }
            TvRemoteLogsAction.ImportSystemLog -> {
                val lines = withContext(Dispatchers.Default) {
                    runCatching { AppLogPlatform.readSystemLog() }
                        .onFailure { log.w(it) { "System log import from the phone failed" } }
                        .getOrNull()
                }
                if (lines != null) {
                    imported = AppLogStore.import(lines)
                    log.i { "System log imported from the phone: $imported lines" }
                    withTimeoutOrNull(FIRST_LOAD_WAIT_MS) { AppLogStore.entries.first { it.size >= imported } }
                }
                reset = true
            }
            null -> Unit
        }

        var all: List<AppLogEntry> = AppLogStore.entries.value
        if (all.isEmpty() && AppLoggerSettings.enabled.value) {
            all = withTimeoutOrNull(FIRST_LOAD_WAIT_MS) { AppLogStore.entries.first { it.isNotEmpty() } } ?: all
        }
        val afterId = if (reset) 0L else request.afterId
        val newestId = all.maxOfOrNull { it.id } ?: 0L
        val limit = request.limit.coerceIn(1, MAX_LIMIT)
        val pending = all.filter { it.id > afterId }.sortedBy { it.id }
        val page = ArrayList<AppLogEntry>(minOf(limit, pending.size))
        var chars = 0
        for (entry in pending) {
            if (page.size >= limit) break
            chars += entry.message.length + (entry.throwable?.length ?: 0) + entry.tag.length + 48
            if (page.isNotEmpty() && chars > MAX_RESPONSE_CHARS) break
            page += entry
        }
        return TvRemoteLogsResponse(
            entries = page,
            newestId = newestId,
            hasMore = page.size < pending.size,
            recording = AppLoggerSettings.enabled.value,
            supportsSystemImport = AppLogPlatform.supportsSystemLogImport,
            imported = imported,
            reset = reset,
        )
    }

    private fun keepSubscribed() {
        lastRequestMs = AppLogStore.nowEpochMs()
        if (subscription?.isActive == true) return
        log.d { "Phone reading the TV logs: observing the log store" }
        subscription = scope.launch {
            val collector = launch { AppLogStore.entries.collect { } }
            while (AppLogStore.nowEpochMs() - lastRequestMs < IDLE_RELEASE_MS) delay(5_000L)
            collector.cancel()
            log.d { "No phone reading the TV logs: observation released" }
        }
    }
}
