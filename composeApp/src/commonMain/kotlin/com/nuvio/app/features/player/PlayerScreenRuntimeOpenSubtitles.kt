package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.opensubtitles.OPENSUBTITLES_LOG_TAG
import com.nuvio.app.features.opensubtitles.OpenSubtitlesLinkResolver
import com.nuvio.app.features.opensubtitles.isOpenSubtitlesPlaceholderUrl
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.opensubtitles_download_failed
import org.jetbrains.compose.resources.getString

private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)

/**
 * Hands an external subtitle URL to [attach]. OpenSubtitles placeholders are first turned into
 * their download link; a link that arrives after the user picked another subtitle is dropped.
 */
internal fun PlayerScreenRuntime.attachExternalSubtitleUrl(url: String, attach: (String) -> Unit) {
    if (!isOpenSubtitlesPlaceholderUrl(url)) {
        attach(url)
        return
    }
    val selectionAtRequest = selectedAddonSubtitleId
    scope.launch {
        val link = OpenSubtitlesLinkResolver.resolve(url)
        if (selectedAddonSubtitleId != selectionAtRequest) {
            log.d { "Subtitle selection changed while resolving $url, link dropped" }
            return@launch
        }
        if (link == null) {
            NuvioToastController.show(getString(Res.string.opensubtitles_download_failed))
            return@launch
        }
        attach(link)
    }
}

/** Text of an OpenSubtitles file (downloaded if needed), null for any other subtitle URL. */
internal suspend fun openSubtitlesText(url: String): String? {
    if (!isOpenSubtitlesPlaceholderUrl(url)) return null
    return OpenSubtitlesLinkResolver.resolveText(url) ?: error("OpenSubtitles file unavailable")
}

/** Maps a resolved OpenSubtitles file reported by the player back to its listed URL. */
internal fun listedExternalSubtitleUrl(url: String): String =
    OpenSubtitlesLinkResolver.placeholderFor(url) ?: url
