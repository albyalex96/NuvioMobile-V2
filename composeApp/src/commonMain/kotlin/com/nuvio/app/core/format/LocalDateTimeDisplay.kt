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
