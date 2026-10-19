@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.downloads.hls

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import co.touchlab.kermit.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class HlsSourceKind { Video, Audio }

/** One downloaded media track: its files per discontinuity group. */
internal class HlsMediaSource(
    val kind: HlsSourceKind,
    /** The audio rendition; null for the video stream. */
    val track: HlsDownloadTrack?,
    val format: HlsSegmentFormat,
    val groupFiles: Map<Int, File>,
    /** Packed audio: MPEG-2 timestamp (90 kHz) of the first segment of each group, from its ID3 tag. */
    val packedPtsByGroup: Map<Int, Long> = emptyMap(),
)

internal class HlsSubtitleSource(
    val track: HlsDownloadTrack,
    /** WebVTT segment files with their discontinuity group. */
    val segments: List<Pair<Int, File>>,
)

internal class HlsConversionRequest(
    val sources: List<HlsMediaSource>,
    val subtitles: List<HlsSubtitleSource>,
    /** Start of each discontinuity group on the output timeline (sum of the previous durations). */
    val groupOffsetsUs: List<Long>,
    val totalDurationUs: Long,
    val includeMuxedAudio: Boolean,
    val muxedAudioLanguage: String?,
    val muxedAudioName: String?,
)

internal class HlsConversionResult(
    val file: File,
    val container: HlsOutputContainer,
    /** MP4 cannot hold the subtitles: SRT text to save next to the video. */
    val sidecarSubtitles: List<Pair<HlsDownloadTrack, String>>,
)

internal class HlsConversionException(message: String) : Exception(message)

/**
 * Merges the downloaded HLS tracks into one file without re-encoding: MP4 when every track fits
 * (H.264/HEVC/AV1/VP9 + AAC/Opus), MKV otherwise (AC-3, E-AC-3, MP3, DTS…). The timestamps of the
 * tracks are aligned per discontinuity group and the groups are laid one after the other.
 */
internal class HlsConverter(private val request: HlsConversionRequest) {
    private val log = Logger.withTag("HlsDownload")
    private val groupCount = request.groupOffsetsUs.size

    private class OutputTrack(
        val index: Int,
        val format: Format,
        val isVideo: Boolean,
        val isSubtitle: Boolean = false,
        val language: String?,
        val name: String?,
        var isDefault: Boolean,
        val isForced: Boolean = false,
    )

    /** [startUs]: earliest first sample of the group; [videoStartUs]: first video sample. */
    private class GroupTiming(val startUs: Long, val videoStartUs: Long?)

    /** Output tracks of one source, matched by order in every group. */
    private class SourceOutputs(val video: OutputTrack?, val audio: List<OutputTrack>)

    suspend fun convert(outputWithoutExtension: File, onProgress: (Float) -> Unit): HlsConversionResult {
        val timings = probeGroupTimings()
        val groupStarts = timings.map { it.startUs }
        val outputs = probeOutputs()
        val mediaTracks = outputs.flatMap { listOfNotNull(it.video) + it.audio }
        if (mediaTracks.isEmpty()) throw HlsConversionException("no audio or video track found")
        val unsupported = mediaTracks.filter { !it.format.isMp4Compatible() && it.format.matroskaCodec() == null }
        if (unsupported.isNotEmpty()) {
            throw HlsConversionException("unsupported codec ${unsupported.joinToString { it.format.sampleMimeType.orEmpty() }}")
        }
        val container = if (mediaTracks.all { it.format.isMp4Compatible() }) HlsOutputContainer.Mp4 else HlsOutputContainer.Mkv
        log.i {
            "Converting to $container: " + mediaTracks.joinToString { "${it.format.sampleMimeType}${it.language?.let { lang -> "($lang)" } ?: ""}" } +
                ", ${request.subtitles.size} subtitle track(s), $groupCount group(s)"
        }

        val cues = request.subtitles.map { source -> source to subtitleCues(source, groupStarts) }
        val subtitleTracks = if (container == HlsOutputContainer.Mkv) {
            cues.mapIndexed { index, (source, _) ->
                OutputTrack(
                    index = mediaTracks.size + index,
                    format = Format.Builder().setSampleMimeType("text/plain").build(),
                    isVideo = false,
                    isSubtitle = true,
                    language = source.track.language,
                    name = source.track.name ?: source.track.label,
                    isDefault = source.track.isDefault,
                    isForced = source.track.forced,
                )
            }
        } else {
            emptyList()
        }

        val file = File(outputWithoutExtension.path + "." + container.extension)
        file.delete()
        val sink: Sink = if (container == HlsOutputContainer.Mp4) Mp4Sink(file, mediaTracks) else MkvSink(file, mediaTracks + subtitleTracks)
        var success = false
        try {
            val pendingCues = subtitleTracks.flatMapIndexed { index, track ->
                cues[index].second.map { track to it }
            }.sortedBy { it.second.startUs }.toMutableList()
            var lastProgressUs = -1L
            for (group in 0 until groupCount) {
                writeGroup(group, timings[group], outputs, sink, pendingCues) { timeUs ->
                    if (timeUs - lastProgressUs >= 2_000_000L) {
                        lastProgressUs = timeUs
                        onProgress((timeUs.toFloat() / request.totalDurationUs.coerceAtLeast(1L)).coerceIn(0f, 0.99f))
                    }
                }
            }
            pendingCues.forEach { (track, cue) -> sink.writeCue(track, cue) }
            sink.finish()
            success = true
        } finally {
            runCatching { sink.close() }
            if (!success) file.delete()
        }
        onProgress(1f)
        val sidecars = if (container == HlsOutputContainer.Mp4) {
            cues.filter { it.second.isNotEmpty() }.map { (source, list) -> source.track to HlsWebVtt.toSrt(list) }
        } else {
            emptyList()
        }
        return HlsConversionResult(file, container, sidecars)
    }

