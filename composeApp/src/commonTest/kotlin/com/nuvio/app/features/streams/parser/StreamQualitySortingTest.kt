package com.nuvio.app.features.streams.parser

import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamQualitySortingTest {
    @Test
    fun `merges addon and plugin groups sorted by resolution`() {
        val groups = listOf(
            AddonStreamGroup(
                addonName = "Addon",
                addonId = "addon",
                streams = listOf(stream("a-720", "720p WEB-DL"), stream("a-4k", "2160p BluRay")),
            ),
            AddonStreamGroup(
                addonName = "Plugin",
                addonId = "plugin:p",
                streams = listOf(stream("p-1080", "1080p"), stream("p-none", "no info"), stream("p-4k", "4K HDR")),
                isLoading = true,
            ),
        )

        val merged = StreamQualitySorting.mergeGroups(groups, knownTitle = null)

        assertEquals(1, merged.size)
        assertTrue(merged.single().isQualitySortedGroup)
        assertTrue(merged.single().isLoading)
        // Same resolution keeps the original order (stable sort); unknown resolution defaults to SD.
        assertEquals(listOf("a-4k", "p-4k", "p-1080", "a-720", "p-none"), merged.single().streams.map { it.name })
    }

    @Test
    fun `empty groups stay empty`() {
        assertEquals(emptyList(), StreamQualitySorting.mergeGroups(emptyList(), knownTitle = null))
    }

    @Test
    fun `hidden qualities are removed and emptied groups dropped unless loading or failed`() {
        val hidden = setOf(StreamParserResolution.HD, StreamParserResolution.SD)
        val groups = listOf(
            AddonStreamGroup(
                addonName = "Mixed",
                addonId = "mixed",
                streams = listOf(stream("m-720", "720p WEB-DL"), stream("m-4k", "2160p BluRay"), stream("m-none", "no info")),
            ),
            AddonStreamGroup(addonName = "Only SD", addonId = "sd", streams = listOf(stream("s-480", "480p"))),
            AddonStreamGroup(addonName = "Loading", addonId = "loading", streams = listOf(stream("l-720", "720p")), isLoading = true),
            AddonStreamGroup(addonName = "Failed", addonId = "failed", streams = emptyList(), error = "boom"),
        )

        val filtered = StreamQualitySorting.filterGroups(groups, knownTitle = null, hidden = hidden)

        assertEquals(listOf("mixed", "loading", "failed"), filtered.map { it.addonId })
        assertEquals(listOf("m-4k"), filtered.first().streams.map { it.name })
        assertTrue(filtered[1].streams.isEmpty())
    }

    @Test
    fun `no hidden qualities keeps the original list`() {
        val streams = listOf(stream("a", "720p"), stream("b", "no info"))
        assertSame(streams, StreamQualitySorting.filterStreams(streams, knownTitle = null, hidden = emptySet()))
    }

    private fun stream(name: String, description: String): StreamItem =
        StreamItem(name = name, description = description, url = "https://x.example/$name", addonName = "A", addonId = "a")
}
