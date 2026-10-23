package com.nuvio.app.features.mal

import com.nuvio.app.features.tracking.TrackingListStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MalListStatus(
    val wireValue: String,
    val trackingStatus: TrackingListStatus,
) {
    WATCHING("watching", TrackingListStatus.WATCHING),
    PLAN_TO_WATCH("plan_to_watch", TrackingListStatus.PLAN_TO_WATCH),
    ON_HOLD("on_hold", TrackingListStatus.ON_HOLD),
    COMPLETED("completed", TrackingListStatus.COMPLETED),
    DROPPED("dropped", TrackingListStatus.DROPPED);

    companion object {
        fun fromWire(value: String?): MalListStatus? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }

        fun fromTracking(status: TrackingListStatus): MalListStatus =
            entries.first { it.trackingStatus == status }
    }
}

/** One anime of the user's MAL list, as cached and projected into the library. */
@Serializable
data class MalListEntry(
    @SerialName("id") val malId: Long,
    @SerialName("title") val title: String,
    @SerialName("title_en") val englishTitle: String? = null,
    @SerialName("poster") val posterUrl: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int = 0,
    @SerialName("year") val startYear: Int? = null,
    @SerialName("mean") val mean: Double? = null,
    @SerialName("status") val statusWire: String? = null,
    @SerialName("watched") val watchedEpisodes: Int = 0,
    @SerialName("score") val score: Int = 0,
    @SerialName("rewatching") val isRewatching: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val status: MalListStatus?
        get() = MalListStatus.fromWire(statusWire)

    val isMovie: Boolean
        get() = mediaType.equals("movie", ignoreCase = true)
}

@Serializable
internal data class MalListSnapshot(
    @SerialName("entries") val entries: List<MalListEntry> = emptyList(),
    @SerialName("fetched_at") val fetchedAtEpochMs: Long = 0L,
)

/** The list status of one anime, as MAL returns it and as the update rules read it. */
@Serializable
data class MalListStatusState(
    @SerialName("status") val statusWire: String? = null,
    @SerialName("score") val score: Int = 0,
    @SerialName("num_episodes_watched") val watchedEpisodes: Int = 0,
    @SerialName("is_rewatching") val isRewatching: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("finish_date") val finishDate: String? = null,
) {
    val status: MalListStatus?
        get() = MalListStatus.fromWire(statusWire)
}

@Serializable
internal data class MalPicture(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
internal data class MalStartSeason(
    val year: Int? = null,
)

@Serializable
internal data class MalAlternativeTitles(
    val en: String? = null,
)

@Serializable
internal data class MalAnimeNode(
    val id: Long,
    val title: String = "",
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("num_episodes") val numEpisodes: Int = 0,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("start_season") val startSeason: MalStartSeason? = null,
    val mean: Double? = null,
    @SerialName("alternative_titles") val alternativeTitles: MalAlternativeTitles? = null,
    @SerialName("my_list_status") val myListStatus: MalListStatusState? = null,
)

@Serializable
internal data class MalAnimeListItem(
    val node: MalAnimeNode,
    @SerialName("list_status") val listStatus: MalListStatusState? = null,
)

@Serializable
internal data class MalPaging(
    val next: String? = null,
)

@Serializable
internal data class MalAnimeListPage(
    val data: List<MalAnimeListItem> = emptyList(),
    val paging: MalPaging? = null,
)

internal fun MalAnimeNode.toListEntry(status: MalListStatusState?): MalListEntry =
    MalListEntry(
        malId = id,
        title = title.ifBlank { "MAL $id" },
        englishTitle = alternativeTitles?.en?.takeIf(String::isNotBlank),
        posterUrl = mainPicture?.large ?: mainPicture?.medium,
        mediaType = mediaType,
        numEpisodes = numEpisodes,
        startYear = startSeason?.year,
        mean = mean,
        statusWire = status?.statusWire,
        watchedEpisodes = status?.watchedEpisodes ?: 0,
        score = status?.score ?: 0,
        isRewatching = status?.isRewatching ?: false,
        updatedAt = status?.updatedAt,
    )

/** Fields requested for every anime node, so list and single-anime calls fill the same entry. */
internal const val MAL_ANIME_NODE_FIELDS =
    "num_episodes,media_type,start_season,mean,alternative_titles,main_picture"
