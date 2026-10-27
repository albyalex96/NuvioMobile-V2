package com.nuvio.app.features.downloads

import com.nuvio.app.features.downloads.season.SeasonDownloadBatch
import com.nuvio.app.features.downloads.season.SeasonEpisodeState

/** What a title card is doing now, from its downloads and its season downloads. */
internal sealed interface DownloadTitleAction {
    /** An HLS episode is being merged into its final file. */
    data class Converting(val item: DownloadItem) : DownloadTitleAction
    data class Downloading(val item: DownloadItem, val others: Int) : DownloadTitleAction
    /** Looking up the stream of an episode of a season download. */
    data class Searching(val seasonNumber: Int, val episodeNumber: Int) : DownloadTitleAction
    /** A source refused too many requests: the season waits until [untilEpochMs]. */
    data class RateLimited(val untilEpochMs: Long) : DownloadTitleAction
    /** Episodes waiting for a free download slot. */
    data object Queued : DownloadTitleAction
    data object Paused : DownloadTitleAction
    data object Failed : DownloadTitleAction
    data object Idle : DownloadTitleAction
}

internal data class DownloadTitleStatus(
    /** Episodes (or files) of the title, counting those of the season downloads not started yet. */
    val total: Int,
    val completed: Int,
    /** 0..1 over [total]: finished files count 1, running ones their progress, waiting ones 0. */
    val progress: Float,
    val downloading: Int,
    val converting: Int,
    val paused: Int,
    val failed: Int,
    val queued: Int,
    val searching: Int,
    val withoutStream: Int,
    val action: DownloadTitleAction,
) {
    /** Something runs or waits to run: the title can be paused. */
    val canPause: Boolean get() = downloading + converting + queued + searching > 0

    /** Something is paused (and nothing runs): the title can be resumed. */
    val canResume: Boolean get() = !canPause && paused > 0
}

/**
 * Sums up the downloads of [group] and the season downloads of the same title ([batches]), with
 * the action shown on the card: merging, downloading, looking up a stream, waiting for a rate
 * limit, in queue, paused, failed (in this order of priority).
 */
internal fun downloadTitleStatus(
    group: DownloadTitleGroup,
    batches: List<SeasonDownloadBatch>,
    cooldowns: Map<String, Long>,
    nowEpochMs: Long,
): DownloadTitleStatus {
    val items = group.items
    val itemIds = items.mapTo(HashSet()) { it.id }
    val titleBatches = batches.filter { it.parentMetaId == group.key }
    // Episodes of the season downloads that have no download yet (waiting, looking up, no stream).
    val waitingEpisodes = titleBatches.flatMap { batch ->
        batch.episodes.filter { episode ->
            episode.state != SeasonEpisodeState.Skipped &&
                episode.state != SeasonEpisodeState.Completed &&
                (episode.downloadId == null || episode.downloadId !in itemIds)
        }.map { batch to it }
    }
    val queued = waitingEpisodes.count { (batch, episode) -> episode.state == SeasonEpisodeState.Pending && !batch.paused }
    val pausedEpisodes = waitingEpisodes.count { (batch, episode) -> episode.state == SeasonEpisodeState.Pending && batch.paused }
    val searchingEpisodes = waitingEpisodes.filter { (_, episode) -> episode.state == SeasonEpisodeState.Resolving }
    val withoutStream = waitingEpisodes.count { (_, episode) -> episode.state == SeasonEpisodeState.NeedsManual }
    val failedEpisodes = waitingEpisodes.count { (_, episode) -> episode.state == SeasonEpisodeState.Failed }

    val converting = items.filter { it.isConverting }
    val downloading = items.filter { it.status == DownloadStatus.Downloading && !it.isConverting }
    val pausedItems = items.count { it.status == DownloadStatus.Paused }
    val failedItems = items.count { it.status == DownloadStatus.Failed }
    val completed = items.count { it.status == DownloadStatus.Completed }

    val total = items.size + waitingEpisodes.size
    val progress = if (total == 0) 0f else {
        items.sumOf { item ->
            when (item.status) {
                DownloadStatus.Completed -> 1.0
                else -> item.progressFraction.toDouble()
            }
        }.div(total).toFloat().coerceIn(0f, 1f)
    }
    val rateLimitedUntil = titleBatches
        .filter { !it.paused && it.pendingCount > 0 }
        .mapNotNull { cooldowns[it.id]?.takeIf { until -> until > nowEpochMs } }
        .maxOrNull()

    val action = when {
        converting.isNotEmpty() -> DownloadTitleAction.Converting(converting.first())
        downloading.isNotEmpty() -> DownloadTitleAction.Downloading(downloading.first(), others = downloading.size - 1)
        searchingEpisodes.isNotEmpty() -> searchingEpisodes.first().let { (batch, episode) ->
            DownloadTitleAction.Searching(batch.seasonNumber, episode.episodeNumber)
        }
        rateLimitedUntil != null -> DownloadTitleAction.RateLimited(rateLimitedUntil)
        queued > 0 -> DownloadTitleAction.Queued
        pausedItems + pausedEpisodes > 0 -> DownloadTitleAction.Paused
        failedItems + failedEpisodes > 0 -> DownloadTitleAction.Failed
        else -> DownloadTitleAction.Idle
    }
    return DownloadTitleStatus(
        total = total,
        completed = completed,
        progress = progress,
        downloading = downloading.size,
        converting = converting.size,
        paused = pausedItems + pausedEpisodes,
        failed = failedItems + failedEpisodes,
        queued = queued,
        searching = searchingEpisodes.size,
        withoutStream = withoutStream,
        action = action,
    )
}
