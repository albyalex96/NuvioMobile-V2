package com.nuvio.app.features.cast

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.parser.GenericStreamParser
import com.nuvio.app.features.streams.parser.StreamParserTag

/**
 * Features of a stream that many Chromecasts cannot play with the Default Media Receiver:
 * HEVC/AV1/Dolby Vision video need recent models, DTS and TrueHD audio are never decoded.
 * Returned as short labels for the warnings, in a stable order and without duplicates.
 */
internal fun castCompatibilityIssues(tags: List<StreamParserTag>): List<String> {
    val labels = tags.map { it.label }
    return buildList {
        if ("HEVC" in labels) add("HEVC")
        if ("AV1" in labels) add("AV1")
        if ("Dolby Vision" in labels) add("Dolby Vision")
        if (labels.any { it.startsWith("DTS") }) add("DTS")
        if ("TrueHD" in labels) add("TrueHD")
    }
}

/** Compatibility issues read from the stream description with the Stream Parser. */
internal fun StreamItem.castCompatibilityIssues(knownTitle: String? = null): List<String> =
    runCatching { castCompatibilityIssues(GenericStreamParser.parse(this, knownTitle).tags) }
        .getOrDefault(emptyList())
