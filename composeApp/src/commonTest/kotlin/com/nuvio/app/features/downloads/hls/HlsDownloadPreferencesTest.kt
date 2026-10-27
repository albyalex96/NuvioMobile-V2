package com.nuvio.app.features.downloads.hls

import kotlin.test.Test
import kotlin.test.assertEquals

class HlsDownloadPreferencesTest {

    private val strings = HlsLabelStrings(
        track = { "Track $it" },
        forced = "forced",
        audioDescription = "AD",
        hearingImpaired = "SDH",
        singleQuality = "Single",
        bitrate = { "$it Mbps" },
    )

    private fun choices(master: String) = HlsDownloadChoices(
        playlist = HlsPlaylistParser.parseMultivariant(master.trimIndent(), "https://h.tv/master.m3u8"),
        directPlaylistUrl = "https://h.tv/master.m3u8",
        strings = strings,
        languageName = { null },
    )

    private val firstEpisode = choices(
        """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Italiano",LANGUAGE="ita",DEFAULT=YES,URI="a/ita.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",LANGUAGE="eng",URI="a/eng.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="en",URI="s/en.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=6200000,CODECS="avc1.640028,mp4a.40.2,wvtt",RESOLUTION=1920x1080,AUDIO="aac",SUBTITLES="subs"
        v/1080.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,CODECS="avc1.4d401f,mp4a.40.2,wvtt",RESOLUTION=1280x720,AUDIO="aac",SUBTITLES="subs"
        v/720.m3u8
        """,
    )

    @Test
    fun choicesOfTheFirstEpisodeApplyToAnotherPlaylist() {
        val video = firstEpisode.videoOptions.first { it.playlistUrl.endsWith("720.m3u8") }
        val audio = firstEpisode.audioOptions(video).filter { it.rendition.language == "eng" }
        val subtitles = firstEpisode.subtitleOptions(video)
        val preferences = firstEpisode.preferencesFor(video, audio, subtitles)

        val nextEpisode = choices(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Italiano",LANGUAGE="it",DEFAULT=YES,URI="b/it.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",LANGUAGE="en",URI="b/en.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="eng",URI="t/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,CODECS="avc1.640028,mp4a.40.2,wvtt",RESOLUTION=1920x1080,AUDIO="aac",SUBTITLES="subs"
            w/1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,CODECS="avc1.4d401f,mp4a.40.2,wvtt",RESOLUTION=1280x720,AUDIO="aac",SUBTITLES="subs"
            w/720.m3u8
            """,
        )
        val spec = nextEpisode.specFor(preferences, durationSeconds = 1_500.0)

        assertEquals("https://h.tv/w/720.m3u8", spec.videoPlaylistUrl)
        assertEquals(listOf("https://h.tv/b/en.m3u8"), spec.audio.map { it.playlistUrl })
        assertEquals(listOf("https://h.tv/t/en.m3u8"), spec.subtitles.map { it.playlistUrl })
    }

    @Test
    fun missingHeightFallsBackToTheBestBelow() {
        val preferences = HlsDownloadPreferences(height = 900, audioLanguages = listOf("de"))

        val spec = firstEpisode.specFor(preferences, durationSeconds = null)

        assertEquals("https://h.tv/v/720.m3u8", spec.videoPlaylistUrl)
        // No German audio: the default audio of the playlist is kept.
        assertEquals(listOf("https://h.tv/a/ita.m3u8"), spec.audio.map { it.playlistUrl })
    }
}
