package com.nuvio.app.features.player

data class PersistedPlayerTrackPreference(
    val subtitleType: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleName: String? = null,
    val subtitleTrackId: String? = null,
    val addonSubtitleId: String? = null,
    val addonSubtitleUrl: String? = null,
    val addonSubtitleAddonName: String? = null,
    val audioLanguage: String? = null,
    val audioName: String? = null,
    val audioTrackId: String? = null,
    val subtitleIsForced: Boolean? = null,
)

object PersistedSubtitleSelectionType {
    const val INTERNAL = "INTERNAL"
    const val ADDON = "ADDON"
    const val DISABLED = "DISABLED"
}

internal expect object PlayerTrackPreferenceStorage {
    fun load(contentId: String): PersistedPlayerTrackPreference?
    fun save(contentId: String, preference: PersistedPlayerTrackPreference)
    fun loadSubtitleDelayMs(videoId: String): Int?
    fun saveSubtitleDelayMs(videoId: String, delayMs: Int)
    /** Per audio output delay ("remember audio delay per device"); local to this device. */
    fun loadAudioDelayMs(routeKey: String): Int?
    /** Zero removes the entry. */
    fun saveAudioDelayMs(routeKey: String, delayMs: Int)
}
