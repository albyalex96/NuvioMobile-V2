package com.nuvio.app.features.library

import com.nuvio.app.features.tracking.TrackingProviderId
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeLibraryViewsTest {
    private val malSection = LibrarySection(type = "mal:status:watching", displayTitle = "Watching", items = emptyList())
    private val traktSection = LibrarySection(type = "watchlist", displayTitle = "Watchlist", items = emptyList())
    private val malLibrary = AnimeLibraryView(
        providerId = TrackingProviderId.MAL,
        state = LibraryUiState(sourceMode = LibrarySourceMode.MAL, sections = listOf(malSection), isLoaded = true),
    )

    @Test
    fun allAddsTheAnimeTrackerListsToTheLibrarySource() {
        val saved = LibraryUiState(sourceMode = LibrarySourceMode.TRAKT, sections = listOf(traktSection), isLoaded = true)
        val merged = mergeAllLibrarySections(saved, listOf(malLibrary))
        assertEquals(listOf("watchlist", "mal:status:watching"), merged.sections.map { it.type })
    }

    @Test
    fun allDoesNotRepeatTheTrackerThatIsAlreadyTheLibrarySource() {
        val saved = LibraryUiState(sourceMode = LibrarySourceMode.MAL, sections = listOf(malSection), isLoaded = true)
        assertEquals(saved, mergeAllLibrarySections(saved, listOf(malLibrary)))
    }

    @Test
    fun animeSectionsAreLocalizedAndNamedInAll() {
        val titles = AnimeLibrarySectionTitles(
            statusTitles = mapOf("mal:status:watching" to "In corso"),
            providerLabels = mapOf(TrackingProviderId.MAL to "MAL"),
            titleFormat = "%1\$s · %2\$s",
        )
        val sections = listOf(traktSection, malSection)
        assertEquals(
            listOf("Watchlist", "MAL · In corso"),
            titles.localize(sections, withProviderName = true).map { it.displayTitle },
        )
        assertEquals(
            listOf("Watchlist", "In corso"),
            titles.localize(sections, withProviderName = false).map { it.displayTitle },
        )
    }
}
