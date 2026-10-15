package com.nuvio.app.features.streams.parser

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object StreamParserSettingsStorage {
    // Shares the stream badge preferences file so the account data cleaner already wipes it.
    private const val preferencesName = "nuvio_stream_badge_settings"
    private val syncKeys = listOf(STREAM_PARSER_ENABLED_KEY, STREAM_PARSER_ANIMATIONS_ENABLED_KEY)

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadEnabled(): Boolean? = loadBoolean(STREAM_PARSER_ENABLED_KEY)

    actual fun saveEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_ENABLED_KEY, enabled)
    }

    actual fun loadAnimationsEnabled(): Boolean? = loadBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)

    actual fun saveAnimationsEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, enabled)
    }

    private fun loadBoolean(key: String): Boolean? =
        preferences?.let { sharedPreferences ->
            val scopedKey = ProfileScopedKey.of(key)
            if (sharedPreferences.contains(scopedKey)) {
                sharedPreferences.getBoolean(scopedKey, false)
            } else {
                null
            }
        }

    private fun saveBoolean(key: String, enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(key), enabled)
            ?.apply()
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(STREAM_PARSER_ENABLED_KEY, encodeSyncBoolean(it)) }
        loadAnimationsEnabled()?.let { put(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.apply {
            syncKeys.forEach { remove(ProfileScopedKey.of(it)) }
        }?.apply()

        payload.decodeSyncBoolean(STREAM_PARSER_ENABLED_KEY)?.let(::saveEnabled)
        payload.decodeSyncBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)?.let(::saveAnimationsEnabled)
    }
}
