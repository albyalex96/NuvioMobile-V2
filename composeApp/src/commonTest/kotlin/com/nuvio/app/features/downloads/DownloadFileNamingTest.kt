package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadFileNamingTest {

    @Test
    fun episodeIsSavedInSeriesAndSeasonFolders() {
        val location = episodeFileLocation(
            seriesTitle = "La casa di carta",
            seasonFolderName = "Stagione 1",
            episodeCode = "S01E02",
            episodeTitle = "Città e libertà",
            extension = "mkv",
        )

        assertEquals(listOf("La casa di carta", "Stagione 1"), location?.folderPath)
        assertEquals("S01E02 - Città e libertà.mkv", location?.fileName)
    }

    @Test
    fun forbiddenCharactersAreReplacedAndLocalizedLettersKept() {
        val location = episodeFileLocation(
            seriesTitle = "Star Wars: Andor / 安多",
            seasonFolderName = "Season 1",
            episodeCode = "1x03",
            episodeTitle = "Reckoning?  \"Part\" 3.",
            extension = "mp4",
        )

        assertEquals(listOf("Star Wars Andor 安多", "Season 1"), location?.folderPath)
        assertEquals("1x03 - Reckoning Part 3.mp4", location?.fileName)
    }

    @Test
    fun missingEpisodeTitleKeepsOnlyTheCode() {
        val location = episodeFileLocation("Show", "Specials", "S00E01", episodeTitle = " ", extension = "mp4")

        assertEquals("S00E01.mp4", location?.fileName)
    }

    @Test
    fun blankSeriesTitleHasNoLocation() {
        assertNull(episodeFileLocation("  ", "Season 1", "S01E01", "Pilot", "mp4"))
    }
}
