package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleCoordinator
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import com.nuvio.app.features.watching.domain.isShortPlaceholderDuration
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository

internal fun PlayerLaunch.externalPlaybackSession(): WatchProgressPlaybackSession = WatchProgressPlaybackSession(
    profileId = profileId,
    contentType = contentType ?: parentMetaType,
    parentMetaId = parentMetaId,
    parentMetaType = parentMetaType,
    videoId = videoId ?: parentMetaId,
    title = title,
    logo = logo,
    poster = poster,
    background = background,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeTitle = episodeTitle,
    episodeThumbnail = episodeThumbnail,
    providerName = providerName,
    providerAddonId = providerAddonId,
    lastStreamTitle = streamTitle,
    lastStreamSubtitle = streamSubtitle,
    pauseDescription = pauseDescription,
    lastSourceUrl = sourceUrl,
)

internal suspend fun recordExternalPlaybackProgress(
    result: ExternalPlaybackResult,
    fallbackSession: WatchProgressPlaybackSession?,
    privateLaunch: PlayerLaunch? = null,
) {
    if (PlayerSettingsRepository.uiState.value.privateViewingEnabled) {
        Logger.i("PrivateViewing") { "External player result not recorded: private viewing is on" }
        return
    }
    val session = result.playbackSession ?: fallbackSession ?: return
    if (
        privateLaunch != null &&
        PrivatePlaybackRequests.contentKey(privateLaunch.parentMetaId, privateLaunch.videoId) ==
        PrivatePlaybackRequests.contentKey(session.parentMetaId, session.videoId)
    ) {
        Logger.i("PrivateViewing") { "External player result not recorded: watched privately" }
        return
    }
    // A completed playback may carry no position at all (MX Player), so only drop position-less
    // results that were not reported as played to the end.
    if (result.positionMs <= 0L && result.endedByUser) return
    val durationMs = result.durationMs
    if (durationMs != null && isShortPlaceholderDuration(durationMs)) return
    WatchProgressRepository.upsertPlaybackProgress(
        session = session,
        snapshot = PlayerPlaybackSnapshot(
            isLoading = false,
            isPlaying = false,
            isEnded = !result.endedByUser,
            durationMs = durationMs ?: 0L,
            positionMs = result.positionMs,
        ),
    )
    if (durationMs != null && durationMs > 0L) {
        val media = buildTrackingMediaReference(
            contentType = session.parentMetaType,
            parentMetaId = session.parentMetaId,
            videoId = session.videoId,
            title = session.title,
            seasonNumber = session.seasonNumber,
            episodeNumber = session.episodeNumber,
            episodeTitle = session.episodeTitle,
        )
        if (media.hasResolvableIdentity) {
            runCatching {
                TrackingScrobbleCoordinator.scrobble(
                    profileId = session.profileId,
                    action = TrackingScrobbleAction.STOP,
                    event = TrackingScrobbleEvent(
                        media = media,
                        progressPercent = (result.positionMs.toDouble() / durationMs.toDouble() * 100).coerceIn(0.0, 100.0),
                    ),
                )
            }
        }
    }
}
