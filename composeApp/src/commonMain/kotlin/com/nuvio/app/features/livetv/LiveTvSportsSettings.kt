package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.features.settings.ThemeSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The leagues the Sports section shows. [enabledIds] null means "not chosen yet": the leagues
 * marked [LiveTvSportsLeague.defaultEnabled] are used.
 */
data class LiveTvSportsSettings(
    val enabledIds: Set<String>? = null,
) {
    val isCustom: Boolean get() = enabledIds != null

    fun isEnabled(league: LiveTvSportsLeague): Boolean = enabledIds?.contains(league.id) ?: league.defaultEnabled

    fun enabledLeagues(): List<LiveTvSportsLeague> = liveTvSportsLeagues.filter(::isEnabled)

    fun signature(): String = enabledIds?.sorted()?.joinToString(",") ?: "default"
}

/**
 * Stores the chosen leagues per profile (device storage, profile scoped) and carries them in the
 * profile sync (inside the `stream_badge_settings` payload, like the rest of Live TV).
 */
object LiveTvSportsSettingsRepository {
    private val log = Logger.withTag("LiveTvSportsSettings")
    private const val STORAGE_KEY = "live_tv_sports_leagues"
    private const val SYNC_KEY = "live_tv_sports_leagues"
    private const val SYNC_DEFAULTS = "default"
    private val json = Json { ignoreUnknownKeys = true }
    private val idsSerializer = ListSerializer(String.serializer())

    private val _settings = MutableStateFlow(LiveTvSportsSettings())
    val settings: StateFlow<LiveTvSportsSettings> = _settings.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _settings.value = LiveTvSportsSettings(enabledIds = decode(ThemeSettingsStorage.loadLocalString(STORAGE_KEY)))
    }

    /** Reloads the values of the profile now active. */
    fun onProfileChanged() {
        loaded = false
        ensureLoaded()
    }

    fun setLeagueEnabled(league: LiveTvSportsLeague, enabled: Boolean) {
        ensureLoaded()
        val current = _settings.value
        val ids = current.enabledLeagues().map { it.id }.toMutableSet()
        if (enabled) ids += league.id else ids -= league.id
        log.i { "League ${league.id} ${if (enabled) "enabled" else "disabled"} (${ids.size} active)" }
        save(ids)
    }

    /** Turns every league of [sport] on or off. */
    fun setSportEnabled(sport: LiveTvSport, enabled: Boolean) {
        ensureLoaded()
        val ids = _settings.value.enabledLeagues().map { it.id }.toMutableSet()
        val sportIds = liveTvSportsLeagues.filter { it.sport == sport }.map { it.id }
        if (enabled) ids += sportIds else ids -= sportIds.toSet()
        log.i { "Sport ${sport.name} ${if (enabled) "enabled" else "disabled"} (${ids.size} active)" }
        save(ids)
    }

    fun resetToDefaults() {
        ensureLoaded()
        log.i { "Leagues reset to the defaults" }
        _settings.value = LiveTvSportsSettings(enabledIds = null)
        ThemeSettingsStorage.saveLocalString(STORAGE_KEY, null)
    }

    private fun save(ids: Set<String>) {
        val known = ids.filter { it in liveTvSportsLeaguesById }.toSet()
        _settings.value = LiveTvSportsSettings(enabledIds = known)
        ThemeSettingsStorage.saveLocalString(STORAGE_KEY, encode(known))
    }

    private fun encode(ids: Set<String>): String = json.encodeToString(idsSerializer, ids.sorted())

    private fun decode(raw: String?): Set<String>? = raw?.let {
        runCatching { json.decodeFromString(idsSerializer, it).toSet() }
            .onFailure { error -> log.w(error) { "Stored leagues unreadable, using the defaults" } }
            .getOrNull()
    }

    // ------------------------------------------------------------------------------- profile sync

    fun syncSignature(settings: LiveTvSportsSettings): String = "sports_leagues_${settings.signature()}"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        ensureLoaded()
        put(SYNC_KEY, encodeSyncString(_settings.value.enabledIds?.let(::encode) ?: SYNC_DEFAULTS))
    }

    /** Applies the synced choice; payloads from older versions (no value) keep the local one. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val raw = payload.decodeSyncString(SYNC_KEY) ?: return
        val ids = if (raw == SYNC_DEFAULTS) null else decode(raw) ?: return
        loaded = true
        _settings.value = LiveTvSportsSettings(enabledIds = ids)
        ThemeSettingsStorage.saveLocalString(STORAGE_KEY, ids?.let(::encode))
        log.i { "Leagues applied from the profile sync (${ids?.size?.toString() ?: "defaults"})" }
    }
}
