package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.opensubtitles.OPENSUBTITLES_LOG_TAG
import com.nuvio.app.features.opensubtitles.OpenSubtitlesLinkResolver
import com.nuvio.app.features.opensubtitles.isOpenSubtitlesPlaceholderUrl
import com.nuvio.app.features.subdl.SubDlLinkResolver
import com.nuvio.app.features.subdl.isSubDlPlaceholderUrl
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.opensubtitles_download_failed
import nuvio.composeapp.generated.resources.subdl_download_failed
import org.jetbrains.compose.resources.getString

private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)

/**
 * True for the placeholder URLs of the subtitle integrations (OpenSubtitles, SubDL): their files
 * cost a download of a daily limit, so they are fetched only when attached to the player.
 */
internal fun isIntegrationSubtitlePlaceholderUrl(url: String?): Boolean =
    isOpenSubtitlesPlaceholderUrl(url) || isSubDlPlaceholderUrl(url)

private suspend fun resolveIntegrationSubtitle(url: String): String? = when {
    isOpenSubtitlesPlaceholderUrl(url) -> OpenSubtitlesLinkResolver.resolve(url)
    isSubDlPlaceholderUrl(url) -> SubDlLinkResolver.resolve(url)
    else -> url
}

/**
 * Hands an external subtitle URL to [attach]. Integration placeholders are first turned into a
 * local file; a file that arrives after the user picked another subtitle is dropped.
 */
internal fun PlayerScreenRuntime.attachExternalSubtitleUrl(url: String, attach: (String) -> Unit) {
    if (!isIntegrationSubtitlePlaceholderUrl(url)) {
        attach(url)
        return
    }
    val selectionAtRequest = selectedAddonSubtitleId
    scope.launch {
        val link = resolveIntegrationSubtitle(url)
        if (selectedAddonSubtitleId != selectionAtRequest) {
            log.d { "Subtitle selection changed while resolving $url, link dropped" }
            return@launch
        }
        if (link == null) {
            val message = if (isSubDlPlaceholderUrl(url)) {
                Res.string.subdl_download_failed
            } else {
                Res.string.opensubtitles_download_failed
            }
            NuvioToastController.show(getString(message))
            return@launch
        }
        attach(link)
    }
}

/** Text of an integration subtitle (downloaded if needed), null for any other subtitle URL. */
internal suspend fun openSubtitlesText(url: String): String? = when {
    isOpenSubtitlesPlaceholderUrl(url) ->
        OpenSubtitlesLinkResolver.resolveText(url) ?: error("OpenSubtitles file unavailable")
    isSubDlPlaceholderUrl(url) -> SubDlLinkResolver.resolveText(url) ?: error("SubDL file unavailable")
    else -> null
}

/** Maps a resolved integration file reported by the player back to its listed URL. */
internal fun listedExternalSubtitleUrl(url: String): String =
    OpenSubtitlesLinkResolver.placeholderFor(url) ?: SubDlLinkResolver.placeholderFor(url) ?: url
