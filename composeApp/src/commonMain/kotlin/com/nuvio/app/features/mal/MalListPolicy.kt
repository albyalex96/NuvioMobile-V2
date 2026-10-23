package com.nuvio.app.features.mal

import io.ktor.http.encodeURLParameter

/** Fields of a `PATCH /anime/{id}/my_list_status`; null fields are left untouched on MAL. */
data class MalListUpdate(
    val status: MalListStatus? = null,
    val watchedEpisodes: Int? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val isRewatching: Boolean? = null,
) {
    fun toForm(): String = listOfNotNull(
        status?.let { "status" to it.wireValue },
        watchedEpisodes?.let { "num_watched_episodes" to it.toString() },
        startDate?.let { "start_date" to it },
        finishDate?.let { "finish_date" to it },
        isRewatching?.let { "is_rewatching" to it.toString() },
    ).joinToString("&") { (key, value) -> "$key=${value.encodeURLParameter()}" }

    /** The list state MAL holds after this update is applied to [current]. */
    fun applyTo(current: MalListStatusState?): MalListStatusState {
        val base = current ?: MalListStatusState()
        return base.copy(
            statusWire = status?.wireValue ?: base.statusWire,
            watchedEpisodes = watchedEpisodes ?: base.watchedEpisodes,
            startDate = startDate ?: base.startDate,
            finishDate = finishDate ?: base.finishDate,
            isRewatching = isRewatching ?: base.isRewatching,
        )
    }
}

/**
 * Episode [episode] of an anime with [totalEpisodes] episodes (0 = unknown, still airing) was
 * watched. The counter only moves forward; the status moves to watching (from plan to watch,
 * on hold or dropped) and to completed on the last episode, filling the start and finish dates
 * MAL does not have yet. A completed anime is left alone unless it is being rewatched.
 */
fun planMalEpisodeWatched(
    current: MalListStatusState?,
    episode: Int,
    totalEpisodes: Int,
    today: String,
): MalListUpdate? {
    if (episode < 1) return null
    val status = current?.status
    val rewatching = current?.isRewatching == true
    if (status == MalListStatus.COMPLETED && !rewatching) return null

    val watched = current?.watchedEpisodes ?: 0
    val target = if (totalEpisodes > 0) minOf(maxOf(watched, episode), totalEpisodes) else maxOf(watched, episode)
    val finished = totalEpisodes > 0 && target >= totalEpisodes

    if (rewatching) {
        if (target == watched && !finished) return null
        return MalListUpdate(
            status = if (finished) MalListStatus.COMPLETED else null,
            watchedEpisodes = target.takeIf { it != watched },
            isRewatching = if (finished) false else null,
        )
    }

    val newStatus = if (finished) MalListStatus.COMPLETED else MalListStatus.WATCHING
    if (target == watched && status == newStatus) return null
    return MalListUpdate(
        status = newStatus.takeIf { it != status },
        watchedEpisodes = target.takeIf { it != watched },
        startDate = today.takeIf { current?.startDate.isNullOrBlank() },
        finishDate = today.takeIf { finished && current?.finishDate.isNullOrBlank() },
    )
}

/**
 * Episode [episode] was marked as not watched: MAL only keeps a counter, so it drops to the
 * episode before it (never moving forward). A completed anime goes back to watching.
 */
fun planMalEpisodeUnwatched(
    current: MalListStatusState?,
    episode: Int,
): MalListUpdate? {
    if (current?.status == null || episode < 1) return null
    val watched = current.watchedEpisodes
    val target = minOf(watched, episode - 1)
    if (target == watched) return null
    return MalListUpdate(
        status = MalListStatus.WATCHING.takeIf { current.status == MalListStatus.COMPLETED },
        watchedEpisodes = target,
    )
}

/** Moves the anime to [destination]; completing it also fills the episode count and the dates. */
fun planMalStatusChange(
    current: MalListStatusState?,
    destination: MalListStatus,
    totalEpisodes: Int,
    today: String,
): MalListUpdate? {
    val completing = destination == MalListStatus.COMPLETED
    val watched = current?.watchedEpisodes ?: 0
    val episodes = if (completing && totalEpisodes > 0 && watched < totalEpisodes) totalEpisodes else null
    if (current?.status == destination && episodes == null) return null
    val starting = destination == MalListStatus.WATCHING || completing
    return MalListUpdate(
        status = destination,
        watchedEpisodes = episodes,
        startDate = today.takeIf { starting && current?.startDate.isNullOrBlank() },
        finishDate = today.takeIf { completing && current?.finishDate.isNullOrBlank() },
    )
}
