package com.nuvio.app.features.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Every subtitle setting of one device class (phone, tablet, TV, desktop): style, appearance,
 * languages and libass options. Each class keeps its own copy, synced with the profile, and a
 * device only shows and edits the copy of its own class.
 */
@Serializable
data class SubtitleDeviceSettings(
    val textColor: String,
    val backgroundColor: String,
    val outlineColor: String,
    val outlineEnabled: Boolean,
    val outlineWidth: Int,
    val bold: Boolean,
    val fontSizeSp: Int,
    val bottomOffset: Int,
    val stripSdh: Boolean,
    val useForcedSubtitles: Boolean,
    val showOnlyPreferredLanguages: Boolean,
    val appearance: String? = null,
    val preferredLanguage: String = SubtitleLanguageOption.NONE,
    val secondaryLanguage: String? = null,
    val useLibass: Boolean = true,
    val libassRenderType: String = "CUES",
    val libassRenderScale: String = "FULL",
) {
    fun toStyle(): SubtitleStyleState = SubtitleStyleState.DEFAULT.copy(
        textColor = subtitleColorFromStorage(textColor) ?: SubtitleStyleState.DEFAULT.textColor,
        backgroundColor = subtitleColorFromStorage(backgroundColor) ?: SubtitleStyleState.DEFAULT.backgroundColor,
        outlineColor = subtitleColorFromStorage(outlineColor) ?: SubtitleStyleState.DEFAULT.outlineColor,
        outlineEnabled = outlineEnabled,
        outlineWidth = outlineWidth,
        bold = bold,
        fontSizeSp = fontSizeSp.coerceIn(subtitleFontSizeRangeSp),
        bottomOffset = bottomOffset.coerceIn(0, 200),
        stripSdh = stripSdh,
        useForcedSubtitles = useForcedSubtitles,
        showOnlyPreferredLanguages = showOnlyPreferredLanguages,
        appearance = decodeSubtitleAppearance(appearance),
    )

    fun encode(): String = subtitleDeviceJson.encodeToString(serializer(), this)

    companion object {
        private val subtitleDeviceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): SubtitleDeviceSettings? {
            if (raw.isNullOrBlank()) return null
            return runCatching { subtitleDeviceJson.decodeFromString(serializer(), raw) }.getOrNull()
        }

        fun from(
            style: SubtitleStyleState,
            preferredLanguage: String,
            secondaryLanguage: String?,
            useLibass: Boolean,
            libassRenderType: String,
            libassRenderScale: String,
        ): SubtitleDeviceSettings = SubtitleDeviceSettings(
            textColor = style.textColor.toStorageHexString(),
            backgroundColor = style.backgroundColor.toStorageHexString(),
            outlineColor = style.outlineColor.toStorageHexString(),
            outlineEnabled = style.outlineEnabled,
            outlineWidth = style.outlineWidth,
            bold = style.bold,
            fontSizeSp = style.fontSizeSp,
            bottomOffset = style.bottomOffset,
            stripSdh = style.stripSdh,
            useForcedSubtitles = style.useForcedSubtitles,
            showOnlyPreferredLanguages = style.showOnlyPreferredLanguages,
            appearance = style.appearance.encode(),
            preferredLanguage = preferredLanguage,
            secondaryLanguage = secondaryLanguage,
            useLibass = useLibass,
            libassRenderType = libassRenderType,
            libassRenderScale = libassRenderScale,
        )
    }
}
