package com.nuvio.app.core.format

import androidx.compose.ui.text.intl.Locale
import com.nuvio.app.features.settings.ThemeSettingsRepository

fun formatLocalDateTime(
    epochMs: Long,
    localeTag: String = Locale.current.toLanguageTag(),
    preset: DateFormatPreset = ThemeSettingsRepository.dateFormatState.value,
): String = formatLocalDateTimeWithPatterns(
    epochMs = epochMs,
    localeTag = localeTag,
    datePattern = preset.datePattern(includeYear = true),
    timePattern = DateFormatPreset.TIME_PATTERN,
)

/** Formats [epochMs] in the device time zone; a null [datePattern] uses the locale's short date. */
internal expect fun formatLocalDateTimeWithPatterns(
    epochMs: Long,
    localeTag: String,
    datePattern: String?,
    timePattern: String,
): String

fun formatLocalTime(
    epochMs: Long,
    localeTag: String = Locale.current.toLanguageTag(),
): String = formatLocalTimeWithPattern(
    epochMs = epochMs,
    localeTag = localeTag,
    timePattern = DateFormatPreset.TIME_PATTERN,
)

/** Formats [epochMs] as a time of day in the device time zone. */
internal expect fun formatLocalTimeWithPattern(
    epochMs: Long,
    localeTag: String,
    timePattern: String,
): String
