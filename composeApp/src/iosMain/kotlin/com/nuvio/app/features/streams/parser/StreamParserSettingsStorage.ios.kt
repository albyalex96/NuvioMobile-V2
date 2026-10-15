package com.nuvio.app.features.streams.parser

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

internal actual object StreamParserSettingsStorage {
    private val syncKeys = listOf(STREAM_PARSER_ENABLED_KEY, STREAM_PARSER_ANIMATIONS_ENABLED_KEY)

    actual fun loadEnabled(): Boolean? = loadBoolean(STREAM_PARSER_ENABLED_KEY)

    actual fun saveEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_ENABLED_KEY, enabled)
    }

    actual fun loadAnimationsEnabled(): Boolean? = loadBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)

    actual fun saveAnimationsEnabled(enabled: Boolean) {
        saveBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, enabled)
    }

    private fun loadBoolean(key: String): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val scopedKey = ProfileScopedKey.of(key)
        return if (defaults.objectForKey(scopedKey) != null) {
            defaults.boolForKey(scopedKey)
        } else {
            null
        }
    }

    private fun saveBoolean(key: String, enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(key))
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(STREAM_PARSER_ENABLED_KEY, encodeSyncBoolean(it)) }
        loadAnimationsEnabled()?.let { put(STREAM_PARSER_ANIMATIONS_ENABLED_KEY, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncKeys.forEach { key ->
            NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(key))
        }

        payload.decodeSyncBoolean(STREAM_PARSER_ENABLED_KEY)?.let(::saveEnabled)
        payload.decodeSyncBoolean(STREAM_PARSER_ANIMATIONS_ENABLED_KEY)?.let(::saveAnimationsEnabled)
    }
}
