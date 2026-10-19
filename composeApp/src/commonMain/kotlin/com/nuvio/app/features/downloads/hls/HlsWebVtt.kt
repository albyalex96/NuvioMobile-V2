package com.nuvio.app.features.downloads.hls

data class HlsSubtitleCue(val startUs: Long, val endUs: Long, val text: String)

/**
 * One WebVTT segment of an HLS subtitle playlist. With `X-TIMESTAMP-MAP` the cue times are mapped
 * to the MPEG-2 timestamps of the video ([mpegTs] at [localUs]); without it they are playlist times.
 */
data class HlsWebVttSegment(
    val cues: List<HlsSubtitleCue>,
    val mpegTs: Long?,
    val localUs: Long,
)

object HlsWebVtt {
    private const val MPEG_TS_WRAP = 1L shl 33

    fun parseSegment(text: String): HlsWebVttSegment {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿").split('\n')
        var mpegTs: Long? = null
        var localUs = 0L
        val cues = mutableListOf<HlsSubtitleCue>()
        var index = 0
        // Header: "WEBVTT" and its lines up to the first blank line.
        while (index < lines.size && lines[index].isNotBlank()) {
            val line = lines[index].trim()
            if (line.startsWith("X-TIMESTAMP-MAP", ignoreCase = true)) {
                line.substringAfter('=').split(',').forEach { part ->
                    val key = part.substringBefore(':').trim().uppercase()
                    val value = part.substringAfter(':').trim()
                    when (key) {
                        "MPEGTS" -> mpegTs = value.toLongOrNull()
                        "LOCAL" -> localUs = parseTimestampUs(value) ?: 0L
                    }
                }
            }
            index++
        }
        while (index < lines.size) {
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break
            val block = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) block += lines[index++]
            val timingIndex = block.indexOfFirst { "-->" in it }
            if (timingIndex < 0 || block[0].startsWith("NOTE") || block[0].startsWith("STYLE") || block[0].startsWith("REGION")) {
                continue
            }
            val timing = block[timingIndex]
            val start = parseTimestampUs(timing.substringBefore("-->").trim()) ?: continue
            val end = parseTimestampUs(timing.substringAfter("-->").trim().substringBefore(' ').trim()) ?: continue
            val cueText = block.drop(timingIndex + 1).joinToString("\n") { cleanCueLine(it) }.trim()
            if (cueText.isNotEmpty() && end > start) cues += HlsSubtitleCue(start, end, cueText)
        }
        return HlsWebVttSegment(cues, mpegTs, localUs)
    }

    /**
     * Cues of [segment] on the output timeline. [mapMpegTsUs] converts an (unwrapped) MPEG-2
     * timestamp in microseconds to the output time; it is used only when the segment has
     * `X-TIMESTAMP-MAP`, otherwise the cue times are kept as playlist times.
     */
    fun mapCues(segment: HlsWebVttSegment, referencePts: Long?, mapMpegTsUs: (Long) -> Long): List<HlsSubtitleCue> {
        val mpegTs = segment.mpegTs ?: return segment.cues
        val unwrapped = unwrapPts(mpegTs, referencePts)
        val segmentOffsetUs = mapMpegTsUs(unwrapped * 100L / 9L) - segment.localUs
        return segment.cues.map { it.copy(startUs = it.startUs + segmentOffsetUs, endUs = it.endUs + segmentOffsetUs) }
    }

    /** Picks the 33-bit wrap of [pts] closest to [reference]. */
    fun unwrapPts(pts: Long, reference: Long?): Long {
        if (reference == null) return pts
        val base = reference - reference.mod(MPEG_TS_WRAP)
        return listOf(base - MPEG_TS_WRAP + pts, base + pts, base + MPEG_TS_WRAP + pts)
            .minBy { kotlin.math.abs(it - reference) }
    }

    /** Sorts cues, drops the copies repeated by consecutive segments and joins split ones. */
    fun mergeCues(cues: List<HlsSubtitleCue>): List<HlsSubtitleCue> {
        val merged = mutableListOf<HlsSubtitleCue>()
        for (cue in cues.filter { it.endUs > 0L }.sortedWith(compareBy({ it.startUs }, { it.endUs }))) {
            val previous = merged.lastOrNull()
            when {
                previous != null && previous.text == cue.text && cue.startUs <= previous.endUs + 1_000L ->
                    merged[merged.lastIndex] = previous.copy(endUs = maxOf(previous.endUs, cue.endUs))
                else -> merged += cue.copy(startUs = cue.startUs.coerceAtLeast(0L))
            }
        }
        return merged
    }

    fun toSrt(cues: List<HlsSubtitleCue>): String = buildString {
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(formatSrtTime(cue.startUs)).append(" --> ").append(formatSrtTime(cue.endUs)).append('\n')
            append(cue.text).append("\n\n")
        }
    }

    internal fun parseTimestampUs(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size !in 2..3) return null
        val secondsPart = parts.last()
        val seconds = secondsPart.substringBefore('.').toLongOrNull() ?: return null
        val millis = secondsPart.substringAfter('.', "0").padEnd(3, '0').take(3).toLongOrNull() ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        return ((hours * 3600L + minutes * 60L + seconds) * 1000L + millis) * 1000L
    }

    private fun formatSrtTime(us: Long): String {
        val totalMs = (us / 1000L).coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val minutes = totalMs / 60_000L % 60L
        val seconds = totalMs / 1000L % 60L
        val millis = totalMs % 1000L
        return "${hours.pad(2)}:${minutes.pad(2)}:${seconds.pad(2)},${millis.pad(3)}"
    }

    private fun Long.pad(length: Int): String = toString().padStart(length, '0')

    /** Keeps the <i>, <b> and <u> tags SRT understands and drops the WebVTT-only ones. */
    private fun cleanCueLine(line: String): String =
        line.replace(Regex("<(/?)([ibu])(\\.[^>]*)?>"), "<$1$2>")
            .replace(Regex("<(?!/?[ibu]>)[^>]*>"), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&lrm;", "‎")
            .replace("&rlm;", "‏")
            .replace("&amp;", "&")
}
