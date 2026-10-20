package com.nuvio.app.features.player

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivatePlaybackRequestsTest {
    private fun launch(parentMetaId: String, videoId: String?) = PlayerLaunch(
        profileId = 1,
        title = "Show",
        sourceUrl = "https://example.com/video.mkv",
        streamTitle = "Stream",
        providerName = "Addon",
        videoId = videoId,
        parentMetaId = parentMetaId,
        parentMetaType = "series",
    )

    @AfterTest
    fun reset() = PrivatePlaybackRequests.clear()

    @Test
    fun requestAppliesOnlyToTheSameEpisodeAndOnlyOnce() {
        PrivatePlaybackRequests.request(parentMetaId = "tt1", videoId = "tt1:1:2")
        assertFalse(PrivatePlaybackRequests.applyTo(launch("tt1", "tt1:1:3")).privateViewing)
        assertTrue(PrivatePlaybackRequests.applyTo(launch("tt1", "tt1:1:2")).privateViewing)
        assertFalse(PrivatePlaybackRequests.applyTo(launch("tt1", "tt1:1:2")).privateViewing)
    }

    @Test
    fun moviesMatchWithOrWithoutAnExplicitVideoId() {
        PrivatePlaybackRequests.request(parentMetaId = "tt9", videoId = "tt9")
        assertTrue(PrivatePlaybackRequests.applyTo(launch("tt9", null)).privateViewing)
    }

    @Test
    fun clearedRequestIsNotApplied() {
        PrivatePlaybackRequests.request(parentMetaId = "tt1", videoId = "tt1:1:2")
        PrivatePlaybackRequests.clear()
        assertFalse(PrivatePlaybackRequests.applyTo(launch("tt1", "tt1:1:2")).privateViewing)
    }
}
