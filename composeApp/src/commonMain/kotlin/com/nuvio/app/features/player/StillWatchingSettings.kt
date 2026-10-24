package com.nuvio.app.features.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Still watching" options of one device class. Phones, tablets and TVs keep separate copies,
 * all synced with the profile, so each class can be edited from any device.
 */
@Serializable
data class StillWatchingSettings(
    val promptEnabled: Boolean = false,
    val episodeCount: Int = DEFAULT_STILL_WATCHING_EPISODE_COUNT,
    val nightOnly: Boolean = false,
    val liveTvEnabled: Boolean = false,
    val liveTvMinutes: Int = DEFAULT_STILL_WATCHING_LIVE_TV_MINUTES,
) {
    fun normalized(): StillWatchingSettings = copy(
        episodeCount = episodeCount.coerceIn(STILL_WATCHING_EPISODE_COUNT_RANGE),
        liveTvMinutes = normalizeStillWatchingLiveTvMinutes(liveTvMinutes),
    )

    fun encode(): String = stillWatchingJson.encodeToString(serializer(), this)

    companion object {
        private val stillWatchingJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): StillWatchingSettings? {
            if (raw.isNullOrBlank()) return null
            return runCatching { stillWatchingJson.decodeFromString(serializer(), raw).normalized() }
                .getOrNull()
        }
    }
}

internal fun defaultStillWatchingSettings(): Map<SubtitleSizeFormFactor, StillWatchingSettings> =
    SubtitleSizeFormFactor.entries.associateWith { StillWatchingSettings() }
