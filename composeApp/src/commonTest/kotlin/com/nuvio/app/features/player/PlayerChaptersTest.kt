package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerChaptersTest {
    private val chapters = listOf(
        PlayerChapter(title = "Intro", startMs = 0L),
        PlayerChapter(title = "Middle", startMs = 60_000L),
        PlayerChapter(title = "End", startMs = 120_000L),
    )

    @Test
    fun currentChapterFollowsThePosition() {
        assertEquals(0, chapters.currentChapterIndex(0L))
        assertEquals(0, chapters.currentChapterIndex(59_999L))
        assertEquals(1, chapters.currentChapterIndex(60_000L))
        assertEquals(2, chapters.currentChapterIndex(500_000L))
        assertEquals(-1, listOf(PlayerChapter("Late", 10_000L)).currentChapterIndex(5_000L))
    }

    @Test
    fun marksSkipTheStartAndChaptersBeyondTheDuration() {
        assertEquals(listOf(0.25f, 0.5f), chapters.chapterMarkFractions(240_000L))
        assertEquals(listOf(0.5f), chapters.chapterMarkFractions(120_000L))
        assertTrue(chapters.chapterMarkFractions(0L).isEmpty())
    }

    @Test
    fun mpvChaptersAreParsedSortedAndDeduplicated() {
        val properties = mapOf(
            "chapter-list/count" to "4",
            "chapter-list/0/time" to "90.5",
            "chapter-list/0/title" to " Second ",
            "chapter-list/1/time" to "0",
            "chapter-list/2/time" to "90.5",
            "chapter-list/3/time" to "not-a-number",
        )
        val parsed = mpvChapters { properties[it] }
        assertEquals(
            listOf(PlayerChapter(title = "", startMs = 0L), PlayerChapter(title = "Second", startMs = 90_500L)),
            parsed,
        )
    }

    @Test
    fun aSingleMpvChapterCountsAsNone() {
        val properties = mapOf("chapter-list/count" to "1", "chapter-list/0/time" to "0")
        assertTrue(mpvChapters { properties[it] }.isEmpty())
        assertTrue(mpvChapters { null }.isEmpty())
    }
}
