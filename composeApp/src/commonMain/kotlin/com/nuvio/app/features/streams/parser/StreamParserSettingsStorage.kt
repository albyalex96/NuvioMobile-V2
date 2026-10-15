package com.nuvio.app.features.streams.parser

import kotlinx.serialization.json.JsonObject

internal const val STREAM_PARSER_ENABLED_KEY = "stream_parser_enabled"
internal const val STREAM_PARSER_ANIMATIONS_ENABLED_KEY = "stream_parser_animations_enabled"
internal const val STREAM_PARSER_SORT_BY_QUALITY_KEY = "stream_parser_sort_by_quality"

internal expect object StreamParserSettingsStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
    fun loadAnimationsEnabled(): Boolean?
    fun saveAnimationsEnabled(enabled: Boolean)
    fun loadSortByQuality(): Boolean?
    fun saveSortByQuality(enabled: Boolean)

    /** Keys are merged into the `stream_badge_settings` payload of the profile sync blob. */
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
