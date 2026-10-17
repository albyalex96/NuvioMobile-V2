package com.nuvio.app.features.cast

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.MimeTypes
import co.touchlab.kermit.Logger
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.core.mediaproxy.MediaProxyHandle
import com.nuvio.app.core.mediaproxy.MediaProxyOptions
import com.nuvio.app.core.mediaproxy.MediaProxyScope
import com.nuvio.app.core.mediaproxy.MediaProxySettingsRepository
import com.nuvio.app.core.mediaproxy.MediaProxySource
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.inferPlaybackMimeType
import com.nuvio.app.features.player.probeMimeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cast_lan_unavailable
import nuvio.composeapp.generated.resources.cast_load_failed
import nuvio.composeapp.generated.resources.cast_load_failed_with_issues
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("Cast")

private const val PROGRESS_INTERVAL_MS = 500L
private const val MIN_PLAYBACK_RATE = 0.5
private const val MAX_PLAYBACK_RATE = 2.0

@Composable
internal actual fun CastPlayerSurface(
    request: CastMediaRequest,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val messages = CastErrorMessages(
        loadFailed = if (request.compatibilityIssues.isEmpty()) {
            stringResource(Res.string.cast_load_failed)
        } else {
            stringResource(Res.string.cast_load_failed_with_issues, request.compatibilityIssues.joinToString(", "))
        },
        lanUnavailable = stringResource(Res.string.cast_lan_unavailable),
    )
    val scope = rememberCoroutineScope()
    val latestPlayWhenReady by rememberUpdatedState(playWhenReady)
    val latestOnSnapshot by rememberUpdatedState(onSnapshot)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnInitialPositionHandled by rememberUpdatedState(onInitialPositionHandled)
    var playback by remember { mutableStateOf<CastRemotePlayback?>(null) }

    DisposableEffect(request.sourceUrl) {
        val session = CastPlatform.currentSession()
        val client = session?.remoteMediaClient
        if (session == null || client == null) {
            log.w { "No connected cast session for the cast surface" }
            latestOnError(messages.loadFailed)
            return@DisposableEffect onDispose {}
        }
        val remote = CastRemotePlayback(
            session = session,
            client = client,
            request = request,
            scope = scope,
            messages = messages,
            onSnapshot = { latestOnSnapshot(it) },
            onError = { message -> latestOnError(message) },
            onLoaded = {
                initialPositionRequestKey?.let { key -> latestOnInitialPositionHandled(key, true) }
            },
        )
        playback = remote
        onControllerReady(remote)
        remote.start(
            startPositionMs = initialPositionMs?.coerceAtLeast(0L) ?: 0L,
            autoplay = latestPlayWhenReady,
        )
        onDispose {
            remote.release()
            playback = null
        }
    }

    LaunchedEffect(playback, playWhenReady) {
        playback?.applyPlayWhenReady(playWhenReady)
    }
}

private class CastErrorMessages(
    val loadFailed: String,
    val lanUnavailable: String,
)

/**
 * One stream loaded on the receiver of [session]. Implements the player controller on top of
 * [RemoteMediaClient]; tracks, subtitles and delays are not available on the Default Media
 * Receiver in this phase and are reported as empty / ignored.
 *
 * Streams the receiver cannot fetch by itself (P2P, downloads, links with headers) go through
 * [LanMediaProxy]; adaptive streams that fail when loaded directly (usually missing CORS
 * headers on the server) are retried once through it. The media keeps the original link as
 * content ID, so [ownsMedia] and the watch party never see the proxy address.
 */
