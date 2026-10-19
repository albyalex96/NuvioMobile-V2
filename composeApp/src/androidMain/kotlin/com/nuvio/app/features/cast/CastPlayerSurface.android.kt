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
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.MimeTypes
import co.touchlab.kermit.Logger
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
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
import com.nuvio.app.features.player.languageMatchesPreference
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
    val latestOnControllerReady by rememberUpdatedState(onControllerReady)
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
        remote.start(
            startPositionMs = initialPositionMs?.coerceAtLeast(0L) ?: 0L,
            autoplay = latestPlayWhenReady,
        )
        onDispose {
            remote.release()
            playback = null
        }
    }

    LaunchedEffect(playback) {
        val remote = playback ?: return@LaunchedEffect
        // When the playback key changes the runtime clears its controller in an effect that runs
        // after this surface is set up (local engines are created later, so they never race with
        // it): hand the controller over on the next frame, or every command would be lost.
        withFrameNanos { }
        log.d { "Cast controller handed to the player" }
        latestOnControllerReady(remote)
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
 * [RemoteMediaClient]. Audio tracks are the ones the receiver reports for the stream (e.g. the
 * HLS audio renditions); subtitles and delays are not available in this phase and are reported
 * as empty / ignored.
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
    private var hlsFormats: CastHlsFormats? = null
    /** URL sent to the receiver as content URL (the proxy address when proxied). */
    private var loadedContentUrl: String? = null
    /** Content ID the receiver reported once our load succeeded (some receivers change it). */
    private var loadedContentId: String? = null
    private var preferredAudioLanguages: List<String> = emptyList()
    private var audioPreferenceApplied = false
    private var userSelectedAudio = false
    private var loggedTrackCount = -1
    private var hlsFormatsResolved = false

    private val statusCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onReceiverStatus()

        override fun onMediaError(error: MediaError) {
            log.w {
                "Receiver media error: type=${error.type} reason=${error.reason} " +
                    "detailedCode=${error.detailedErrorCode}${error.detailedErrorCode?.let { " (${castDetailedErrorName(it)})" }.orEmpty()}"
            }
        }
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
            loadedContentUrl = contentUrl
            val contentType = resolveContentType()
            lastContentType = contentType
            if (contentType == "application/x-mpegurl" && !hlsFormatsResolved) {
                hlsFormats = detectCastHlsFormats(request.sourceUrl, request.requestHeaders)
                hlsFormatsResolved = true
                log.i { "HLS segment formats: video=${hlsFormats?.video} audio=${hlsFormats?.audio}" }
            }
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
                    loadedContentId = client.mediaInfo?.contentId
                    log.i {
                        "Receiver loaded the stream (contentId kept: ${loadedContentId == request.sourceUrl}, " +
                            "seekable: ${client.mediaStatus?.isMediaCommandSupported(MediaStatus.COMMAND_SEEK)})"
                    }
                    loaded = true
                    audioPreferenceApplied = false
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
            .apply { if (contentType == "application/x-mpegurl") applyHlsFormats(hlsFormats) }
            .build()
    }

    /**
     * Our stream is the one on the receiver. The content ID is the original link, but receivers
     * may report the content URL (the proxy address) or rewrite it, so all of them are accepted.
     */
    private fun ownsMedia(): Boolean {
        val contentId = client.mediaInfo?.contentId ?: return false
        return contentId == request.sourceUrl || contentId == loadedContentUrl ||
            (loadedContentId != null && contentId == loadedContentId)
    }

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
        applyAudioPreferenceIfNeeded()
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
        if (!loaded || !ownsMedia()) {
            log.w { "Seek ignored: loaded=$loaded ownsMedia=${ownsMedia()} contentIdSet=${client.mediaInfo?.contentId != null}" }
            return
        }
        val target = positionMs.coerceAtLeast(0L)
        log.d { "Seeking the receiver to ${target}ms" }
        client.seek(MediaSeekOptions.Builder().setPosition(target).build()).setResultCallback { result ->
            if (!result.status.isSuccess) {
                log.w {
                    "Receiver seek failed: ${CastStatusCodes.getStatusCodeString(result.status.statusCode)} " +
                        result.status.statusMessage.orEmpty()
                }
            }
        }
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

    private fun receiverAudioTracks(): List<MediaTrack> =
        if (loaded && ownsMedia()) {
            client.mediaInfo?.mediaTracks.orEmpty().filter { it.type == MediaTrack.TYPE_AUDIO }
        } else {
            emptyList()
        }

    override fun getAudioTracks(): List<AudioTrack> {
        val tracks = receiverAudioTracks()
        val active = client.mediaStatus?.activeTrackIds?.toSet().orEmpty()
        if (tracks.size != loggedTrackCount) {
            loggedTrackCount = tracks.size
            log.i { "Receiver audio tracks: ${tracks.joinToString { "${it.language ?: "?"}/${it.name ?: "-"}" }.ifEmpty { "none" }}" }
        }
        return tracks.mapIndexed { index, track ->
            AudioTrack(
                index = index,
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: track.language ?: "Audio ${index + 1}",
                language = track.language,
                isSelected = track.id in active,
            )
        }
    }

    override fun getSubtitleTracks(): List<SubtitleTrack> = emptyList()

    override fun applyAudioLanguagePreferences(languages: List<String>) {
        preferredAudioLanguages = languages
        audioPreferenceApplied = false
        applyAudioPreferenceIfNeeded()
    }

    override fun selectAudioTrack(index: Int) {
        val track = receiverAudioTracks().getOrNull(index) ?: return
        userSelectedAudio = true
        activateAudioTrack(track)
    }

    /** Picks the first preferred language the receiver offers, once the tracks are known. */
    private fun applyAudioPreferenceIfNeeded() {
        if (audioPreferenceApplied || userSelectedAudio || preferredAudioLanguages.isEmpty()) return
        val tracks = receiverAudioTracks()
        if (tracks.isEmpty()) return
        audioPreferenceApplied = true
        val preferred = preferredAudioLanguages.firstNotNullOfOrNull { language ->
            tracks.firstOrNull { languageMatchesPreference(it.language, language) }
        } ?: return
        val active = client.mediaStatus?.activeTrackIds?.toSet().orEmpty()
        if (preferred.id !in active) {
            log.i { "Selecting the preferred audio language on the receiver: ${preferred.language}" }
            activateAudioTrack(preferred)
        }
    }

    private fun activateAudioTrack(track: MediaTrack) {
        if (!loaded || !ownsMedia()) return
        val audioIds = receiverAudioTracks().map { it.id }.toSet()
        val keep = client.mediaStatus?.activeTrackIds?.filter { it !in audioIds }.orEmpty()
        log.d { "Activating receiver audio track ${track.id} (${track.language})" }
        client.setActiveMediaTracks((keep + track.id).toLongArray()).setResultCallback { result ->
            if (!result.status.isSuccess) {
                log.w {
                    "Receiver could not switch the audio track: " +
                        CastStatusCodes.getStatusCodeString(result.status.statusCode)
                }
            }
        }
    }

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