    private suspend fun writeGroup(
        group: Int,
        timing: GroupTiming,
        outputs: List<SourceOutputs>,
        sink: Sink,
        pendingCues: MutableList<Pair<OutputTrack, HlsSubtitleCue>>,
        onTime: (Long) -> Unit,
    ) {
        val offsetUs = request.groupOffsetsUs[group]
        val groupStartUs = timing.startUs
        val readers = request.sources.mapIndexedNotNull { index, source ->
            val file = source.groupFiles[group]?.takeIf { it.length() > 0L } ?: return@mapIndexedNotNull null
            GroupReader(source, outputs[index], HlsSampleReader(file, source.format), packedOffsetUs(source, group, timing.videoStartUs))
        }
        try {
            readers.forEach { it.advance() }
            var written = 0
            while (true) {
                val reader = readers.filter { it.head != null }.minByOrNull { it.headTimeUs } ?: break
                val (track, sample) = reader.head!!
                val timeUs = reader.headTimeUs - groupStartUs + offsetUs
                while (pendingCues.isNotEmpty() && pendingCues.first().second.startUs <= timeUs) {
                    val (cueTrack, cue) = pendingCues.removeAt(0)
                    sink.writeCue(cueTrack, cue)
                }
                sink.write(track, timeUs.coerceAtLeast(0L), sample.data, sample.flags)
                onTime(timeUs)
                reader.advance()
                if (++written % 256 == 0) currentCoroutineContext().ensureActive()
            }
        } finally {
            readers.forEach { runCatching { it.reader.close() } }
        }
    }

    /** Samples of one source in one group, keeping only the tracks that go to the output. */
    private inner class GroupReader(
        val source: HlsMediaSource,
        val outputs: SourceOutputs,
        val reader: HlsSampleReader,
        val packedOffsetUs: Long,
    ) {
        var head: Pair<OutputTrack, HlsSample>? = null
        val headTimeUs: Long get() = head!!.second.timeUs + packedOffsetUs

        fun advance() {
            head = null
            while (true) {
                val sample = reader.next() ?: return
                val output = outputFor(sample.trackId) ?: continue
                head = output to sample
                return
            }
        }

        private fun outputFor(trackId: Int): OutputTrack? {
            val tracks = reader.tracks
            val track = tracks.firstOrNull { it.id == trackId } ?: return null
            return when (track.type) {
                C.TRACK_TYPE_VIDEO -> outputs.video?.takeIf { tracks.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.id == trackId }
                C.TRACK_TYPE_AUDIO -> outputs.audio.getOrNull(tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.indexOfFirst { it.id == trackId })
                else -> null
            }
        }
    }