private class CastRemotePlayback(
    private val session: CastSession,
    private val client: RemoteMediaClient,
    private val request: CastMediaRequest,
    private val scope: CoroutineScope,
    private val messages: CastErrorMessages,
    private val onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    private val onError: (String) -> Unit,
    private val onLoaded: () -> Unit,
) : PlayerEngineController {
    /** Our media is on the receiver (load succeeded or it was already playing there). */
    private var loaded = false
    private var released = false
    private var errorReported = false
    private var proxyHandle: MediaProxyHandle? = null
    private var usingProxy = false
    private var proxyFallbackTried = false
    private var lastContentType: String? = null
    private var lastAutoplay = true

    private val statusCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onReceiverStatus()
    }
    private val progressListener = RemoteMediaClient.ProgressListener { _, _ -> onReceiverStatus() }

    fun start(startPositionMs: Long, autoplay: Boolean) {
        client.registerCallback(statusCallback)
        client.addProgressListener(progressListener, PROGRESS_INTERVAL_MS)
        if (ownsMedia()) {
            // Reopening the player while the same stream is still playing on the receiver.
            log.i { "Attaching to the stream already playing on the receiver" }
            loaded = true
            onLoaded()
            publish()
            return
        }
        onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        load(startPositionMs, autoplay)
    }

    fun release() {
        released = true
        client.unregisterCallback(statusCallback)
        client.removeProgressListener(progressListener)
        if (session.isConnected && ownsMedia()) {
            log.i { "Stopping the stream on the receiver" }
            client.stop()
        }
        releaseProxy()
    }

    fun applyPlayWhenReady(playWhenReady: Boolean) {
        if (!loaded || released || !ownsMedia()) return
        if (playWhenReady && client.isPaused) client.play()
        if (!playWhenReady && (client.isPlaying || client.isBuffering)) client.pause()
    }

    private fun load(startPositionMs: Long, autoplay: Boolean, forceProxy: Boolean = false) {
        loaded = false
        errorReported = false
        lastAutoplay = autoplay
        scope.launch {
            val useProxy = forceProxy || request.needsLanProxy || MediaProxySettingsRepository.settings.value.castAlwaysProxy
            val contentUrl = if (useProxy) {
                val handle = withContext(Dispatchers.IO) { registerProxy() }
                if (released) {
                    handle?.let(LanMediaProxy::unregister)
                    return@launch
                }
                if (handle == null) {
                    log.w { "The stream needs the LAN proxy but this device has no local network address" }
                    reportError(messages.lanUnavailable)
                    return@launch
                }
                handle.url
            } else {
                releaseProxy()
                request.sourceUrl
            }
            usingProxy = useProxy
            val contentType = resolveContentType()
            lastContentType = contentType
            if (released) return@launch
            log.i {
                "Loading on the receiver: type=$contentType live=${request.isLive} proxied=$useProxy " +
                    "start=${startPositionMs}ms autoplay=$autoplay"
            }
            val loadRequest = MediaLoadRequestData.Builder()
                .setMediaInfo(buildMediaInfo(contentType, contentUrl))
                .setAutoplay(autoplay)
                .setCurrentTime(if (request.isLive) 0L else startPositionMs)
                .build()
            client.load(loadRequest).setResultCallback { result ->
                if (released) return@setResultCallback
                if (result.status.isSuccess) {
                    log.i { "Receiver loaded the stream" }
                    loaded = true
                    onLoaded()
                    publish()
                } else {
                    log.w {
                        "Receiver load failed: ${CastStatusCodes.getStatusCodeString(result.status.statusCode)} " +
                            result.status.statusMessage.orEmpty()
                    }
                    if (!retryThroughProxy(startPositionMs)) reportError(messages.loadFailed)
                }
            }
        }
    }

    private fun registerProxy(): MediaProxyHandle? {
        releaseProxy()
        val url = request.sourceUrl.trim()
        val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        val source = if (isHttp) {
            MediaProxySource.Remote(url, request.requestHeaders, request.responseHeaders)
        } else {
            MediaProxySource.LocalFile(url)
        }
        return LanMediaProxy.register(source, MediaProxyOptions(scope = MediaProxyScope.Lan, label = "Cast"))
            .also { proxyHandle = it }
    }

    private fun releaseProxy() {
        proxyHandle?.let(LanMediaProxy::unregister)
        proxyHandle = null
    }

    /**
     * Receivers fetch HLS/DASH with XHR, which fails on servers without CORS headers: retry
     * once through the proxy, which adds them. Progressive files do not need CORS, so a failure
     * there is a format problem and is reported as it is.
     */
    private fun retryThroughProxy(positionMs: Long): Boolean {
        val adaptive = lastContentType == "application/x-mpegurl" || lastContentType == "application/dash+xml"
        if (usingProxy || proxyFallbackTried || !adaptive || released) return false
        if (!MediaProxySettingsRepository.settings.value.castProxyFallback) return false
        if (LanMediaProxy.lanAddress() == null) return false
        proxyFallbackTried = true
        log.i { "Direct load failed for an adaptive stream: retrying through the LAN proxy" }
        onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        load(positionMs, lastAutoplay, forceProxy = true)
        return true
    }

    private suspend fun resolveContentType(): String {
        val inferred = inferPlaybackMimeType(
            url = request.sourceUrl,
            responseHeaders = request.responseHeaders,
            streamType = request.streamType,
        ) ?: withContext(Dispatchers.IO) {
            runCatching { probeMimeType(request.sourceUrl, request.requestHeaders) }.getOrNull()
        }
        return castContentType(inferred)
    }

    private fun buildMediaInfo(contentType: String, contentUrl: String): MediaInfo {
        val isEpisode = request.seasonNumber != null && request.episodeNumber != null
        val metadata = MediaMetadata(
            if (isEpisode) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE,
        ).apply {
            if (isEpisode) {
                putString(MediaMetadata.KEY_SERIES_TITLE, request.title)
                putString(MediaMetadata.KEY_TITLE, request.episodeTitle?.takeIf { it.isNotBlank() } ?: request.title)
                putInt(MediaMetadata.KEY_SEASON_NUMBER, request.seasonNumber)
                putInt(MediaMetadata.KEY_EPISODE_NUMBER, request.episodeNumber)
            } else {
                putString(MediaMetadata.KEY_TITLE, request.title)
            }
            request.subtitle?.takeIf { it.isNotBlank() }?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            listOfNotNull(request.posterUrl, request.backdropUrl)
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
                .forEach { addImage(WebImage(Uri.parse(it))) }
        }
        return MediaInfo.Builder(request.sourceUrl)
            .setContentUrl(contentUrl)
            .setContentType(contentType)
            .setStreamType(if (request.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()
    }

    private fun ownsMedia(): Boolean = client.mediaInfo?.contentId == request.sourceUrl

    private fun onReceiverStatus() {
        if (released || !loaded) return
        if (!ownsMedia()) {
            // Another sender replaced the stream: stop driving the runtime from its status.
            onSnapshot(PlayerPlaybackSnapshot(isLoading = false, isPlaying = false))
            return
        }
        val status = client.mediaStatus
        if (status?.playerState == MediaStatus.PLAYER_STATE_IDLE && status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
            log.w { "Receiver reported a playback error" }
            val positionMs = client.approximateStreamPosition.coerceAtLeast(0L)
            loaded = false
            if (!retryThroughProxy(positionMs)) reportError(messages.loadFailed)
            return
        }
        publish()
    }

    private fun publish() {
        onSnapshot(client.snapshot())
    }

    private fun reportError(message: String) {
        if (errorReported) return
        errorReported = true
        onError(message)
    }

    override fun play() {
        if (loaded && ownsMedia()) client.play()
    }

    override fun pause() {
        if (loaded && ownsMedia()) client.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (!loaded || !ownsMedia()) return
        client.seek(MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0L)).build())
    }

    override fun seekBy(offsetMs: Long) {
        val target = (client.approximateStreamPosition + offsetMs).coerceAtLeast(0L)
        val duration = client.streamDuration
        seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    override fun retry() {
        if (released) return
        val resumeMs = client.approximateStreamPosition.takeIf { ownsMedia() } ?: 0L
        log.i { "Retrying the stream on the receiver" }
        onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        load(resumeMs, autoplay = true, forceProxy = usingProxy)
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (!loaded || !ownsMedia()) return
        client.setPlaybackRate(speed.toDouble().coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE))
    }

    override fun setMuted(muted: Boolean) {
        runCatching { session.setMute(muted) }
            .onFailure { error -> log.w(error) { "Could not change the receiver mute state" } }
    }

    override fun getAudioTracks(): List<AudioTrack> = emptyList()
    override fun getSubtitleTracks(): List<SubtitleTrack> = emptyList()
    override fun applyAudioLanguagePreferences(languages: List<String>) = Unit
    override fun selectAudioTrack(index: Int) = Unit
    override fun selectSubtitleTrack(index: Int) = Unit
    override fun setSubtitleUri(url: String) = Unit
    override fun clearExternalSubtitle() = Unit
    override fun clearExternalSubtitleAndSelect(trackIndex: Int) = Unit
}