/** Names of the CAF detailed error codes most useful to tell why a stream does not play. */
private fun castDetailedErrorName(code: Int): String = when (code) {
    100 -> "MEDIA_UNKNOWN"
    101 -> "MEDIA_ABORTED"
    102 -> "MEDIA_DECODE"
    103 -> "MEDIA_NETWORK"
    104 -> "MEDIA_SRC_NOT_SUPPORTED"
    110 -> "SOURCE_BUFFER_FAILURE"
    200 -> "MEDIAKEYS_UNKNOWN"
    300 -> "NETWORK_UNKNOWN"
    301 -> "SEGMENT_NETWORK"
    311 -> "HLS_NETWORK_MASTER_PLAYLIST"
    312 -> "HLS_NETWORK_PLAYLIST"
    313 -> "HLS_NETWORK_NO_KEY_RESPONSE"
    314 -> "HLS_NETWORK_KEY_LOAD"
    315 -> "HLS_NETWORK_INVALID_SEGMENT"
    316 -> "HLS_SEGMENT_PARSING"
    321 -> "DASH_NETWORK"
    322 -> "DASH_NO_INIT"
    400 -> "MANIFEST_UNKNOWN"
    411 -> "HLS_MANIFEST_MASTER"
    412 -> "HLS_MANIFEST_PLAYLIST"
    420 -> "DASH_MANIFEST_UNKNOWN"
    421 -> "DASH_MANIFEST_NO_PERIODS"
    422 -> "DASH_MANIFEST_NO_MIMETYPE"
    423 -> "DASH_INVALID_SEGMENT_INFO"
    500 -> "SEGMENT_UNKNOWN"
    600 -> "TEXT_UNKNOWN"
    900 -> "APP"
    904 -> "LOAD_INTERRUPTED"
    905 -> "LOAD_FAILED"
    999 -> "GENERIC"
    else -> "code $code"
}

/**
 * Declares the HLS segment formats: without them the receiver expects MPEG-TS with muxed audio
 * and fails on fMP4 (CMAF) streams and on separate packed-audio renditions.
 */
private fun MediaInfo.Builder.applyHlsFormats(formats: CastHlsFormats?) {
    formats ?: return
    setHlsVideoSegmentFormat(
        if (formats.video == CastHlsSegmentFormat.Fmp4) HlsVideoSegmentFormat.FMP4 else HlsVideoSegmentFormat.MPEG2_TS,
    )
    val audioFormat = when (val audio = formats.audio) {
        null -> formats.video.toCastAudioFormat()
        // A separate audio rendition packaged in MPEG-TS.
        CastHlsSegmentFormat.Ts -> HlsSegmentFormat.TS_AAC
        else -> audio.toCastAudioFormat()
    }
    setHlsSegmentFormat(audioFormat)
}

private fun CastHlsSegmentFormat.toCastAudioFormat(): String = when (this) {
    CastHlsSegmentFormat.Ts -> HlsSegmentFormat.TS
    CastHlsSegmentFormat.Fmp4 -> HlsSegmentFormat.FMP4
    CastHlsSegmentFormat.Aac -> HlsSegmentFormat.AAC
    CastHlsSegmentFormat.Ac3 -> HlsSegmentFormat.AC3
    CastHlsSegmentFormat.Eac3 -> HlsSegmentFormat.E_AC3
    CastHlsSegmentFormat.Mp3 -> HlsSegmentFormat.MP3
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
