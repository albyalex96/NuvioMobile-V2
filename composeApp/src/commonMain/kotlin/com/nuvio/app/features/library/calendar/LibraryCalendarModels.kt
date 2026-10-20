package com.nuvio.app.features.library.calendar

import com.nuvio.app.core.time.parseEpisodeReleaseLocalDate
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.library.LibraryItem
import kotlinx.serialization.Serializable

/** One release shown in the library calendar: an episode of a saved series or a saved movie. */
@Serializable
data class LibraryCalendarEvent(
    val contentId: String,
    val contentType: String,
    val title: String,
    val dateIso: String,
    val poster: String? = null,
    val background: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
) {
    val isEpisode: Boolean
        get() = season != null || episode != null
}

data class LibraryCalendarUiState(
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val events: List<LibraryCalendarEvent> = emptyList(),
    val lastUpdatedEpochMs: Long? = null,
    val failedCount: Int = 0,
    val trackedCount: Int = 0,
)

enum class LibraryCalendarViewMode { List, Month }

@Serializable
internal data class LibraryCalendarCachePayload(
    val version: Int = LibraryCalendarCacheVersion,
    val lastUpdatedEpochMs: Long = 0L,
    val entries: List<LibraryCalendarCacheEntry> = emptyList(),
)

@Serializable
internal data class LibraryCalendarCacheEntry(
    val key: String,
    val fetchedAtEpochMs: Long,
    val events: List<LibraryCalendarEvent> = emptyList(),
)

internal data class LibraryCalendarTarget(
    val key: String,
    val type: String,
    val id: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val releaseInfo: String?,
)

internal const val LibraryCalendarCacheVersion = 1

/** Past releases older than this are not kept: the calendar is about what is coming or just came out. */
internal const val LibraryCalendarPastWindowDays = 90

internal fun libraryCalendarType(type: String): String? = when (type.trim().lowercase()) {
    "tv", "show", "series", "tvshow" -> "series"
    "movie", "film" -> "movie"
    else -> null
}

internal fun LibraryItem.toLibraryCalendarTarget(): LibraryCalendarTarget? {
    val calendarType = libraryCalendarType(type) ?: return null
    val trimmedId = id.trim().takeIf { it.isNotEmpty() } ?: return null
    return LibraryCalendarTarget(
        key = "$calendarType:$trimmedId",
        type = type,
        id = trimmedId,
        name = name,
        poster = poster,
        background = banner ?: landscapePoster,
        releaseInfo = releaseInfo,
    )
}

/**
 * Builds the calendar events of one library item. Episodes come from the series video list,
 * movies use the meta release date (falling back to a full date in releaseInfo; a year alone
 * cannot be placed on a calendar). Events before [minDateIso] are dropped.
 */
internal fun buildLibraryCalendarEvents(
    target: LibraryCalendarTarget,
    meta: MetaDetails,
    minDateIso: String,
): List<LibraryCalendarEvent> {
    val title = meta.name.ifBlank { target.name }.ifBlank { target.id }
    val poster = meta.poster ?: target.poster
    val background = meta.background ?: target.background
    if (libraryCalendarType(target.type) == "movie") {
        val date = parseEpisodeReleaseLocalDate(meta.released)
            ?: parseEpisodeReleaseLocalDate(meta.releaseInfo)
            ?: parseEpisodeReleaseLocalDate(target.releaseInfo)
            ?: return emptyList()
        if (date < minDateIso) return emptyList()
        return listOf(
            LibraryCalendarEvent(
                contentId = target.id,
                contentType = target.type,
                title = title,
                dateIso = date,
                poster = poster,
                background = background,
            ),
        )
    }
    return meta.videos.mapNotNull { video ->
        if (video.season == null && video.episode == null) return@mapNotNull null
        val date = parseEpisodeReleaseLocalDate(video.released) ?: return@mapNotNull null
        if (date < minDateIso) return@mapNotNull null
        LibraryCalendarEvent(
            contentId = target.id,
            contentType = target.type,
            title = title,
            dateIso = date,
            poster = poster,
            background = video.thumbnail ?: background,
            season = video.season,
            episode = video.episode,
            episodeTitle = video.title.trim().takeIf { it.isNotEmpty() },
        )
    }.distinctBy { Triple(it.season, it.episode, it.dateIso) }
}

internal val libraryCalendarEventOrder: Comparator<LibraryCalendarEvent> =
    compareBy<LibraryCalendarEvent>({ it.dateIso }, { it.title.lowercase() }, { it.season ?: 0 }, { it.episode ?: 0 })

// Calendar arithmetic on ISO dates (yyyy-MM-dd), without a date-time library.

internal data class CalendarYearMonth(val year: Int, val month: Int) {
    fun plusMonths(delta: Int): CalendarYearMonth {
        val index = year * 12 + (month - 1) + delta
        return CalendarYearMonth(year = index.floorDiv(12), month = index.mod(12) + 1)
    }

    val firstDayIso: String
        get() = isoDateOf(year, month, 1)

    val firstEpochDay: Long
        get() = epochDayOf(year, month, 1)

    val lengthInDays: Int
        get() = (plusMonths(1).firstEpochDay - firstEpochDay).toInt()

    companion object {
        fun ofIsoDate(isoDate: String): CalendarYearMonth? {
            val parts = parseIsoParts(isoDate) ?: return null
            return CalendarYearMonth(parts.first, parts.second)
        }
    }
}

internal fun isoDateToEpochDay(isoDate: String): Long? {
    val (year, month, day) = parseIsoParts(isoDate) ?: return null
    return epochDayOf(year, month, day)
}

internal fun epochDayToIsoDate(epochDay: Long): String {
    // Howard Hinnant's civil_from_days.
    val z = epochDay + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    return isoDateOf(year, month, day)
}

/** ISO day of week: 1 = Monday … 7 = Sunday. */
internal fun isoDayOfWeek(epochDay: Long): Int = (epochDay + 3).mod(7L).toInt() + 1

internal fun plusDaysIso(isoDate: String, days: Int): String? =
    isoDateToEpochDay(isoDate)?.let { epochDayToIsoDate(it + days) }

private fun epochDayOf(year: Int, month: Int, day: Int): Long {
    // Howard Hinnant's days_from_civil.
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = y.floorDiv(400L)
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

private fun isoDateOf(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

private fun parseIsoParts(isoDate: String): Triple<Int, Int, Int>? {
    val parts = isoDate.trim().take(10).split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    return Triple(year, month, day)
}
