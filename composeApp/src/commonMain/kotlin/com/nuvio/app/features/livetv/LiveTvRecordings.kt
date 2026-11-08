package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.features.settings.ThemeSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime

@Serializable
enum class LiveTvRecordingStatus { Scheduled, Recording, Completed, Failed }

/** Where a recording was started from (for the logs and the list). */
@Serializable
enum class LiveTvRecordingSource { Manual, Epg, Sports }

/** One recording: a channel between two times, saved as a .ts file. */
@Serializable
data class LiveTvRecording(
    val id: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val channelLogoUrl: String? = null,
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val stalkerCommand: String? = null,
    val playlistId: String? = null,
    val startMillis: Long,
    val endMillis: Long,
    val source: LiveTvRecordingSource = LiveTvRecordingSource.Manual,
    val status: LiveTvRecordingStatus = LiveTvRecordingStatus.Scheduled,
    val fileUri: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0L,
    val errorMessage: String? = null,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/** Margins added around EPG programs and games (they rarely start and end on time). Synced. */
@Serializable
data class LiveTvRecordingOptions(
    val marginBeforeMinutes: Int = 2,
    val marginAfterMinutes: Int = 10,
)

internal val liveTvRecordingMarginChoices = listOf(0, 2, 5, 10, 15, 30)

/** Starts, stops and stores the recordings (Android only for now). */
internal expect object LiveTvRecordingPlatform {
    val isSupported: Boolean

    fun loadIndex(): String?
    fun saveIndex(raw: String)

    /** Wakes the device at the start of [recording] (or starts it now when the start has passed). */
    fun arm(recording: LiveTvRecording)
    fun disarm(recordingId: String)
    fun stop(recordingId: String)
    fun deleteFile(fileUri: String)

    /** Label of the chosen folder; null means the app's own folder. */
    val folderLabel: StateFlow<String?>
    fun selectFolder(treeUri: String): Boolean
    fun clearFolder()

    /** Android 12+: false until the user allows exact alarms (scheduled recordings may start late). */
    fun canScheduleExactAlarms(): Boolean
    fun openExactAlarmSettings()
}

object LiveTvRecordingsRepository {
    private val log = Logger.withTag("LiveTvRecording")
    private const val OPTIONS_KEY = "live_tv_recording_options"
    private const val SYNC_KEY = "live_tv_recording_options"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(LiveTvRecording.serializer())

    private val _recordings = MutableStateFlow<List<LiveTvRecording>>(emptyList())
    val recordings: StateFlow<List<LiveTvRecording>> = _recordings.asStateFlow()

    private val _options = MutableStateFlow(LiveTvRecordingOptions())
    val options: StateFlow<LiveTvRecordingOptions> = _options.asStateFlow()

    private var loaded = false

    val isSupported: Boolean get() = LiveTvRecordingPlatform.isSupported

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _recordings.value = LiveTvRecordingPlatform.loadIndex()?.let { raw ->
            runCatching { json.decodeFromString(listSerializer, raw) }
                .onFailure { log.e(it) { "Recordings index unreadable" } }
                .getOrNull()
        }.orEmpty()
        _options.value = ThemeSettingsStorage.loadLocalString(OPTIONS_KEY)?.let { raw ->
            runCatching { json.decodeFromString(LiveTvRecordingOptions.serializer(), raw) }.getOrNull()
        } ?: LiveTvRecordingOptions()
        // A recording left "in progress" by a killed process is not running any more.
        val now = Clock.System.now().toEpochMilliseconds()
        _recordings.value.filter { it.status == LiveTvRecordingStatus.Recording && now > it.endMillis }.forEach { stale ->
            update(stale.id) {
                it.copy(status = if (it.sizeBytes > 0) LiveTvRecordingStatus.Completed else LiveTvRecordingStatus.Failed)
            }
        }
    }

    fun get(id: String): LiveTvRecording? {
        ensureLoaded()
        return _recordings.value.firstOrNull { it.id == id }
    }

    /**
     * Schedules [channel] from [startMillis] to [endMillis] (margins already applied by the caller
     * when wanted). A start in the past starts the recording now.
     */
    fun schedule(
        channel: LiveTvChannel,
        title: String,
        startMillis: Long,
        endMillis: Long,
        source: LiveTvRecordingSource,
    ): LiveTvRecording? {
        ensureLoaded()
        val now = Clock.System.now().toEpochMilliseconds()
        if (endMillis <= now || endMillis <= startMillis) {
            log.w { "Recording of '$title' not scheduled: it ends in the past" }
            return null
        }
        val recording = LiveTvRecording(
            id = "rec-${now}-${Random.nextInt(1000, 9999)}",
            title = title.ifBlank { channel.name },
            channelId = channel.id,
            channelName = channel.name,
            channelLogoUrl = channel.logoUrl,
            streamUrl = channel.streamUrl,
            headers = channel.headers,
            stalkerCommand = channel.stalkerCommand,
            playlistId = channel.playlistId,
            startMillis = startMillis.coerceAtLeast(now),
            endMillis = endMillis,
            source = source,
        )
        _recordings.update { it + recording }
        save()
        log.i {
            "Recording scheduled (${source.name}): '${recording.title}' on ${channel.name}, " +
                "${(recording.startMillis - now) / 60_000} min from now, ${recording.durationMillis / 60_000} min long"
        }
        LiveTvRecordingPlatform.arm(recording)
        return recording
    }

    /** Schedules [program] of [channel] with the margins of the options. */
    fun scheduleProgram(channel: LiveTvChannel, program: LiveTvEpgProgram): LiveTvRecording? {
        val options = options.value
        return schedule(
            channel = channel,
            title = program.title,
            startMillis = program.startMillis - options.marginBeforeMinutes * MINUTE_MILLIS,
            endMillis = program.endMillis + options.marginAfterMinutes * MINUTE_MILLIS,
            source = LiveTvRecordingSource.Epg,
        )
    }

    /** Schedules a game on [channel]: its usual length for the sport, plus the margins. */
    fun scheduleGame(channel: LiveTvChannel, event: LiveTvSportsEvent): LiveTvRecording? {
        val options = options.value
        return schedule(
            channel = channel,
            title = event.name,
            startMillis = event.startMillis - options.marginBeforeMinutes * MINUTE_MILLIS,
            endMillis = event.startMillis + event.league.sport.typicalMinutes * MINUTE_MILLIS +
                options.marginAfterMinutes * MINUTE_MILLIS,
            source = LiveTvRecordingSource.Sports,
        )
    }

    /** The scheduled or running recording of [program] on [channel], if any. */
    fun findProgram(channelId: String, program: LiveTvEpgProgram): LiveTvRecording? =
        _recordings.value.firstOrNull {
            it.channelId == channelId && it.title == program.title &&
                it.status in activeStatuses && it.endMillis >= program.endMillis
        }

    /** Stops a running recording (the file is kept) or cancels a scheduled one. */
    fun stop(id: String) {
        val recording = get(id) ?: return
        when (recording.status) {
            LiveTvRecordingStatus.Scheduled -> {
                LiveTvRecordingPlatform.disarm(id)
                _recordings.update { it.filterNot { it.id == id } }
                save()
                log.i { "Scheduled recording cancelled: '${recording.title}'" }
            }
            LiveTvRecordingStatus.Recording -> {
                log.i { "Recording stopped by the user: '${recording.title}'" }
                LiveTvRecordingPlatform.stop(id)
            }
            else -> Unit
        }
    }

    /** Removes a finished or failed recording and its file. */
    fun delete(id: String) {
        val recording = get(id) ?: return
        if (recording.status in activeStatuses) {
            stop(id)
            if (recording.status == LiveTvRecordingStatus.Scheduled) return
        }
        recording.fileUri?.let { uri ->
            runCatching { LiveTvRecordingPlatform.deleteFile(uri) }
                .onFailure { log.w(it) { "Recording file not deleted" } }
        }
        _recordings.update { it.filterNot { it.id == id } }
        save()
        log.i { "Recording deleted: '${recording.title}'" }
    }

    /** Used by the platform recorder to report progress and results. */
    fun update(id: String, transform: (LiveTvRecording) -> LiveTvRecording) {
        ensureLoaded()
        _recordings.update { list -> list.map { if (it.id == id) transform(it) else it } }
        save()
    }

    /** Arms every scheduled recording again (after a reboot or an app update). */
    fun rearmAll() {
        ensureLoaded()
        val scheduled = _recordings.value.filter { it.status == LiveTvRecordingStatus.Scheduled }
        log.i { "Re-arming ${scheduled.size} scheduled recordings" }
        scheduled.forEach(LiveTvRecordingPlatform::arm)
    }

    private fun save() {
        LiveTvRecordingPlatform.saveIndex(json.encodeToString(listSerializer, _recordings.value))
    }

    fun setMarginBefore(minutes: Int) = updateOptions { it.copy(marginBeforeMinutes = minutes) }

    fun setMarginAfter(minutes: Int) = updateOptions { it.copy(marginAfterMinutes = minutes) }

    private fun updateOptions(transform: (LiveTvRecordingOptions) -> LiveTvRecordingOptions) {
        ensureLoaded()
        val updated = transform(_options.value)
        if (updated == _options.value) return
        _options.value = updated
        ThemeSettingsStorage.saveLocalString(OPTIONS_KEY, json.encodeToString(LiveTvRecordingOptions.serializer(), updated))
        log.i { "Recording options: $updated" }
    }

    fun onProfileChanged() {
        _options.value = ThemeSettingsStorage.loadLocalString(OPTIONS_KEY)?.let { raw ->
            runCatching { json.decodeFromString(LiveTvRecordingOptions.serializer(), raw) }.getOrNull()
        } ?: LiveTvRecordingOptions()
    }

    fun syncSignature(options: LiveTvRecordingOptions): String = "recording_$options"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        ensureLoaded()
        put(SYNC_KEY, encodeSyncString(json.encodeToString(LiveTvRecordingOptions.serializer(), _options.value)))
    }

    /** Payloads from older versions (no value) keep the local options. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val raw = payload.decodeSyncString(SYNC_KEY) ?: return
        val options = runCatching { json.decodeFromString(LiveTvRecordingOptions.serializer(), raw) }.getOrNull() ?: return
        _options.value = options
        ThemeSettingsStorage.saveLocalString(OPTIONS_KEY, raw)
    }

    private val activeStatuses = setOf(LiveTvRecordingStatus.Scheduled, LiveTvRecordingStatus.Recording)
}

/** Usual length of a game, for recordings started from the Sports section. */
internal val LiveTvSport.typicalMinutes: Int
    get() = when (this) {
        LiveTvSport.Soccer -> 120
        LiveTvSport.Basketball -> 150
        LiveTvSport.AmericanFootball -> 210
        LiveTvSport.Hockey -> 165
        LiveTvSport.Baseball -> 195
        LiveTvSport.Rugby, LiveTvSport.AussieRules -> 130
        LiveTvSport.Cricket -> 240
        LiveTvSport.Fighting -> 240
        LiveTvSport.Racing -> 150
        LiveTvSport.Golf, LiveTvSport.Tennis -> 240
        else -> 150
    }

/** "1.4 GB", "350 MB". */
internal fun formatRecordingSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) {
        val gb = (mb / 1024 * 10).toLong() / 10.0
        "$gb GB"
    } else {
        "${mb.toLong()} MB"
    }
}

/** "21:45" in the device's time zone. */
internal fun formatRecordingClock(epochMillis: Long): String {
    val time = kotlin.time.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
