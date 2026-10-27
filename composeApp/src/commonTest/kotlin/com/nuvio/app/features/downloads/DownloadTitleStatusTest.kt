package com.nuvio.app.features.downloads

import com.nuvio.app.features.downloads.season.SeasonDownloadBatch
import com.nuvio.app.features.downloads.season.SeasonDownloadEpisode
import com.nuvio.app.features.downloads.season.SeasonEpisodeState
import com.nuvio.app.features.downloads.season.SeasonStreamTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadTitleStatusTest {

    private fun item(
        id: String,
        episode: Int,
        status: DownloadStatus,
        downloaded: Long = 0L,
        total: Long? = null,
        conversion: Float? = null,
    ) = DownloadItem(
        id = id,
        contentType = "series",
        parentMetaId = "show",
        parentMetaType = "series",
        videoId = "show:1:$episode",
        title = "Show",
        seasonNumber = 1,
        episodeNumber = episode,
        streamTitle = "Stream",
        providerName = "Addon",
        sourceUrl = "https://cdn.example/$id.mkv",
        fileName = "$id.mkv",
        conversionProgress = conversion,
        status = status,
        downloadedBytes = downloaded,
        totalBytes = total,
        createdAtEpochMs = episode.toLong(),
        updatedAtEpochMs = episode.toLong(),
    )

    private fun batch(vararg episodes: SeasonDownloadEpisode, paused: Boolean = false) = SeasonDownloadBatch(
        id = "batch",
        contentType = "series",
        parentMetaId = "show",
        parentMetaType = "series",
        title = "Show",
        seasonNumber = 1,
        template = SeasonStreamTemplate(addonId = "addon", addonName = "Addon", seasonNumber = 1, episodeNumber = 1),
        episodes = episodes.toList(),
        paused = paused,
        createdAtEpochMs = 0L,
    )

    private fun episode(number: Int, state: SeasonEpisodeState, downloadId: String? = null) =
        SeasonDownloadEpisode(videoId = "show:1:$number", seasonNumber = 1, episodeNumber = number, state = state, downloadId = downloadId)

    private fun status(items: List<DownloadItem>, batches: List<SeasonDownloadBatch> = emptyList(), cooldowns: Map<String, Long> = emptyMap()) =
        downloadTitleStatus(groupDownloadsByTitle(items).single(), batches, cooldowns, nowEpochMs = 1_000L)

    @Test
    fun overallProgressCountsTheEpisodesStillInQueue() {
        val items = listOf(
            item("e1", 1, DownloadStatus.Completed),
            item("e2", 2, DownloadStatus.Downloading, downloaded = 50, total = 100),
        )
        val season = batch(
            episode(1, SeasonEpisodeState.Completed, "e1"),
            episode(2, SeasonEpisodeState.Downloading, "e2"),
            episode(3, SeasonEpisodeState.Pending),
            episode(4, SeasonEpisodeState.Resolving),
        )

        val status = status(items, listOf(season))

        assertEquals(4, status.total)
        assertEquals(1, status.completed)
        assertEquals(1.5f / 4f, status.progress)
        assertEquals(1, status.queued)
        assertEquals(1, status.searching)
        assertTrue(status.action is DownloadTitleAction.Downloading)
        assertTrue(status.canPause)
    }

    @Test
    fun mergingWinsOverDownloadingAndSearching() {
        val items = listOf(
            item("e1", 1, DownloadStatus.Downloading, conversion = 0.4f),
            item("e2", 2, DownloadStatus.Downloading, downloaded = 10, total = 100),
        )

        assertEquals(DownloadTitleAction.Converting(items[0]), status(items).action)
    }

    @Test
    fun rateLimitedSeasonShowsTheWait() {
        val items = listOf(item("e1", 1, DownloadStatus.Completed))
        val season = batch(episode(1, SeasonEpisodeState.Completed, "e1"), episode(2, SeasonEpisodeState.Pending))

        val status = status(items, listOf(season), cooldowns = mapOf("batch" to 61_000L))

        assertEquals(DownloadTitleAction.RateLimited(61_000L), status.action)
    }

    @Test
    fun pausedTitleCanBeResumed() {
        val items = listOf(item("e1", 1, DownloadStatus.Paused, downloaded = 10, total = 100))
        val season = batch(episode(1, SeasonEpisodeState.Downloading, "e1"), episode(2, SeasonEpisodeState.Pending), paused = true)

        val status = status(items, listOf(season))

        assertEquals(DownloadTitleAction.Paused, status.action)
        assertEquals(2, status.paused)
        assertTrue(status.canResume && !status.canPause)
    }
}
