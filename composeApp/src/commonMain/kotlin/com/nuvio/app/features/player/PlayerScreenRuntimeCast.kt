package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.cast.CastMediaRequest
import com.nuvio.app.features.cast.CastPlatform
import com.nuvio.app.features.cast.CastSessionState
import com.nuvio.app.features.cast.CastSourceIssue
import com.nuvio.app.features.cast.castCompatibilityIssues
import com.nuvio.app.features.cast.castSourceIssue
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cast_compatibility_toast
import nuvio.composeapp.generated.resources.cast_session_lost
import nuvio.composeapp.generated.resources.cast_source_local_only
import nuvio.composeapp.generated.resources.cast_source_separate_audio
import org.jetbrains.compose.resources.stringResource

private val castLog = Logger.withTag("Cast")

private const val CAST_TOAST_DURATION_MS = 4_000L

/** URL the surface plays: the P2P engine output for torrents, the stream link otherwise. */
internal val PlayerScreenRuntime.playerSurfaceSourceUrl: String?
    get() = if (activeTorrentInfoHash != null) p2pResolvedSourceUrl else activeSourceUrl

/**
 * Keeps [PlayerScreenRuntime.castSessionId] in sync with the cast session: while a session is
 * connected and the stream can be cast, the player renders the cast surface instead of the
 * local one. Changing it changes the playback key, exactly like switching playback engine, so
 * the new surface starts from the current position. Call it before `resetIdentityStateIfNeeded`.
 */
@Composable
internal fun PlayerScreenRuntime.BindCastHandoff() {
    val castState by CastPlatform.state.collectAsStateWithLifecycle()
    val connected = castState as? CastSessionState.Connected
    val sourceUrl = playerSurfaceSourceUrl
    // P2P streams and downloads are served to the receiver by the LAN proxy of this device.
    val canServeLocal = remember(connected?.sessionId, sourceUrl) { CastPlatform.canServeLocalMedia() }
    val issue = sourceUrl?.let { castSourceIssue(it, activeSourceAudioUrl) }
        ?.takeUnless { it == CastSourceIssue.LocalOnly && canServeLocal }
    val target = connected?.sessionId?.takeIf { sourceUrl != null && issue == null }
    val stream = activeStream
    castCompatibilityIssues = remember(stream, title) { stream?.castCompatibilityIssues(title).orEmpty() }

    // Opening the player during a session goes straight to the receiver, without starting the
    // local player for a frame.
    if (!castInitialSyncDone) {
        castInitialSyncDone = true
        castSessionId = target
        castDeviceName = connected?.deviceName
        if (target != null) castLog.i { "Player opened during a cast session: playing on the receiver" }
    }

    val issueMessage = when (issue) {
        CastSourceIssue.LocalOnly -> stringResource(Res.string.cast_source_local_only)
        CastSourceIssue.SeparateAudio -> stringResource(Res.string.cast_source_separate_audio)
        null -> null
    }
    LaunchedEffect(connected?.sessionId, sourceUrl, issueMessage) {
        val sessionId = connected?.sessionId ?: return@LaunchedEffect
        if (sourceUrl == null || issueMessage == null) return@LaunchedEffect
        val key = "$sessionId|$sourceUrl"
        if (castIssueNotifiedFor == key) return@LaunchedEffect
        castIssueNotifiedFor = key
        castLog.i { "Stream cannot be cast ($issue): it keeps playing locally" }
        NuvioToastController.show(issueMessage, CAST_TOAST_DURATION_MS)
    }

    // Warn once per session and stream about formats many receivers cannot play.
    val compatibilityMessage = castCompatibilityIssues.takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.cast_compatibility_toast, it.joinToString(", ")) }
    LaunchedEffect(target, sourceUrl, compatibilityMessage) {
        if (target == null || sourceUrl == null || compatibilityMessage == null) return@LaunchedEffect
        val key = "$target|$sourceUrl"
        if (castCompatibilityNotifiedFor == key) return@LaunchedEffect
        castCompatibilityNotifiedFor = key
        castLog.i { "Casting a stream with possible compatibility issues: ${castCompatibilityIssues.joinToString()}" }
        NuvioToastController.show(compatibilityMessage, CAST_TOAST_DURATION_MS)
    }

    val sessionLostMessage = stringResource(Res.string.cast_session_lost)
    LaunchedEffect(target, connected?.deviceName) {
        if (connected != null) castDeviceName = connected.deviceName
        if (target == castSessionId) return@LaunchedEffect
        val sessionLost = target == null && castSessionId != null && connected == null
        handoffCast(target)
        if (sessionLost) NuvioToastController.show(sessionLostMessage, CAST_TOAST_DURATION_MS)
    }
}

/** Moves playback between this device and the receiver, keeping the current position. */
private fun PlayerScreenRuntime.handoffCast(target: String?) {
    val leavingCast = target == null && castSessionId != null
    if (initialSeekApplied && playbackSnapshotKey == activePlaybackKey && playbackSnapshot.positionMs > 0L) {
        activeInitialPositionMs = playbackSnapshot.positionMs
        activeInitialProgressFraction = null
    }
    // Back on the phone playback starts paused: the user just turned the TV off or disconnected.
    castHandoffPlayWhenReady = if (leavingCast) false else shouldPlay
    flushWatchProgress()
    castLog.i {
        if (target != null) {
            "Moving playback to the receiver at ${activeInitialPositionMs}ms"
        } else {
            "Moving playback back to this device at ${activeInitialPositionMs}ms"
        }
    }
    castSessionId = target
    controlsVisible = true
}

internal fun PlayerScreenRuntime.castMediaRequest(sourceUrl: String): CastMediaRequest = CastMediaRequest(
    sourceUrl = sourceUrl,
    streamType = activeStreamType,
    responseHeaders = activeSourceResponseHeaders,
    requestHeaders = activeSourceHeaders,
    title = if (isLiveTvPlayback) activeStreamTitle else title,
    episodeTitle = activeEpisodeTitle,
    seasonNumber = activeSeasonNumber,
    episodeNumber = activeEpisodeNumber,
    subtitle = activeProviderName.takeIf { it.isNotBlank() },
    posterUrl = if (isLiveTvPlayback) activeLogo else poster,
    backdropUrl = activeEpisodeThumbnail ?: background,
    isLive = isLiveTvPlayback,
    compatibilityIssues = castCompatibilityIssues,
)

/** Artwork shown behind the controls while casting. */
internal val PlayerScreenRuntime.castBackdropUrl: String?
    get() = when {
        isLiveTvPlayback -> activeLogo
        else -> background ?: activeEpisodeThumbnail ?: poster
    }
