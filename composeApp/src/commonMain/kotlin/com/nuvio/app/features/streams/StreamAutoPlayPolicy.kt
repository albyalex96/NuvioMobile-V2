package com.nuvio.app.features.streams

import com.nuvio.app.features.player.PlayerSettingsUiState

object StreamAutoPlayPolicy {
    fun isEffectivelyEnabled(settings: PlayerSettingsUiState): Boolean {
        if (settings.streamReuseLastLinkEnabled) return true
        if (settings.streamAutoPlayReuseBingeGroup && settings.streamAutoPlayPreferBingeGroup) return true

        return when (settings.streamAutoPlayMode) {
            StreamAutoPlayMode.MANUAL -> false
            StreamAutoPlayMode.FIRST_STREAM -> true
            StreamAutoPlayMode.REGEX_MATCH -> isRegexSelectionConfigured(settings.streamAutoPlayRegex)
            StreamAutoPlayMode.PROVIDER_PRIORITY -> settings.streamProviderPriority.applyToFirstPlayback
        }
    }

    /**
     * Mode used when a title is opened. Provider priority limited to the next episode leaves the
     * first stream choice to the user.
     */
    fun firstPlaybackMode(settings: PlayerSettingsUiState): StreamAutoPlayMode =
        if (
            settings.streamAutoPlayMode == StreamAutoPlayMode.PROVIDER_PRIORITY &&
            !settings.streamProviderPriority.applyToFirstPlayback
        ) {
            StreamAutoPlayMode.MANUAL
        } else {
            settings.streamAutoPlayMode
        }

    fun isRegexSelectionConfigured(regexPattern: String): Boolean {
        val pattern = regexPattern.trim()
        if (pattern.isEmpty() || !pattern.any { it.isLetterOrDigit() }) return false
        return runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
    }
}