private fun RemoteMediaClient.snapshot(): PlayerPlaybackSnapshot {
    val status = mediaStatus
    val playerState = status?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
    val ended = playerState == MediaStatus.PLAYER_STATE_IDLE && status?.idleReason == MediaStatus.IDLE_REASON_FINISHED
    val durationMs = streamDuration.takeIf { it > 0L } ?: mediaInfo?.streamDuration?.takeIf { it > 0L } ?: 0L
    val positionMs = approximateStreamPosition.coerceAtLeast(0L)
    return PlayerPlaybackSnapshot(
        isLoading = playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
            playerState == MediaStatus.PLAYER_STATE_LOADING ||
            playerState == MediaStatus.PLAYER_STATE_UNKNOWN,
        isPlaying = playerState == MediaStatus.PLAYER_STATE_PLAYING,
        isEnded = ended,
        durationMs = durationMs,
        positionMs = if (ended && durationMs > 0L) durationMs else positionMs,
        // The receiver does not report its buffer.
        bufferedPositionMs = positionMs,
        playbackSpeed = status?.playbackRate?.toFloat()?.takeIf { it > 0f } ?: 1f,
        videoWidth = status?.videoInfo?.width ?: 0,
        videoHeight = status?.videoInfo?.height ?: 0,
    )
}

/**
 * Content type for the Default Media Receiver: adaptive formats keep their own type (they pick
 * the receiver's HLS/DASH player), everything progressive is sent as MP4/WebM because older
 * Chromecasts refuse "video/x-matroska" even when they can decode the file.
 */
internal fun castContentType(mimeType: String?): String = when (mimeType) {
    MimeTypes.APPLICATION_M3U8 -> "application/x-mpegurl"
    MimeTypes.APPLICATION_MPD -> "application/dash+xml"
    MimeTypes.APPLICATION_SS -> "application/vnd.ms-sstr+xml"
    MimeTypes.VIDEO_WEBM -> MimeTypes.VIDEO_WEBM
    else -> if (mimeType?.startsWith("audio/") == true) mimeType else MimeTypes.VIDEO_MP4
}
