package com.nuvio.app.features.streams.parser

import kotlin.math.abs

/**
 * Estimates a plausible file size when the stream does not report one.
 *
 * The value is pseudo-random but deterministic: it is seeded with a stable key of the
 * stream, so the same stream always shows the same estimate across refreshes and scrolls.
 * The range comes from a typical bitrate for resolution and source, multiplied by the
 * duration (reported by the stream or a default for movies/episodes).
 */
object StreamParserSizeEstimator {
    private const val DEFAULT_MOVIE_SECONDS = 115L * 60L
    private const val DEFAULT_EPISODE_SECONDS = 45L * 60L

    private data class BitrateRange(val minMbps: Double, val maxMbps: Double)

    fun estimateBytes(info: StreamParserInfo, isEpisode: Boolean, seedKey: String): Long {
        val range = bitrateRange(info)
        val seconds = info.durationSeconds?.takeIf { it in 60..(6 * 3600) }
            ?: if (isEpisode) DEFAULT_EPISODE_SECONDS else DEFAULT_MOVIE_SECONDS
        val fraction = stableFraction(seedKey)
        val mbps = range.minMbps + (range.maxMbps - range.minMbps) * fraction
        return (mbps * 1_000_000.0 / 8.0 * seconds).toLong()
    }

    private fun bitrateRange(info: StreamParserInfo): BitrateRange {
        val sourceLabel = info.tags.firstOrNull {
            it.kind == StreamParserTagKind.SOURCE || it.kind == StreamParserTagKind.CAM
        }?.label
        if (sourceLabel == "CAM") return BitrateRange(1.0, 2.5)
        return when (info.resolution) {
            StreamParserResolution.UHD_8K -> BitrateRange(60.0, 100.0)
            StreamParserResolution.UHD_4K -> when (sourceLabel) {
                "REMUX" -> BitrateRange(45.0, 80.0)
                "BluRay" -> BitrateRange(15.0, 35.0)
                else -> BitrateRange(12.0, 25.0)
            }
            StreamParserResolution.QHD -> BitrateRange(8.0, 16.0)
            StreamParserResolution.FHD -> when (sourceLabel) {
                "REMUX" -> BitrateRange(20.0, 35.0)
                "BluRay" -> BitrateRange(6.0, 15.0)
                "HDTV" -> BitrateRange(2.0, 5.0)
                else -> BitrateRange(3.0, 8.0)
            }
            StreamParserResolution.HD -> when (sourceLabel) {
                "BluRay", "REMUX" -> BitrateRange(3.0, 6.0)
                else -> BitrateRange(1.5, 4.0)
            }
            StreamParserResolution.SD -> BitrateRange(0.8, 2.0)
        }
    }

    /** FNV-1a hash of [key] mapped to [0, 1). */
    private fun stableFraction(key: String): Double {
        var hash = 0x811C9DC5L
        for (char in key) {
            hash = hash xor char.code.toLong()
            hash = (hash * 0x01000193L) and 0xFFFFFFFFL
        }
        return abs(hash % 10_000L) / 10_000.0
    }
}
