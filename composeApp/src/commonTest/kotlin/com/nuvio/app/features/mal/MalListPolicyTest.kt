package com.nuvio.app.features.mal

import com.nuvio.app.features.anime.AnimeIdIndex
import com.nuvio.app.features.anime.AnimeIdMapping
import com.nuvio.app.features.simkl.SimklAnimeIdPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MalListPolicyTest {
    private val today = "2026-10-23"

    @Test
    fun firstEpisodeStartsWatchingWithStartDate() {
        val update = planMalEpisodeWatched(current = null, episode = 1, totalEpisodes = 12, today = today)
        assertEquals(MalListUpdate(MalListStatus.WATCHING, 1, startDate = today), update)
    }

    @Test
    fun counterNeverMovesBackAndPlanToWatchBecomesWatching() {
        val current = MalListStatusState(statusWire = "watching", watchedEpisodes = 5, startDate = "2026-01-01")
        assertNull(planMalEpisodeWatched(current, episode = 3, totalEpisodes = 12, today = today))
        assertEquals(
            MalListUpdate(watchedEpisodes = 6),
            planMalEpisodeWatched(current, episode = 6, totalEpisodes = 12, today = today),
        )
        val planned = MalListStatusState(statusWire = "plan_to_watch")
        assertEquals(
            MalListUpdate(MalListStatus.WATCHING, 2, startDate = today),
            planMalEpisodeWatched(planned, episode = 2, totalEpisodes = 0, today = today),
        )
    }

    @Test
    fun lastEpisodeCompletesWithFinishDate() {
        val current = MalListStatusState(statusWire = "watching", watchedEpisodes = 11, startDate = "2026-01-01")
        assertEquals(
            MalListUpdate(MalListStatus.COMPLETED, 12, finishDate = today),
            planMalEpisodeWatched(current, episode = 12, totalEpisodes = 12, today = today),
        )
        // Episodes beyond the known total are capped.
        assertEquals(
            MalListUpdate(MalListStatus.COMPLETED, 12, finishDate = today),
            planMalEpisodeWatched(current, episode = 14, totalEpisodes = 12, today = today),
        )
    }

    @Test
    fun completedAnimeIsLeftAloneUnlessRewatching() {
        val completed = MalListStatusState(statusWire = "completed", watchedEpisodes = 12)
        assertNull(planMalEpisodeWatched(completed, episode = 3, totalEpisodes = 12, today = today))
        val rewatching = MalListStatusState(statusWire = "completed", watchedEpisodes = 2, isRewatching = true)
        assertEquals(
            MalListUpdate(watchedEpisodes = 3),
            planMalEpisodeWatched(rewatching, episode = 3, totalEpisodes = 12, today = today),
        )
        assertEquals(
            MalListUpdate(MalListStatus.COMPLETED, 12, isRewatching = false),
            planMalEpisodeWatched(rewatching, episode = 12, totalEpisodes = 12, today = today),
        )
    }

    @Test
    fun unwatchingMovesTheCounterBack() {
        val completed = MalListStatusState(statusWire = "completed", watchedEpisodes = 12)
        assertEquals(
            MalListUpdate(MalListStatus.WATCHING, 9),
            planMalEpisodeUnwatched(completed, episode = 10),
        )
        assertNull(planMalEpisodeUnwatched(MalListStatusState(statusWire = "watching", watchedEpisodes = 4), episode = 6))
        assertNull(planMalEpisodeUnwatched(null, episode = 1))
    }

    @Test
    fun statusChangesFillEpisodesAndDates() {
        assertEquals(
            MalListUpdate(MalListStatus.COMPLETED, 24, startDate = today, finishDate = today),
            planMalStatusChange(null, MalListStatus.COMPLETED, totalEpisodes = 24, today = today),
        )
        assertEquals(
            MalListUpdate(MalListStatus.PLAN_TO_WATCH),
            planMalStatusChange(null, MalListStatus.PLAN_TO_WATCH, totalEpisodes = 24, today = today),
        )
        assertNull(
            planMalStatusChange(
                MalListStatusState(statusWire = "dropped"),
                MalListStatus.DROPPED,
                totalEpisodes = 24,
                today = today,
            ),
        )
    }

    @Test
    fun formEncodesOnlyTheChangedFields() {
        assertEquals(
            "status=watching&num_watched_episodes=3&start_date=2026-10-23",
            MalListUpdate(MalListStatus.WATCHING, 3, startDate = today).toForm(),
        )
    }

    @Test
    fun libraryProjectionFollowsTheAnimeIdPreference() {
        val index = AnimeIdIndex(
            listOf(AnimeIdMapping(mal = 1, kitsu = 10, imdb = listOf("tt0001"), tvdb = 99)),
        )
        val entries = listOf(
            MalListEntry(malId = 1, title = "A", statusWire = "watching", updatedAt = "2026-10-01T10:00:00+00:00"),
            MalListEntry(malId = 2, title = "B", statusWire = "completed", mediaType = "movie"),
            MalListEntry(malId = 3, title = "C", statusWire = null),
        )
        val byImdb = projectMalLibrary(entries, index, SimklAnimeIdPreference.IMDB)
        assertEquals(listOf("tt0001", "mal:2"), byImdb.items.map { it.id })
        assertEquals(listOf("series", "movie"), byImdb.items.map { it.type })
        assertEquals(1L, byImdb.malIdsByContentId["kitsu:10"])
        assertEquals(listOf("mal:status:watching", "mal:status:completed"), byImdb.sections.map { it.type })

        val byKitsu = projectMalLibrary(entries, index, SimklAnimeIdPreference.KITSU)
        assertEquals("kitsu:10", byKitsu.items.first().id)
    }
}
