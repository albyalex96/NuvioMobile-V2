@file:androidx.annotation.OptIn(UnstableApi::class)

package com.nuvio.app.features.player

import androidx.media3.common.C
import co.touchlab.kermit.Logger
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.text.SubtitleParser

/**
 * Audio delay for ExoPlayer (ported from NuvioTV). ExoPlayer has no audio delay property:
 * audio is the master clock, so shifting audio timestamps would pull video along with it.
 * Video sample timestamps are shifted in the opposite direction instead, which gives a real
 * perceived audio delay. The provider is read for every sample, so changes apply live.
 */
internal class AudioDelayMediaSource(
    mediaSource: MediaSource,
    private val audioDelayUsProvider: () -> Long,
) : WrappingMediaSource(mediaSource) {

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod {
        return AudioDelayMediaPeriod(
            mediaPeriod = mediaSource.createPeriod(id, allocator, startPositionUs),
            audioDelayUsProvider = audioDelayUsProvider,
        )
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        val wrappedPeriod = (mediaPeriod as AudioDelayMediaPeriod).mediaPeriod
        mediaSource.releasePeriod(wrappedPeriod)
    }

    override fun onChildSourceInfoRefreshed(timeline: Timeline) {
        refreshSourceInfo(timeline)
    }
}

private val audioDelayLog = Logger.withTag(AUDIO_DELAY_LOG_TAG)

/**
 * Video track check that also works when the sample MIME type is still unknown (e.g. HLS
 * without CODECS): falls back to the track group type, the codecs string and the frame size.
 */
private fun ExoTrackSelection.isVideoSelection(): Boolean {
    val format = selectedFormat
    val sampleType = MimeTypes.getTrackType(format.sampleMimeType)
    if (sampleType != C.TRACK_TYPE_UNKNOWN) return sampleType == C.TRACK_TYPE_VIDEO
    if (trackGroup.type != C.TRACK_TYPE_UNKNOWN) return trackGroup.type == C.TRACK_TYPE_VIDEO
    return MimeTypes.getVideoMediaMimeType(format.codecs) != null ||
        (format.width > 0 && format.height > 0)
}

/** Wraps every media source created by [delegate] in an [AudioDelayMediaSource]. */
internal class AudioDelayMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val audioDelayUsProvider: () -> Long,
) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }

    override fun setSubtitleParserFactory(
        subtitleParserFactory: SubtitleParser.Factory,
    ): MediaSource.Factory = apply { delegate.setSubtitleParserFactory(subtitleParserFactory) }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun experimentalParseSubtitlesDuringExtraction(
        parseSubtitlesDuringExtraction: Boolean,
    ): MediaSource.Factory = apply { delegate.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction) }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        AudioDelayMediaSource(delegate.createMediaSource(mediaItem), audioDelayUsProvider)
}

private class AudioDelayMediaPeriod(
    val mediaPeriod: MediaPeriod,
    private val audioDelayUsProvider: () -> Long,
) : MediaPeriod, MediaPeriod.Callback {

    private var callback: MediaPeriod.Callback? = null

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        this.callback = callback
        mediaPeriod.prepare(this, positionUs)
    }

    override fun maybeThrowPrepareError() {
        mediaPeriod.maybeThrowPrepareError()
    }

    override fun getTrackGroups(): TrackGroupArray = mediaPeriod.trackGroups

    override fun getStreamKeys(trackSelections: List<ExoTrackSelection>): List<StreamKey> =
        mediaPeriod.getStreamKeys(trackSelections)

    override fun selectTracks(
        selections: Array<ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long,
    ): Long {
        val childStreams = arrayOfNulls<SampleStream>(streams.size)
        for (index in streams.indices) {
            childStreams[index] = (streams[index] as? AudioDelaySampleStream)?.childStream ?: streams[index]
        }

        val selectedPositionUs = mediaPeriod.selectTracks(
            selections,
            mayRetainStreamFlags,
            childStreams,
            streamResetFlags,
            positionUs,
        )

        var videoStreams = 0
        for (index in streams.indices) {
            val childStream = childStreams[index]
            streams[index] = when {
                childStream == null -> null
                selections[index]?.isVideoSelection() == true -> {
                    videoStreams++
                    val currentWrapper = streams[index] as? AudioDelaySampleStream
                    if (currentWrapper?.childStream === childStream) {
                        currentWrapper
                    } else {
                        AudioDelaySampleStream(childStream, audioDelayUsProvider)
                    }
                }
                else -> childStream
            }
        }
        audioDelayLog.d {
            "selectTracks: $videoStreams video stream(s) shifted; formats=" +
                selections.joinToString { selection ->
                    selection?.selectedFormat?.let { "${it.sampleMimeType ?: it.containerMimeType}/${it.codecs}" } ?: "-"
                }
        }
        if (videoStreams == 0 && selections.any { it != null }) {
            audioDelayLog.w { "No video stream recognized: audio delay cannot be applied to this source" }
        }

        return selectedPositionUs
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
        mediaPeriod.discardBuffer(positionUs, toKeyframe)
    }

    override fun readDiscontinuity(): Long = mediaPeriod.readDiscontinuity()

    override fun seekToUs(positionUs: Long): Long = mediaPeriod.seekToUs(positionUs)

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long =
        mediaPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters)

    override fun getBufferedPositionUs(): Long = mediaPeriod.bufferedPositionUs

    override fun getNextLoadPositionUs(): Long = mediaPeriod.nextLoadPositionUs

    override fun continueLoading(loadingInfo: LoadingInfo): Boolean = mediaPeriod.continueLoading(loadingInfo)

    override fun isLoading(): Boolean = mediaPeriod.isLoading

    override fun reevaluateBuffer(positionUs: Long) {
        mediaPeriod.reevaluateBuffer(positionUs)
    }

    override fun onPrepared(mediaPeriod: MediaPeriod) {
        callback?.onPrepared(this)
    }

    override fun onContinueLoadingRequested(source: MediaPeriod) {
        callback?.onContinueLoadingRequested(this)
    }
}

private class AudioDelaySampleStream(
    val childStream: SampleStream,
    private val audioDelayUsProvider: () -> Long,
) : SampleStream {

    override fun isReady(): Boolean = childStream.isReady

    override fun maybeThrowError() {
        childStream.maybeThrowError()
    }

    /** Last delay applied to a sample, only to log when the shift actually changes. */
    @Volatile
    private var lastAppliedDelayUs = 0L

    override fun readData(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
        val result = childStream.readData(formatHolder, buffer, readFlags)
        if (result == C.RESULT_BUFFER_READ) {
            val delayUs = audioDelayUsProvider()
            if (delayUs != lastAppliedDelayUs) {
                lastAppliedDelayUs = delayUs
                audioDelayLog.i { "Video samples shifted by ${-delayUs / 1_000}ms (sample at ${buffer.timeUs / 1_000}ms)" }
            }
            buffer.timeUs -= delayUs
        }
        return result
    }

    override fun skipData(positionUs: Long): Int =
        childStream.skipData((positionUs + audioDelayUsProvider()).coerceAtLeast(0L))
}
