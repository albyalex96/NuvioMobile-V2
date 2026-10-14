package com.nuvio.app.core.format

import com.nuvio.app.features.settings.ThemeSettingsRepository

/**
 * User-selectable display format for season/episode codes (e.g. "S01E02", "1x2").
 * Only affects what the user sees; [DEFAULT] keeps the localized string resources.
 */
enum class EpisodeCodeFormat(
    val key: String,
    /** Example shown in settings; null for [DEFAULT], which uses a localized label. */
    val sample: String?,
) {
    DEFAULT("default", null),
    S_PADDED("s01e02", "S01E02"),
    S_PLAIN("s1e2", "S1E2"),
    X_PADDED("01x02", "01x02"),
    X_PLAIN("1x2", "1x2"),
    ;

    /** Formatted code, or null for [DEFAULT] / missing episode number. */
    fun format(seasonNumber: Int?, episodeNumber: Int?): String? {
        if (this == DEFAULT || episodeNumber == null) return null
        val padded = this == S_PADDED || this == X_PADDED
        val episode = if (padded) episodeNumber.twoDigits() else episodeNumber.toString()
        if (seasonNumber == null) return "E$episode"
        val season = if (padded) seasonNumber.twoDigits() else seasonNumber.toString()
        return when (this) {
            S_PADDED, S_PLAIN -> "S${season}E$episode"
            else -> "${season}x$episode"
        }
    }

    companion object {
        fun fromKey(key: String?): EpisodeCodeFormat =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/**
 * Season/episode code in the user's selected format, or null when the default format is active
 * (callers then fall back to their localized string resource). Reads snapshot state, so composables
 * calling it recompose when the format changes.
 */
fun formatEpisodeCodeWithSelectedFormat(seasonNumber: Int?, episodeNumber: Int?): String? =
    ThemeSettingsRepository.episodeCodeFormatState.value.format(seasonNumber, episodeNumber)
