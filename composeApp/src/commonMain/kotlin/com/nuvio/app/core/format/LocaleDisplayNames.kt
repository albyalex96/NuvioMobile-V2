package com.nuvio.app.core.format

import androidx.compose.ui.text.intl.Locale

private val countryCodeRegex = Regex("^[A-Za-z]{2}$")
private val languageCodeRegex = Regex("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$")

// TMDB uses a few non-standard original_language codes.
private val tmdbLanguageAliases = mapOf(
    "cn" to "yue",
    "sh" to "sr",
)

/**
 * Turns a comma separated list of ISO 3166-1 alpha-2 codes ("JP, US") into localized country
 * names ("Giappone, Stati Uniti"). Values that are not codes (e.g. names sent by addons) and
 * unknown codes are kept as they are.
 */
fun formatCountriesForDisplay(raw: String, localeTag: String = Locale.current.toLanguageTag()): String =
    raw.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(", ") { value -> countryDisplayName(value, localeTag) ?: value }

/** Localized name of an ISO 3166-1 alpha-2 code, or null when the code is not recognized. */
fun countryDisplayName(code: String, localeTag: String = Locale.current.toLanguageTag()): String? {
    val trimmed = code.trim()
    // ZZ is the CLDR "unknown region" code: showing "Unknown region" would hide the original value.
    if (!countryCodeRegex.matches(trimmed) || trimmed.equals("ZZ", ignoreCase = true)) return null
    return platformCountryDisplayName(trimmed.uppercase(), localeTag)
        ?.takeIf { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
}

/**
 * Localized name of a language code ("ja" -> "Giapponese"). Falls back to the upper-cased code
 * when the language is not recognized; values that are not codes are kept as they are.
 */
fun formatLanguageForDisplay(raw: String, localeTag: String = Locale.current.toLanguageTag()): String {
    val trimmed = raw.trim()
    if (!languageCodeRegex.matches(trimmed)) return trimmed
    return languageDisplayName(trimmed, localeTag) ?: trimmed.uppercase()
}

fun languageDisplayName(code: String, localeTag: String = Locale.current.toLanguageTag()): String? {
    val normalized = code.trim().replace('_', '-').lowercase()
    if (normalized.isEmpty() || normalized == "xx" || normalized == "und") return null
    val primary = normalized.substringBefore('-')
    val resolved = tmdbLanguageAliases[primary] ?: primary
    return platformLanguageDisplayName(resolved, localeTag)
        ?.takeIf { it.isNotBlank() && !it.equals(resolved, ignoreCase = true) && !it.equals(primary, ignoreCase = true) }
        ?.capitalizeFirst(localeTag)
}

// Some locales (e.g. Italian) return lower-case language names. Turkish needs the dotted capital I.
private fun String.capitalizeFirst(localeTag: String): String =
    if (startsWith('i') && localeTag.startsWith("tr")) "İ" + substring(1)
    else replaceFirstChar { it.titlecase() }

internal expect fun platformCountryDisplayName(code: String, localeTag: String): String?

internal expect fun platformLanguageDisplayName(code: String, localeTag: String): String?
