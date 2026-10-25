package com.nuvio.app.features.player.skip

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.features.player.PlayerSettingsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Profile sync of the timestamp integrations (AnimeSkip, IntroDB, SkipDB and AniSkip toggles and credentials,
 * provider priority and submission targets), carried inside the
 * `stream_badge_settings` payload of the profile settings blob like the other local extras: the
 * custom credential RPCs are not reliable on the server, so the client ID and the API key travel
 * with the blob instead.
 */
internal object TimestampIntegrationsProfileSync {
    private const val ANIMESKIP_ENABLED_KEY = "timestamp_animeskip_enabled"
    private const val ANIMESKIP_CLIENT_ID_KEY = "timestamp_animeskip_client_id"
    private const val INTRODB_SUBMIT_ENABLED_KEY = "timestamp_introdb_submit_enabled"
    private const val INTRODB_API_KEY_KEY = "timestamp_introdb_api_key"
    private const val SKIPDB_SETTINGS_KEY = "timestamp_skipdb_settings"
    private const val ANISKIP_SETTINGS_KEY = "timestamp_aniskip_settings"
    private const val PRIORITY_KEY = "timestamp_provider_priority"
    private const val SUBMIT_TARGETS_KEY = "timestamp_submit_targets"
    private const val MERGE_PROVIDERS_KEY = "timestamp_merge_providers"

    private val log = Logger.withTag("TimestampIntegrations")

    fun exportToSyncPayload(): JsonObject {
        PlayerSettingsRepository.ensureLoaded()
        val settings = PlayerSettingsRepository.uiState.value
        val providers = TimestampProviderSettingsRepository.current()
        return buildJsonObject {
            put(ANIMESKIP_ENABLED_KEY, encodeSyncBoolean(settings.animeSkipEnabled))
            put(ANIMESKIP_CLIENT_ID_KEY, encodeSyncString(settings.animeSkipClientId.trim()))
            put(INTRODB_SUBMIT_ENABLED_KEY, encodeSyncBoolean(settings.introSubmitEnabled))
            put(INTRODB_API_KEY_KEY, encodeSyncString(settings.introDbApiKey.trim()))
            put(SKIPDB_SETTINGS_KEY, encodeSyncString(TimestampProviderSettingsRepository.encodeSkipDb(providers.skipDb)))
            put(ANISKIP_SETTINGS_KEY, encodeSyncString(TimestampProviderSettingsRepository.encodeAniSkip(providers.aniSkip)))
            put(MERGE_PROVIDERS_KEY, encodeSyncBoolean(providers.mergeProviders))
            put(PRIORITY_KEY, encodeSyncString(providers.priority.joinToString(LIST_SEPARATOR)))
            // Until the user chooses, the default (highest-priority ready service) is not synced.
            providers.submitTargets?.let { put(SUBMIT_TARGETS_KEY, encodeSyncString(it.joinToString(LIST_SEPARATOR))) }
        }
    }

    /** Applies each remote value only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        var applied = 0
        payload.decodeSyncBoolean(ANIMESKIP_ENABLED_KEY)?.let {
            PlayerSettingsRepository.setAnimeSkipEnabled(it)
            applied++
        }
        payload.decodeSyncString(ANIMESKIP_CLIENT_ID_KEY)?.let {
            PlayerSettingsRepository.setAnimeSkipClientId(it.trim())
            applied++
        }
        payload.decodeSyncBoolean(INTRODB_SUBMIT_ENABLED_KEY)?.let {
            PlayerSettingsRepository.setIntroSubmitEnabled(it)
            applied++
        }
        payload.decodeSyncString(INTRODB_API_KEY_KEY)?.let {
            PlayerSettingsRepository.setIntroDbApiKey(it.trim())
            applied++
        }
        payload.decodeSyncString(SKIPDB_SETTINGS_KEY)
            ?.let(TimestampProviderSettingsRepository::decodeSkipDb)
            ?.let { remote ->
                TimestampProviderSettingsRepository.updateSkipDb { remote }
                applied++
            }
        payload.decodeSyncString(ANISKIP_SETTINGS_KEY)
            ?.let(TimestampProviderSettingsRepository::decodeAniSkip)
            ?.let { remote ->
                TimestampProviderSettingsRepository.updateAniSkip { remote }
                applied++
            }
        payload.decodeSyncBoolean(MERGE_PROVIDERS_KEY)?.let {
            TimestampProviderSettingsRepository.setMergeProviders(it)
            applied++
        }
        payload.decodeSyncString(PRIORITY_KEY)?.let {
            TimestampProviderSettingsRepository.setPriority(decodeIdList(it))
            applied++
        }
        payload.decodeSyncString(SUBMIT_TARGETS_KEY)?.let {
            TimestampProviderSettingsRepository.setSubmitTargets(decodeIdList(it))
            applied++
        }
        if (applied > 0) {
            log.d { "Applied $applied timestamp integration settings from the profile blob" }
        }
    }

    /** Signature of the synced values, so a local change schedules a blob upload. */
    fun syncSignature(settings: TimestampProviderSettings): String = "timestamp_providers_${settings.hashCode()}"

    private fun decodeIdList(value: String): List<String> =
        value.split(LIST_SEPARATOR).map(String::trim).filter(String::isNotEmpty)

    private const val LIST_SEPARATOR = ","
}
