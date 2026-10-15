package com.nuvio.app.features.streams.parser

import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun stream(name: String, description: String): StreamItem =
        StreamItem(name = name, description = description, url = "https://x.example/$name", addonName = "A", addonId = "a")
}