    /** First timestamp of every group: the earliest first sample among its tracks. */
    private fun probeGroupTimings(): List<GroupTiming> = (0 until groupCount).map { group ->
        val videoStart = request.sources.firstOrNull { it.kind == HlsSourceKind.Video }
            ?.let { firstSampleTime(it, group, null) }
        val starts = request.sources.mapNotNull { source ->
            if (source.kind == HlsSourceKind.Video) videoStart else firstSampleTime(source, group, videoStart)
        }
        GroupTiming(starts.minOrNull() ?: 0L, videoStart)
    }

    private fun firstSampleTime(source: HlsMediaSource, group: Int, videoStartUs: Long?): Long? {
        val file = source.groupFiles[group]?.takeIf { it.length() > 0L } ?: return null
        val relevant = if (source.kind == HlsSourceKind.Video) {
            setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
        } else {
            setOf(C.TRACK_TYPE_AUDIO)
        }
        HlsSampleReader(file, source.format).use { reader ->
            repeat(PROBE_SAMPLE_LIMIT) {
                val sample = reader.next() ?: return null
                val type = reader.tracks.firstOrNull { it.id == sample.trackId }?.type
                if (type in relevant) return sample.timeUs + packedOffsetUs(source, group, videoStartUs)
            }
        }
        return null
    }

    /** Packed audio starts at zero: its real start is the ID3 timestamp, or the video start. */
    private fun packedOffsetUs(source: HlsMediaSource, group: Int, referenceUs: Long?): Long {
        if (source.format !in PACKED_FORMATS) return 0L
        val pts = source.packedPtsByGroup[group] ?: return referenceUs ?: 0L
        val unwrapped = HlsWebVtt.unwrapPts(pts, referenceUs?.let { it * 9L / 100L })
        return unwrapped * 100L / 9L
    }

    /** Formats of the output tracks, read from the start of the first group of each source. */
    private fun probeOutputs(): List<SourceOutputs> {
        var nextIndex = 0
        val outputs = request.sources.map { source ->
            val file = (0 until groupCount).firstNotNullOfOrNull { group -> source.groupFiles[group]?.takeIf { it.length() > 0L } }
                ?: return@map SourceOutputs(null, emptyList())
            val tracks = HlsSampleReader(file, source.format).use { reader ->
                var samples = 0
                while (samples < PROBE_SAMPLE_LIMIT) {
                    reader.next() ?: break
                    samples++
                    val relevant = reader.tracks.filter { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO }
                    if (relevant.isNotEmpty() && relevant.all { it.format != null && it.sawSample }) break
                }
                reader.tracks.filter { it.format != null && it.sawSample }
            }
            when (source.kind) {
                HlsSourceKind.Video -> {
                    val video = tracks.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.let { track ->
                        OutputTrack(nextIndex++, track.format!!, isVideo = true, language = null, name = null, isDefault = true)
                    }
                    val muxed = if (request.includeMuxedAudio) tracks.filter { it.type == C.TRACK_TYPE_AUDIO } else emptyList()
                    val audio = muxed.map { track ->
                        val single = muxed.size == 1
                        OutputTrack(
                            index = nextIndex++,
                            format = track.format!!,
                            isVideo = false,
                            language = (if (single) request.muxedAudioLanguage else null) ?: track.format!!.language,
                            name = if (single) request.muxedAudioName else null,
                            isDefault = false,
                        )
                    }
                    SourceOutputs(video, audio)
                }
                HlsSourceKind.Audio -> {
                    val audio = tracks.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.let { track ->
                        OutputTrack(
                            index = nextIndex++,
                            format = track.format!!,
                            isVideo = false,
                            language = source.track?.language ?: track.format!!.language,
                            name = source.track?.name,
                            isDefault = source.track?.isDefault == true,
                        )
                    }
                    if (audio == null) log.w { "No audio found in the rendition ${source.track?.label}" }
                    SourceOutputs(null, listOfNotNull(audio))
                }
            }
        }
        // Exactly one default audio track: the chosen default, otherwise the first one.
        val audio = outputs.flatMap { it.audio }
        val default = audio.firstOrNull { it.isDefault } ?: audio.firstOrNull()
        audio.forEach { it.isDefault = it === default }
        return outputs
    }

