package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.nuvio.app.features.settings.ThemeSettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** Minutes before the start a reminder can ring at (0 = at kickoff). */
internal val liveTvSportsReminderLeadChoices = listOf(0, 5, 15, 30, 60)

/** Minutes between two score checks while a followed game is on. */
internal val liveTvSportsLiveAlertIntervals = listOf(5, 10, 15, 30)

/**
 * Options of the game reminders (Settings → Sports). Synced with the profile; the reminders
 * themselves stay on the device that rings them.
 */
@Serializable
data class LiveTvSportsReminderOptions(
    val enabled: Boolean = false,
    val leadMinutes: Set<Int> = setOf(0),
    val liveAlertsEnabled: Boolean = false,
    val liveAlertIntervalMinutes: Int = 10,
)

/** One game the user wants to be reminded of. */
@Serializable
data class LiveTvSportsReminder(
    val key: String,
    val leagueId: String,
    val eventId: String,
    val title: String,
    val leagueLabel: String,
    val startMillis: Long,
    val hasTime: Boolean = true,
)

internal fun liveTvSportsEventKey(event: LiveTvSportsEvent): String = "${event.league.id}:${event.id}"

/** Schedules and shows the reminder notifications (Android only for now). */
internal expect object LiveTvSportsNotificationPlatform {
    val isSupported: Boolean

    /** Replaces every scheduled reminder and score check with these. */
    fun schedule(reminders: List<LiveTvSportsReminder>, options: LiveTvSportsReminderOptions)

    /** Shows a sample reminder now. */
    fun showTestNotification(title: String, body: String)
}

object LiveTvSportsRemindersRepository {
    private val log = Logger.withTag("LiveTvSportsReminders")
    private const val OPTIONS_KEY = "live_tv_sports_reminder_options"
    private const val REMINDERS_KEY = "live_tv_sports_reminders"
    private const val SYNC_KEY = "live_tv_sports_reminder_options"

    /** A reminder is dropped this long after the start (the score checks end well before). */
    private const val KEEP_AFTER_START_MILLIS = 12 * HOUR_MILLIS

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val remindersSerializer = ListSerializer(LiveTvSportsReminder.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _options = MutableStateFlow(LiveTvSportsReminderOptions())
    val options: StateFlow<LiveTvSportsReminderOptions> = _options.asStateFlow()

    private val _reminders = MutableStateFlow<Map<String, LiveTvSportsReminder>>(emptyMap())
    val reminders: StateFlow<Map<String, LiveTvSportsReminder>> = _reminders.asStateFlow()

    private var loaded = false

    val isSupported: Boolean get() = LiveTvSportsNotificationPlatform.isSupported

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _options.value = ThemeSettingsStorage.loadLocalString(OPTIONS_KEY)?.let { raw ->
            runCatching { json.decodeFromString(LiveTvSportsReminderOptions.serializer(), raw) }
                .onFailure { log.w(it) { "Stored reminder options unreadable, using the defaults" } }
                .getOrNull()
        } ?: LiveTvSportsReminderOptions()
        val now = Clock.System.now().toEpochMilliseconds()
        _reminders.value = ThemeSettingsStorage.loadLocalString(REMINDERS_KEY)?.let { raw ->
            runCatching { json.decodeFromString(remindersSerializer, raw) }.getOrNull()
        }.orEmpty()
            .filter { now - it.startMillis < KEEP_AFTER_START_MILLIS }
            .associateBy { it.key }
    }

    fun onProfileChanged() {
        loaded = false
        ensureLoaded()
        reschedule()
    }

    fun isSet(event: LiveTvSportsEvent): Boolean = liveTvSportsEventKey(event) in _reminders.value

    /** Sets or removes the reminder of [event]. */
    fun toggle(event: LiveTvSportsEvent) {
        ensureLoaded()
        val key = liveTvSportsEventKey(event)
        val current = _reminders.value
        _reminders.value = if (key in current) {
            log.i { "Reminder removed: ${event.name}" }
            current - key
        } else {
            log.i { "Reminder set: ${event.name} (${event.league.id}, start ${event.startMillis})" }
            current + (key to LiveTvSportsReminder(
                key = key,
                leagueId = event.league.id,
                eventId = event.id,
                title = event.name,
                leagueLabel = event.league.label,
                startMillis = event.startMillis,
                hasTime = event.hasTime,
            ))
        }
        saveReminders()
        reschedule()
    }

