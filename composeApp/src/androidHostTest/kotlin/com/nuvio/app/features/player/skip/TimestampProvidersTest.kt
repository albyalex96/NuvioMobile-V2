package com.nuvio.app.features.player.skip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimestampProvidersTest {
    private fun interval(type: String, provider: String, start: Double = 10.0, end: Double = 40.0) =
        SkipInterval(startTime = start, endTime = end, type = type, provider = provider)

    @Test
    fun priorityOrderKeepsUnknownAndNewProvidersStable() {
        val items = listOf("introdb", "skipdb", "animeskip", "aniskip")
        assertEquals(
            listOf("skipdb", "aniskip", "introdb", "animeskip"),
            orderByPriority(items, listOf("skipdb", "removed-service", "aniskip")) { it },
        )
        assertEquals(items, orderByPriority(items, emptyList()) { it })
    }

    @Test
    fun mergeTakesEachSegmentFromTheHighestProviderThatHasIt() {
        val skipDb = listOf(interval("intro", "skipdb"))
        val introDb = listOf(interval("intro", "introdb", 12.0, 42.0), interval("outro", "introdb", 1200.0, 1290.0))
        val merged = mergeTimestampsByPriority(listOf(skipDb, introDb))
        assertEquals(listOf("intro@skipdb", "outro@introdb"), merged.map { "${it.type}@${it.provider}" })
    }

    @Test
    fun withoutMergingOnlyTheFirstProviderWithDataIsUsed() {
        val empty = emptyList<SkipInterval>()
        val unusable = listOf(interval("preview", "skipdb"))
        val introDb = listOf(interval("intro", "introdb"))
        val aniSkip = listOf(interval("ed", "aniskip", 1200.0, 1290.0))
        val merged = mergeTimestampsByPriority(listOf(empty, unusable, introDb, aniSkip), mergeProviders = false)
        assertEquals(listOf("intro@introdb"), merged.map { "${it.type}@${it.provider}" })
        assertTrue(mergeTimestampsByPriority(listOf(empty, empty), mergeProviders = false).isEmpty())
    }

    @Test
    fun submitTargetsDefaultToTheHighestReadyServiceUntilChosen() {
        val ready = listOf("skipdb", "introdb")
        assertEquals(listOf("skipdb"), selectSubmitTargets(ready, null))
        assertEquals(listOf("skipdb", "introdb"), selectSubmitTargets(ready, listOf("introdb", "skipdb")))
        assertEquals(listOf("introdb"), selectSubmitTargets(listOf("introdb"), listOf("introdb", "skipdb")))
        assertTrue(selectSubmitTargets(ready, emptyList()).isEmpty())
        assertTrue(selectSubmitTargets(emptyList(), null).isEmpty())
    }

    @Test
    fun skipDbFiltersApplyToConfidenceAndOutOfRangeMatches() {
        val response = SkipDbSegmentsResponse(
            segments = SkipDbSegments(
                intro = SkipDbSegment(startMs = 61_000, endMs = 91_000, match = "exact", confidence = 0.93),
                recap = SkipDbSegment(startMs = 0, endMs = 30_000, match = "shifted", confidence = 0.4),
                outro = SkipDbSegment(startMs = 2_760_000, endMs = null, match = "out-of-range", confidence = 0.9),
                preview = SkipDbSegment(startMs = 2_700_000, endMs = 2_760_000, match = "exact", confidence = 1.0),
            ),
        )
        val all = response.toSkipIntervals(SkipDbSettings(), durationSec = 2_820.0, isMovie = false)
        assertEquals(listOf("intro", "recap", "outro"), all.map { it.type })
        assertEquals(2_820.0, all.last().endTime)

        val filtered = response.toSkipIntervals(
            SkipDbSettings(ignoreOutOfRange = true, minConfidencePercent = 50),
            durationSec = 2_820.0,
            isMovie = false,
        )
        assertEquals(listOf("intro"), filtered.map { it.type })

        val movie = response.toSkipIntervals(SkipDbSettings(), durationSec = null, isMovie = true)
        // An open-ended outro without a known duration cannot be skipped.
        assertEquals(listOf("intro", "recap"), movie.map { it.type })
    }

    @Test
    fun skipDbUrlCarriesDurationAndAdjustOnlyWhenNeeded() {
        assertEquals(
            "https://api.skipdb.tv/api/segments?imdb_id=tt0903747&season=1&episode=1&duration=3500",
            skipDbSegmentsUrl("tt0903747", 1, 1, 3_500.4, SkipDbAdjustMode.CONSERVATIVE),
        )
        assertEquals(
            "https://api.skipdb.tv/api/segments?imdb_id=tt0111161&adjust=none",
            skipDbSegmentsUrl("tt0111161", null, null, null, SkipDbAdjustMode.NONE),
        )
    }

    @Test
    fun skipDbSubmissionAcceptsPendingAndAlreadyApproved() {
        assertTrue(SkipDbSubmitResult(201, "pending").isAccepted)
        assertTrue(SkipDbSubmitResult(409, "already_approved").isAccepted)
        assertTrue(!SkipDbSubmitResult(201, "rejected").isAccepted)
        assertTrue(!SkipDbSubmitResult(409, null).isAccepted)
        assertTrue(!SkipDbSubmitResult(401, null).isAccepted)
    }

    @Test
    fun unreadableOrOlderSettingsFallBackToDefaults() {
        val decode = TimestampProviderSettingsRepository::decodeTimestampProviderSettings
        assertEquals(TimestampProviderSettings(), decode("not json"))
        assertEquals(TimestampProviderSettings(), decode(null))
        val older = decode("""{"priority":["skipdb"],"future_field":1}""")
        assertEquals(listOf("skipdb"), older.priority)
        assertEquals(null, older.submitTargets)
        assertTrue(older.mergeProviders)
        assertEquals(SkipDbSettings(), older.skipDb)
    }

    @Test
    fun movieCreditsStopBeforePostCreditsScene() {
        val trimmed = trimMovieCreditsBeforePostCredits(
            listOf(
                interval("movie-credits", "skipdb", 6_000.0, 6_400.0),
                interval("post-credits", "introdb", 6_300.0, 6_350.0),
            ),
        )
        assertEquals(6_300.0, trimmed.first { it.type == "movie-credits" }.endTime)
    }
}
