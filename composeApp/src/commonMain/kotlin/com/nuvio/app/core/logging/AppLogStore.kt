package com.nuvio.app.core.logging

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * In-app log buffer, persisted to a local file and kept for [RETENTION_MS].
 *
 * Writers (any thread) only enqueue commands; a single consumer coroutine owns the buffer and the
 * file, so no locking is needed. Updates are published at most every [PUBLISH_INTERVAL_MS].
 */
object AppLogStore {
    const val RETENTION_MS: Long = 24L * 60L * 60L * 1000L

    /** Safety caps on top of the 24 h retention, to bound memory and disk use on very chatty days. */
    internal const val MAX_ENTRIES = 20_000
    internal const val MAX_MESSAGE_CHARS = 8_000
    private const val PUBLISH_INTERVAL_MS = 300L
    private const val COMPACT_INTERVAL_MS = 15L * 60L * 1000L
    private const val COMPACT_PRUNED_THRESHOLD = 2_000
    private const val SYSTEM_IMPORT_DEDUP_WINDOW_MS = 2_000L

    private sealed interface Command {
        data class Add(val entry: PendingAppLogEntry) : Command
        data class Import(val entries: List<PendingAppLogEntry>, val added: CompletableDeferred<Int>) : Command
        data object Clear : Command
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val commands = Channel<Command>(capacity = Channel.UNLIMITED)
    private val json = Json { ignoreUnknownKeys = true }

    private val buffer = ArrayDeque<AppLogEntry>()
    private var nextId = 1L
    private var prunedSinceCompaction = 0
    private var lastCompactionMs = 0L

    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    @Volatile
    private var started = false

    private val captureState = MutableStateFlow(false)

    /** Set when the buffer changed while nobody was observing [entries]. */
    private var unpublished = false

    /** Whether new log lines are recorded; the stored history stays readable either way. */
    @Volatile
    var captureEnabled: Boolean = false
        internal set(value) {
            field = value
            captureState.value = value
        }

    internal fun start() {
        if (started) return
        started = true
        scope.launch {
            // The stored history is read only when it is needed (recording on, or the viewer open):
            // with the logger off the app does no log file work at startup.
            combine(captureState, _entries.subscriptionCount) { capture, subscribers -> capture || subscribers > 0 }
                .first { it }
            loadPersisted()
            launch {
                // The viewer opened after changes made while nobody was looking: publish them now.
                _entries.subscriptionCount.collect { subscribers ->
                    if (subscribers > 0 && unpublished) publish()
                }
            }
            while (true) {
                val batch = mutableListOf(commands.receive())
                while (true) batch += commands.tryReceive().getOrNull() ?: break
                process(batch)
                delay(PUBLISH_INTERVAL_MS)
            }
        }
    }

    internal fun record(entry: PendingAppLogEntry) {
        if (!started || !captureEnabled) return
        commands.trySend(Command.Add(entry))
    }

    /** Imports system log lines, skipping those already stored; returns how many were added. */
    internal suspend fun import(entries: List<PendingAppLogEntry>): Int {
        if (!started) return 0
        val added = CompletableDeferred<Int>()
        commands.trySend(Command.Import(entries, added))
        return added.await()
    }

    fun clear() {
        if (!started) return
        commands.trySend(Command.Clear)
    }

    private fun process(batch: List<Command>) {
        val appended = mutableListOf<AppLogEntry>()
        var needsRewrite = false
        for (command in batch) {
            when (command) {
                is Command.Add -> appended += addToBuffer(command.entry)
                is Command.Import -> {
                    val added = importToBuffer(command.entries)
                    // Imported lines are older than the newest ones: re-sort and rewrite the file.
                    if (added > 0) needsRewrite = true
                    command.added.complete(added)
                }
                Command.Clear -> {
                    buffer.clear()
                    appended.clear()
                    prunedSinceCompaction = 0
                    AppLogFileStorage.clear()
                }
            }
        }

        val now = nowEpochMs()
        prune(now)
        if (needsRewrite || shouldCompact(now)) {
            rewriteFile(now)
        } else if (appended.isNotEmpty()) {
            val survivors = appended.filter { it.id >= (buffer.firstOrNull()?.id ?: Long.MAX_VALUE) }
            runCatching { AppLogFileStorage.append(encode(survivors)) }
        }
        // Copying the whole buffer (up to MAX_ENTRIES) is only worth it for an open viewer.
        if (_entries.subscriptionCount.value > 0) publish() else unpublished = true
    }