    /** Keeps the stored start time in line with the feed (games get moved). */
    fun updateFromEvents(events: List<LiveTvSportsEvent>) {
        ensureLoaded()
        val current = _reminders.value
        if (current.isEmpty()) return
        var changed = false
        val updated = current.toMutableMap()
        events.forEach { event ->
            val reminder = current[liveTvSportsEventKey(event)] ?: return@forEach
            if (reminder.startMillis != event.startMillis || reminder.title != event.name) {
                updated[reminder.key] = reminder.copy(startMillis = event.startMillis, title = event.name)
                changed = true
            }
        }
        if (!changed) return
        log.i { "Reminder start times updated from the feed" }
        _reminders.value = updated
        saveReminders()
        reschedule()
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            // Android 13+: ask for the notification permission the first time.
            scope.launch {
                val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }.getOrDefault(false)
                if (!granted) log.w { "Notifications not allowed: reminders will not ring" }
            }
        }
        updateOptions { it.copy(enabled = enabled) }
    }

    fun toggleLeadMinutes(minutes: Int) = updateOptions { options ->
        val leads = if (minutes in options.leadMinutes) options.leadMinutes - minutes else options.leadMinutes + minutes
        // At least one moment must stay chosen.
        options.copy(leadMinutes = leads.ifEmpty { setOf(minutes) })
    }

    fun setLiveAlertsEnabled(enabled: Boolean) = updateOptions { it.copy(liveAlertsEnabled = enabled) }

    fun setLiveAlertInterval(minutes: Int) = updateOptions { it.copy(liveAlertIntervalMinutes = minutes) }

    fun sendTestNotification(title: String, body: String) {
        scope.launch {
            val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }.getOrDefault(false)
            if (!granted) {
                log.w { "Test notification skipped: notifications not allowed" }
                return@launch
            }
            log.i { "Test notification sent" }
            LiveTvSportsNotificationPlatform.showTestNotification(title, body)
        }
    }

    private fun updateOptions(transform: (LiveTvSportsReminderOptions) -> LiveTvSportsReminderOptions) {
        ensureLoaded()
        val updated = transform(_options.value)
        if (updated == _options.value) return
        _options.value = updated
        ThemeSettingsStorage.saveLocalString(OPTIONS_KEY, json.encodeToString(LiveTvSportsReminderOptions.serializer(), updated))
        log.i { "Reminder options: $updated" }
        reschedule()
    }

    private fun saveReminders() {
        ThemeSettingsStorage.saveLocalString(REMINDERS_KEY, json.encodeToString(remindersSerializer, _reminders.value.values.toList()))
    }

    /** Hands every reminder to the platform again (cheap: a handful of scheduled jobs). */
    fun reschedule() {
        ensureLoaded()
        if (!isSupported) return
        val options = _options.value
        val reminders = if (options.enabled) _reminders.value.values.toList() else emptyList()
        log.d { "Scheduling ${reminders.size} reminders (enabled=${options.enabled})" }
        runCatching { LiveTvSportsNotificationPlatform.schedule(reminders, options) }
            .onFailure { log.e(it) { "Scheduling the reminders failed" } }
    }

    // ------------------------------------------------------------------------------- profile sync

    fun syncSignature(options: LiveTvSportsReminderOptions): String = "sports_reminders_$options"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        ensureLoaded()
        put(SYNC_KEY, encodeSyncString(json.encodeToString(LiveTvSportsReminderOptions.serializer(), _options.value)))
    }

    /** Payloads from older versions (no value) keep the local options. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val raw = payload.decodeSyncString(SYNC_KEY) ?: return
        val options = runCatching { json.decodeFromString(LiveTvSportsReminderOptions.serializer(), raw) }.getOrNull() ?: return
        ensureLoaded()
        if (options == _options.value) return
        _options.value = options
        ThemeSettingsStorage.saveLocalString(OPTIONS_KEY, raw)
        log.i { "Reminder options applied from the profile sync" }
        reschedule()
    }
}
