package com.nuvio.app.core.format

import androidx.compose.ui.text.intl.Locale
import com.nuvio.app.core.time.parseEpisodeReleaseLocalDate
import com.nuvio.app.features.settings.ThemeSettingsRepository

fun formatReleaseDateForDisplay(raw: String): String = formatReleaseDate(raw, includeYear = true)

fun formatReleaseDateWithoutYear(raw: String): String = formatReleaseDate(raw, includeYear = false)

internal fun formatReleaseDate(
    raw: String,
    includeYear: Boolean,
    localeTag: String = Locale.current.toLanguageTag(),
    preset: DateFormatPreset = ThemeSettingsRepository.dateFormatState.value,
): String {
    val date = parseEpisodeReleaseLocalDate(raw) ?: return raw
    val pattern = preset.datePattern(includeYear) ?: return formatCalendarDate(date, localeTag, includeYear)
    return formatCalendarDateWithPattern(date, localeTag, pattern)
}

/**
 * Formats [raw] with the user's explicit date preset. Returns null when the system preset is
 * active or [raw] is not a date, so callers can keep their own default formatting.
 */
fun formatDateWithSelectedPreset(raw: String, includeYear: Boolean = true): String? {
    val pattern = ThemeSettingsRepository.dateFormatState.value.datePattern(includeYear) ?: return null
    val date = parseEpisodeReleaseLocalDate(raw) ?: return null
    return formatCalendarDateWithPattern(date, Locale.current.toLanguageTag(), pattern)
}

internal expect fun formatCalendarDate(isoDate: String, localeTag: String, includeYear: Boolean): String

internal expect fun formatCalendarDateWithPattern(isoDate: String, localeTag: String, pattern: String): String

/**
 * Parses a release/air string (ISO date, year-only, or timestamp prefix) for compact UI (e.g. year chips).
 */
fun extractReleaseYearForDisplay(raw: String): Int? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    if (t.length == 4 && t.all { it.isDigit() }) {
        return t.toIntOrNull()?.takeIf { it in 1000..9999 }
    }
    val datePart = parseEpisodeReleaseLocalDate(t) ?: return null
    val yearStr = datePart.split('-').firstOrNull() ?: return null
    return yearStr.toIntOrNull()?.takeIf { it in 1000..9999 }
}
