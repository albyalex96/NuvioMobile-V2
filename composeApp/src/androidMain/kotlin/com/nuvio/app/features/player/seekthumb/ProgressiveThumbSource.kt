package com.nuvio.app.features.player.seekthumb

import android.graphics.Bitmap
import android.os.SystemClock

/** One keyframe ready for the decoder, and where it is in the title. */
internal class ThumbKeyframe(
    val positionMs: Long,
    val accessUnit: ThumbAu,
    val track: VideoTrack,
)

/**
 * Where previews of one stream come from. [keyFor] maps a position to the picture serving it (the key is that
 * picture's own position in ms, so nearby positions share it); [fetch] reads that picture. Both block: call them on
 * the worker thread only.
 */
internal interface ThumbSource : AutoCloseable {
    /** For the log: container, codec and size. */
    val description: String

    /** The resolution of the stream's pictures, to refuse a decode the device cannot afford. */
    val videoWidth: Int
    val videoHeight: Int

    fun keyFor(positionMs: Long): Long

    fun fetch(key: Long): ThumbKeyframe?

    /** Cancels a read under way (the session is closing). */
    fun cancel() = Unit
}

/**
 * A single file (Matroska or MP4) read by range request: its keyframe index from the Cues or the moov box, then only
 * the bytes of the keyframe asked for. The index format and the extraction are Nuvio Arc's (GPL-3.0).
 */
internal class ProgressiveThumbSource private constructor(
    private val reader: CountingRangeReader,
    private val index: MediaIndex,
) : ThumbSource {
    private val extractor = KeyframeExtractor(index, reader)

    override val description: String =
        "${index.kind} ${index.video.codec} ${index.video.width}x${index.video.height} " +
            "${index.video.bitDepth}-bit keyframes=${index.keyframes.count}"
    override val videoWidth: Int get() = index.video.width
    override val videoHeight: Int get() = index.video.height
    val requests: Int get() = reader.requests
    val bytes: Long get() = reader.bytes

    override fun keyFor(positionMs: Long): Long {
        val i = index.keyframes.nearest(positionMs * 1000L)
        return if (i < 0) -1L else index.keyframes.ptsUs[i] / 1000L
    }

    override fun fetch(key: Long): ThumbKeyframe? {
        val i = index.keyframes.nearest(key * 1000L)
        if (i < 0) return null
        val buffer = ThumbBuffer.single()
        val au = extractor.extractAu(i, buffer)
        if (au == null) {
            buffer.release()
            return null
        }
        return ThumbKeyframe(index.keyframes.ptsUs[i] / 1000L, au, index.video)
    }

    override fun cancel() = reader.cancel()

    override fun close() = reader.close()

    companion object {
        /** Throws [UnsupportedMediaException] for a container without a usable index, IOException on network errors. */
        fun open(reader: CountingRangeReader, head: ByteArray): ProgressiveThumbSource {
            val t0 = SystemClock.elapsedRealtime()
            val index = when {
                MkvIndexReader.looksLikeMatroska(head) -> MkvIndexReader.read(reader, head)
                Mp4IndexReader.looksLikeMp4(head) -> Mp4IndexReader.read(reader, head)
                else -> throw UnsupportedMediaException("container not supported")
            }
            if (index.keyframes.count == 0) throw UnsupportedMediaException("no keyframe index")
            seekThumbLog.i {
                "Index ${index.kind} ${index.video.codec} ${index.video.width}x${index.video.height} " +
                    "keyframes=${index.keyframes.count} requests=${reader.requests} kb=${reader.bytes / 1024} " +
                    "t=${SystemClock.elapsedRealtime() - t0}ms"
            }
            return ProgressiveThumbSource(reader, index)
        }
    }
}

/** Decodes and renders one keyframe; [maxWidthPx] bounds the picture. Null when the decoder refuses it. */
internal fun FfmpegThumbDecoder.picture(keyframe: ThumbKeyframe, maxWidthPx: Int): Bitmap? {
    val track = keyframe.track
    val threads = if (track.width.toLong() * track.height > 1920L * 1088L) 2 else 1
    val frame = try {
        decode(keyframe.accessUnit, track, threads)
    } finally {
        keyframe.accessUnit.release()
    } ?: return null
    if (track.dolbyVision?.profile == 5 && !frame.colour.doviIpt) {
        throw UnsupportedMediaException("Dolby Vision profile 5 without decoder metadata")
    }
    val decision = ColourClassifier.classify(track.colour, frame.colour, track.dolbyVision)
    val size = ThumbGeometry.outputSize(
        frame.width, frame.height, frame.sarNum, frame.sarDen, track.displayAspect, maxWidth = maxWidthPx,
    )
    return render(frame, decision, size[0], size[1])
}
