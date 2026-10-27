package com.nuvio.app.features.downloads

/** The downloads of one title (a movie, or the episodes of a series) shown as one card. */
internal data class DownloadTitleGroup(
    val key: String,
    val title: String,
    val isSeries: Boolean,
    val items: List<DownloadItem>,
) {
    val unfinishedItems: List<DownloadItem> get() = items.filter { it.status != DownloadStatus.Completed }
    val sizeBytes: Long get() = items.sumOf { item -> item.totalBytes?.takeIf { it > 0L } ?: item.downloadedBytes }
}

/**
 * Groups the downloads by title with an order that does not move while they progress: titles with
 * downloads in progress first, oldest started first, then the finished titles, last finished
 * first. Episodes are listed by season and episode.
 *
 * The progress of a download changes its update time every second: ordering by it made two
 * parallel downloads swap places continuously.
 */
internal fun groupDownloadsByTitle(items: List<DownloadItem>): List<DownloadTitleGroup> {
    val groups = items
        .groupBy { it.parentMetaId.trim().ifEmpty { it.id } }
        .map { (key, groupItems) ->
            val isSeries = groupItems.any(DownloadItem::isEpisode)
            DownloadTitleGroup(
                key = key,
                title = groupItems.first().title,
                isSeries = isSeries,
                items = if (isSeries) groupItems.sortedForSeriesDownloads() else groupItems.sortedBy { it.createdAtEpochMs },
            )
        }
    val (inProgress, finished) = groups.partition { it.unfinishedItems.isNotEmpty() }
    return inProgress.sortedWith(
        compareBy<DownloadTitleGroup> { group -> group.unfinishedItems.minOf { it.createdAtEpochMs } }.thenBy { it.key },
    ) + finished.sortedWith(
        compareByDescending<DownloadTitleGroup> { group -> group.items.maxOf { it.updatedAtEpochMs } }.thenBy { it.key },
    )
}
