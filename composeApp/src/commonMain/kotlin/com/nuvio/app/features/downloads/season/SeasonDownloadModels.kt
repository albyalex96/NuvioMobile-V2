package com.nuvio.app.features.downloads.season

import com.nuvio.app.features.downloads.hls.HlsDownloadPreferences
import kotlinx.serialization.Serializable

@Serializable
enum class SeasonEpisodeState {
    /** Waiting for a free download slot. */
    Pending,
    /** Looking up the stream of the episode. */
    Resolving,
    /** Handed to the downloads: [SeasonDownloadEpisode.downloadId] follows it. */
    Downloading,
    Completed,
    /** No stream like the chosen one: the user has to pick it from the streams screen. */
    NeedsManual,
    Failed,
    /** Removed by the user, or already downloaded when the season download started. */
    Skipped,
}

/**
 * The stream the user picked for the first episode, reduced to what identifies the "same" stream
 * for the other episodes: source, binge group, file name and quality.
 */
@Serializable
data class SeasonStreamTemplate(
    val addonId: String,
    val addonName: String,
    val bingeGroup: String? = null,
    val filename: String? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val resolution: String? = null,
    val qualityTokens: List<String> = emptyList(),
)

@Serializable
data class SeasonDownloadEpisode(
    val videoId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val thumbnail: String? = null,
    val state: SeasonEpisodeState = SeasonEpisodeState.Pending,
    val downloadId: String? = null,
    /** Failed lookups or downloads of this episode, retried until the limit of the coordinator. */
    val attempts: Int = 0,
    val message: String? = null,
    /** Stream being downloaded (see [SeasonStreamMatcher.keyOf]). */
    val sourceKey: String? = null,
    /** Streams that failed for this episode: the next lookup skips them. */
    val failedSources: List<String> = emptyList(),
    /** Name of the other source used when the chosen one had no stream for this episode. */
    val fallbackSource: String? = null,
)

@Serializable
data class SeasonDownloadBatch(
    val id: String,
    val contentType: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int,
    val template: SeasonStreamTemplate,
    /** Set when the chosen stream is HLS: quality and languages applied to every episode. */
    val hls: HlsDownloadPreferences? = null,
    val episodes: List<SeasonDownloadEpisode>,
    val paused: Boolean = false,
    val createdAtEpochMs: Long,
) {
    val completedCount: Int get() = episodes.count { it.state == SeasonEpisodeState.Completed }
    val activeCount: Int get() = episodes.count { it.state == SeasonEpisodeState.Downloading || it.state == SeasonEpisodeState.Resolving }
    val pendingCount: Int get() = episodes.count { it.state == SeasonEpisodeState.Pending }
    val needsManualCount: Int get() = episodes.count { it.state == SeasonEpisodeState.NeedsManual }
    val failedCount: Int get() = episodes.count { it.state == SeasonEpisodeState.Failed }
    val countedEpisodes: Int get() = episodes.count { it.state != SeasonEpisodeState.Skipped }

    /** Nothing left to start or follow: kept in the list until the user clears it. */
    val isFinished: Boolean get() = episodes.none {
        it.state == SeasonEpisodeState.Pending || it.state == SeasonEpisodeState.Resolving ||
            it.state == SeasonEpisodeState.Downloading
    }
}

/** Season download waiting for the user to pick the stream of its first episode. */
data class SeasonDownloadRequest(
    val contentType: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val title: String,
    val logo: String?,
    val poster: String?,
    val background: String?,
    val seasonNumber: Int,
    val episodes: List<SeasonDownloadEpisode>,
) {
    val firstEpisode: SeasonDownloadEpisode get() = episodes.first()

    fun matches(parentMetaId: String, seasonNumber: Int?, episodeNumber: Int?): Boolean =
        this.parentMetaId == parentMetaId &&
            this.seasonNumber == seasonNumber &&
            firstEpisode.episodeNumber == episodeNumber
}