    private fun publish() {
        unpublished = false
        _entries.value = buffer.toList()
    }

    private fun addToBuffer(pending: PendingAppLogEntry): AppLogEntry {
        val entry = pending.toEntry(nextId++)
        buffer.addLast(entry)
        return entry
    }

    private fun importToBuffer(imported: List<PendingAppLogEntry>): Int {
        if (imported.isEmpty()) return 0
        val minEpoch = nowEpochMs() - RETENTION_MS
        // Index of what is already stored, to skip system lines that mirror an app log or a previous import.
        val known = HashMap<String, MutableList<Long>>()
        for (entry in buffer) {
            known.getOrPut(dedupKey(entry.tag, entry.message)) { mutableListOf() } += entry.epochMs
        }
        val accepted = imported
            .filter { it.epochMs >= minEpoch }
            .map { it.toEntry(nextId++) }
            .filter { line ->
                val times = known[dedupKey(line.tag, line.message)]
                times == null || times.none { abs(it - line.epochMs) <= SYSTEM_IMPORT_DEDUP_WINDOW_MS }
            }
        if (accepted.isEmpty()) return 0
        val merged = (buffer + accepted).sortedWith(
            compareBy<AppLogEntry> { it.epochMs }.thenBy { it.id },
        )
        buffer.clear()
        buffer.addAll(merged)
        return accepted.size
    }

    private fun prune(now: Long) {
        val minEpoch = now - RETENTION_MS
        while (buffer.isNotEmpty() && (buffer.first().epochMs < minEpoch || buffer.size > MAX_ENTRIES)) {
            buffer.removeFirst()
            prunedSinceCompaction++
        }
    }

    private fun shouldCompact(now: Long): Boolean =
        prunedSinceCompaction >= COMPACT_PRUNED_THRESHOLD ||
            (prunedSinceCompaction > 0 && now - lastCompactionMs >= COMPACT_INTERVAL_MS)

    private fun rewriteFile(now: Long) {
        runCatching { AppLogFileStorage.overwrite(encode(buffer)) }
        prunedSinceCompaction = 0
        lastCompactionMs = now
    }

    private fun loadPersisted() {
        val now = nowEpochMs()
        val stored = runCatching { AppLogFileStorage.readText() }.getOrNull().orEmpty()
        val decoded = stored.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { json.decodeFromString(AppLogEntry.serializer(), line) }.getOrNull() }
            .toList()
        buffer.addAll(decoded)
        nextId = (decoded.maxOfOrNull { it.id } ?: 0L) + 1L
        prune(now)
        // Always compact on start: drops expired lines and any line corrupted by a crash mid-write.
        rewriteFile(now)
        _entries.value = buffer.toList()
    }

    private fun encode(entries: Collection<AppLogEntry>): String =
        buildString {
            for (entry in entries) {
                append(json.encodeToString(AppLogEntry.serializer(), entry))
                append('\n')
            }
        }

    private fun PendingAppLogEntry.toEntry(id: Long) = AppLogEntry(
        id = id,
        epochMs = epochMs,
        level = level,
        tag = tag.ifBlank { DEFAULT_TAG },
        message = AppLogRedactor.redact(message).take(MAX_MESSAGE_CHARS),
        throwable = throwable?.let { AppLogRedactor.redact(it).take(MAX_MESSAGE_CHARS) },
        source = source,
    )

    private fun dedupKey(tag: String, message: String) = "$tag\u0000${message.trim()}"

    internal const val DEFAULT_TAG = "App"

    internal fun nowEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
