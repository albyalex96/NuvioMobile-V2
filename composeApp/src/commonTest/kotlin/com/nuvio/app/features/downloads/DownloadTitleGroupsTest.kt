package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadTitleGroupsTest {

    private fun item(
        id: String,
        parent: String,
        status: DownloadStatus,
        createdAt: Long,
        updatedAt: Long = createdAt,
        season: Int? = null,
        episode: Int? = null,
    ) = DownloadItem(
        id = id,
        contentType = if (episode != null) "series" else "movie",
        parentMetaId = parent,
        parentMetaType = if (episode != null) "series" else "movie",
        videoId = "$parent:$season:$episode",
        title = "Title $parent",
        seasonNumber = season,
        episodeNumber = episode,
        streamTitle = "Stream",
        providerName = "Addon",
        sourceUrl = "https://cdn.example/$id.mkv",
        fileName = "$id.mkv",
        status = status,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun progressUpdatesDoNotMoveTheCards() {
        val first = item("a", "show-a", DownloadStatus.Downloading, createdAt = 100, updatedAt = 900)
        val second = item("b", "movie-b", DownloadStatus.Downloading, createdAt = 200, updatedAt = 300)

        val before = groupDownloadsByTitle(listOf(first, second)).map { it.key }
        // The older download reports progress later: the order must stay the same.
        val after = groupDownloadsByTitle(listOf(first.copy(updatedAtEpochMs = 100), second.copy(updatedAtEpochMs = 5_000))).map { it.key }

        assertEquals(listOf("show-a", "movie-b"), before)
        assertEquals(before, after)
    }

    @Test
    fun episodesAreGroupedUnderTheirSeriesInEpisodeOrder() {
        val items = listOf(
            item("e3", "show", DownloadStatus.Completed, createdAt = 30, season = 1, episode = 3),
            item("e1", "show", DownloadStatus.Downloading, createdAt = 10, season = 1, episode = 1),
            item("e2", "show", DownloadStatus.Completed, createdAt = 20, season = 1, episode = 2),
        )

        val groups = groupDownloadsByTitle(items)

        assertEquals(1, groups.size)
        assertEquals(true, groups.single().isSeries)
        assertEquals(listOf("e1", "e2", "e3"), groups.single().items.map { it.id })
        assertEquals(listOf("e1"), groups.single().unfinishedItems.map { it.id })
    }

    @Test
    fun titlesInProgressComeBeforeFinishedOnesLastFinishedFirst() {
        val items = listOf(
            item("old", "finished-old", DownloadStatus.Completed, createdAt = 1, updatedAt = 10),
            item("new", "finished-new", DownloadStatus.Completed, createdAt = 2, updatedAt = 20),
            item("run", "running", DownloadStatus.Paused, createdAt = 3),
        )

        assertEquals(listOf("running", "finished-new", "finished-old"), groupDownloadsByTitle(items).map { it.key })
    }
}
