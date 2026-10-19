package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadsFolderTest {

    @Test
    fun recognisesFileNamesSavedByTheApp() {
        assertTrue(isAppDownloadFileName("Dune Part Two_mh2k9x1q_1.mkv"))
        assertTrue(isAppDownloadFileName("The Show S01E02 Pilot_mh2k9x1q_1a.mp4"))
        assertFalse(isAppDownloadFileName("Dune.Part.Two.2024.2160p.mkv"))
        assertFalse(isAppDownloadFileName("holiday_2024.mp4"))
    }

    @Test
    fun keepsOnlyVideosThatAreNotInTheDownloadList() {
        val known = downloadItem(fileName = "Known_mh2k9x1q_1.mkv", localFileUri = "content://tree/known")
        val entries = listOf(
            entry("content://tree/known", "Known_mh2k9x1q_1.mkv"),
            entry("content://tree/renamed", "Known_mh2k9x1q_1.mkv"),
            entry("content://tree/part", "Partial_mh2k9x1q_2.mkv.part"),
            entry("content://tree/other", "Other Profile_mh2k9x1q_3.mp4", modified = 2L),
            entry("content://tree/movie", "Holiday Movie.mkv", modified = 3L),
            entry("content://tree/notes", "notes.txt"),
        )

        val found = findExternalVideos(entries, listOf(known))

        assertEquals(listOf("Holiday Movie.mkv", "Other Profile_mh2k9x1q_3.mp4"), found.map { it.fileName })
        assertEquals(ExternalVideoOrigin.External, found[0].origin)
        assertEquals(ExternalVideoOrigin.OtherProfile, found[1].origin)
        assertEquals("Holiday Movie", found[0].displayName)
    }

    @Test
    fun attachesSubtitlesWithTheSameBaseName() {
        val entries = listOf(
            entry("content://tree/video", "Movie.mkv"),
            entry("content://tree/sub", "Movie.srt"),
            entry("content://tree/sub-it", "Movie.it.srt"),
            entry("content://tree/sub-other", "Movie 2.srt"),
        )

        val subtitles = findExternalVideos(entries, emptyList()).single().subtitles

        assertEquals(listOf("und", "it"), subtitles.map { it.language })
        assertEquals(listOf("content://tree/sub", "content://tree/sub-it"), subtitles.map { it.url })
    }

    private fun entry(uri: String, name: String, modified: Long? = null) =
        DownloadsFolderEntry(uri = uri, name = name, sizeBytes = 10L, modifiedAtEpochMs = modified)

    private fun downloadItem(fileName: String, localFileUri: String) = DownloadItem(
        id = "id",
        contentType = "movie",
        parentMetaId = "tt1",
        parentMetaType = "movie",
        videoId = "tt1",
        title = "Known",
        streamTitle = "Stream",
        providerName = "Addon",
        sourceUrl = "https://example.com/video.mkv",
        localFileUri = localFileUri,
        fileName = fileName,
        status = DownloadStatus.Completed,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
    )
}
