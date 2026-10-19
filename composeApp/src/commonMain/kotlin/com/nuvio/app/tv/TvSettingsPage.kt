package com.nuvio.app.tv

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.nuvio.app.features.home.components.continueWatchingCardArtworkUrl
import com.nuvio.app.features.home.components.continueWatchingPosterArtworkUrl
import com.nuvio.app.features.home.components.shouldBlurContinueWatchingArtwork
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle

/**
 * A settings page drawn by the TV app (Settings -> Appearance -> TV layout). The shared settings
 * screen owns navigation, header and scrolling; [content] adds the page rows to its list.
 */
@Immutable
class TvSettingsPage(
    val content: LazyListScope.(navigator: TvSettingsNavigator) -> Unit,
    /** Search index entries of the page (resolved strings), see `settingsSearchEntries`. */
    val searchEntries: @Composable () -> List<TvSettingsSearchItem>,
)

@Immutable
class TvSettingsSearchItem(
    val key: String,
    val title: String,
    val description: String,
    val section: String,
)

/** Shared settings pages a TV settings page can link to. */
@Immutable
class TvSettingsNavigator(
    val openHomescreen: () -> Unit,
    val openContinueWatching: () -> Unit,
    val openMetaScreen: () -> Unit,
    val openStreams: () -> Unit,
)

/**
 * Artwork of a TV "continue watching" card, picked like the phone cards: the landscape card
 * prefers episode thumbnails/backdrops, the wide and poster cards the poster.
 */
fun ContinueWatchingItem.tvContinueWatchingArtwork(
    style: ContinueWatchingSectionStyle,
    useEpisodeThumbnails: Boolean,
): String? = when (style) {
    ContinueWatchingSectionStyle.Card -> continueWatchingCardArtworkUrl(
        useEpisodeThumbnails = useEpisodeThumbnails,
        preferBackdropForNextUp = false,
    )
    // NuvioTV never shows episode thumbnails on the poster card.
    ContinueWatchingSectionStyle.Poster -> continueWatchingPosterArtworkUrl(useEpisodeThumbnails = false)
    ContinueWatchingSectionStyle.Wide -> continueWatchingPosterArtworkUrl(useEpisodeThumbnails)
}

/** "Blur unwatched in continue watching": hides the thumbnail of episodes not seen yet. */
fun ContinueWatchingItem.tvShouldBlurContinueWatchingArtwork(
    blurNextUp: Boolean,
    useEpisodeThumbnails: Boolean,
    artworkUrl: String?,
): Boolean = shouldBlurContinueWatchingArtwork(
    blurUnwatchedEpisodes = blurNextUp,
    useEpisodeThumbnails = useEpisodeThumbnails,
    artworkUrl = artworkUrl,
)
