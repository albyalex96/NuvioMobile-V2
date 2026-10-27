package com.nuvio.app.features.downloads.season

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamDebridCacheState
import com.nuvio.app.features.streams.StreamDebridCacheStatus
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeasonStreamMatcherTest {

    private fun stream(
        name: String,
        addon: String = "Torrentio",
        filename: String? = null,
        bingeGroup: String? = null,
        url: String? = "https://cdn.example/${name.replace(' ', '_')}.mkv",
        cache: StreamDebridCacheState? = null,
    ) = StreamItem(
        name = name,
        url = url,
        addonName = addon,
        addonId = "addon:$addon",
        behaviorHints = StreamBehaviorHints(bingeGroup = bingeGroup, filename = filename),
        debridCacheStatus = cache?.let { StreamDebridCacheStatus(providerId = "rd", providerName = "RD", state = it) },
    )

    private val picked = stream(
        name = "Show 1080p WEB-DL x265",
        filename = "Show.S01E01.1080p.WEB-DL.x265.mkv",
        bingeGroup = "torrentio|1080p|WEB-DL",
    )
    private val template = SeasonStreamMatcher.templateOf(picked, seasonNumber = 1, episodeNumber = 1)
    private val downloadable: (StreamItem) -> Boolean = { it.playableDirectUrl != null }

    @Test
    fun templateKeepsSourceBingeGroupAndQuality() {
        assertEquals("addon:Torrentio", template.addonId)
        assertEquals("torrentio|1080p|WEB-DL", template.bingeGroup)
        assertEquals("1080p", template.resolution)
        assertTrue("hevc" in template.qualityTokens && "web-dl" in template.qualityTokens)
    }

    @Test
    fun expectedFileNameWinsOverBingeGroupAndOrder() {
        val sameGroup = stream("Show 1080p other", filename = "Show.S01E02.1080p.WEBRip.mkv", bingeGroup = "torrentio|1080p|WEB-DL")
        val sameFile = stream("Show 1080p", filename = "Show.S01E02.1080p.WEB-DL.x265.mkv")

        val selected = SeasonStreamMatcher.select(template, 1, 2, listOf(sameGroup, sameFile), downloadable)

        assertEquals(sameFile, selected)
    }

    @Test
    fun bingeGroupWinsOverQualityTokens() {
        val sameGroup = stream("Show 720p", bingeGroup = "torrentio|1080p|WEB-DL")
        val sameQuality = stream("Show 1080p WEB-DL x265")

        assertEquals(sameGroup, SeasonStreamMatcher.select(template, 1, 2, listOf(sameQuality, sameGroup), downloadable))
    }

    @Test
    fun skipsStreamsOfAnotherEpisodeNotCachedOrNotDownloadable() {
        val otherEpisode = stream("Show 1080p", filename = "Show.S01E03.1080p.WEB-DL.x265.mkv")
        val notCached = stream("Show 1080p WEB-DL", cache = StreamDebridCacheState.NOT_CACHED)
        val torrentOnly = stream("Show 1080p WEB-DL x265", url = null)
        val good = stream("Show 720p")

        val ranked = SeasonStreamMatcher.rank(template, 1, 2, listOf(otherEpisode, notCached, torrentOnly, good), emptySet(), downloadable)

        assertEquals(listOf(good), ranked)
    }

    @Test
    fun fallsBackToOtherSourcesAfterTheChosenOne() {
        val otherSource1080 = stream("Other 1080p WEB-DL", addon = "Comet")
        val otherSource720 = stream("Other 720p", addon = "Comet")
        val chosenSource = stream("Show 720p")

        val ranked = SeasonStreamMatcher.rank(template, 1, 2, listOf(otherSource720, otherSource1080, chosenSource), emptySet(), downloadable)

        assertEquals(listOf(chosenSource, otherSource1080, otherSource720), ranked)
    }

    @Test
    fun excludesStreamsThatAlreadyFailed() {
        val failed = stream("Show 1080p WEB-DL x265", bingeGroup = "torrentio|1080p|WEB-DL")
        val other = stream("Show 720p")

        val ranked = SeasonStreamMatcher.rank(
            template, 1, 2, listOf(failed, other), setOf(SeasonStreamMatcher.keyOf(failed)), downloadable,
        )

        assertEquals(listOf(other), ranked)
    }

    @Test
    fun nothingDownloadableGivesNoStream() {
        assertNull(SeasonStreamMatcher.select(template, 1, 2, listOf(stream("Show", url = null)), downloadable))
    }
}