    private fun subtitleCues(source: HlsSubtitleSource, groupStarts: List<Long>): List<HlsSubtitleCue> {
        val cues = source.segments.flatMap { (group, file) ->
            val segment = runCatching { HlsWebVtt.parseSegment(file.readText()) }.getOrNull() ?: return@flatMap emptyList()
            val start = groupStarts.getOrElse(group) { 0L }
            val offset = request.groupOffsetsUs.getOrElse(group) { 0L }
            HlsWebVtt.mapCues(segment, referencePts = start * 9L / 100L) { mpegUs -> mpegUs - start + offset }
        }
        return HlsWebVtt.mergeCues(cues).also { log.d { "Subtitle ${source.track.label}: ${it.size} cues" } }
    }

    private interface Sink {
        fun write(track: OutputTrack, timeUs: Long, data: ByteArray, flags: Int)
        fun writeCue(track: OutputTrack, cue: HlsSubtitleCue)
        fun finish()
        fun close()
    }

    private class Mp4Sink(file: File, tracks: List<OutputTrack>) : Sink {
        private val muxer = Mp4Muxer.Builder(FileOutputStream(file)).build()
        private val ids = tracks.associate { track ->
            val format = track.format.buildUpon()
                .setLanguage(track.language ?: track.format.language)
                .build()
            track.index to muxer.addTrack(format)
        }

        override fun write(track: OutputTrack, timeUs: Long, data: ByteArray, flags: Int) {
            val id = ids[track.index] ?: return
            muxer.writeSampleData(id, ByteBuffer.wrap(data), BufferInfo(timeUs, data.size, flags and C.BUFFER_FLAG_KEY_FRAME))
        }

        override fun writeCue(track: OutputTrack, cue: HlsSubtitleCue) = Unit

        override fun finish() = Unit

        override fun close() = muxer.close()
    }

    private class MkvSink(file: File, tracks: List<OutputTrack>) : Sink {
        private val writer = MatroskaWriter(file)
        private val codecs = tracks.filterNot { it.isSubtitle }.associate { it.index to it.format.matroskaCodec()!! }

        init {
            writer.start(tracks.map { track ->
                val codec = codecs[track.index]
                MatroskaWriter.Track(
                    number = track.index + 1,
                    type = when {
                        track.isSubtitle -> MatroskaWriter.TrackType.Subtitle
                        track.isVideo -> MatroskaWriter.TrackType.Video
                        else -> MatroskaWriter.TrackType.Audio
                    },
                    codecId = codec?.codecId ?: "S_TEXT/UTF8",
                    codecPrivate = codec?.codecPrivate,
                    language = track.language,
                    name = track.name,
                    isDefault = track.isDefault,
                    isForced = track.isForced,
                    width = track.format.width.takeIf { track.isVideo && it > 0 },
                    height = track.format.height.takeIf { track.isVideo && it > 0 },
                    sampleRate = track.format.sampleRate.takeIf { !track.isVideo && !track.isSubtitle && it > 0 },
                    channels = track.format.channelCount.takeIf { !track.isVideo && !track.isSubtitle && it > 0 },
                    codecDelayNs = codec?.codecDelayNs,
                    seekPreRollNs = codec?.seekPreRollNs,
                )
            })
        }

        override fun write(track: OutputTrack, timeUs: Long, data: ByteArray, flags: Int) {
            val codec = codecs[track.index] ?: return
            val payload = if (codec.convertAnnexB) annexBToLengthPrefixed(data) else data
            writer.writeSample(track.index + 1, timeUs, payload, keyFrame = flags and C.BUFFER_FLAG_KEY_FRAME != 0)
        }

        override fun writeCue(track: OutputTrack, cue: HlsSubtitleCue) {
            writer.writeSample(
                trackNumber = track.index + 1,
                timeUs = cue.startUs,
                data = cue.text.encodeToByteArray(),
                keyFrame = true,
                durationUs = cue.endUs - cue.startUs,
            )
        }

        override fun finish() = writer.finish()

        override fun close() = writer.close()
    }

    private companion object {
        const val PROBE_SAMPLE_LIMIT = 4_000
        val PACKED_FORMATS = setOf(HlsSegmentFormat.PackedAac, HlsSegmentFormat.PackedAc3, HlsSegmentFormat.PackedMp3)
    }
}
