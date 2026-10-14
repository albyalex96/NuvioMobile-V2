package com.nuvio.app.core.format

/**
 * User-selectable display format for calendar dates. Only affects how dates are shown;
 * stored/compared values stay ISO-8601. Patterns use Unicode (CLDR) date symbols.
 */
enum class DateFormatPreset(
    val key: String,
    /** Human-readable pattern shown in settings; null for [SYSTEM], which uses a localized label. */
    val patternLabel: String?,
    private val fullPattern: String?,
    private val shortPattern: String?,
) {
    SYSTEM("system", null, null, null),
    DD_MM_YYYY("dd_mm_yyyy", "DD/MM/YYYY", "dd/MM/yyyy", "dd/MM"),
    MM_DD_YYYY("mm_dd_yyyy", "MM/DD/YYYY", "MM/dd/yyyy", "MM/dd"),
    YYYY_MM_DD("yyyy_mm_dd", "YYYY-MM-DD", "yyyy-MM-dd", "MM-dd"),
    DD_MMM_YYYY("dd_mmm_yyyy", "DD MMM YYYY", "dd MMM yyyy", "dd MMM"),
    MMM_DD_YYYY("mmm_dd_yyyy", "MMM DD, YYYY", "MMM dd, yyyy", "MMM dd"),
    ;

    /** Pattern for this preset, or null when the locale-based system format should be used. */
    fun datePattern(includeYear: Boolean): String? = if (includeYear) fullPattern else shortPattern

    companion object {
        const val TIME_PATTERN = "HH:mm"

        fun fromKey(key: String?): DateFormatPreset =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
