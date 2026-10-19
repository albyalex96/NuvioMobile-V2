package com.nuvio.app.features.downloads.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlsDownloadChoicesTest {

    private val strings = HlsLabelStrings(
        track = { "Traccia $it" },
        forced = "forzati",
        audioDescription = "Audiodescrizione",
        hearingImpaired = "Non udenti",
        singleQuality = "Qualità unica",
        bitrate = { "$it Mbps" },
    )
    private val languageNames = mapOf("ita" to "italiano", "it" to "Italiano", "eng" to "Inglese", "en" to "Inglese")

    private fun choices(master: String, preferredAudio: List<String> = emptyList()) = HlsDownloadChoices(
        playlist = HlsPlaylistParser.parseMultivariant(master.trimIndent(), "https://h.tv/master.m3u8"),
        directPlaylistUrl = "https://h.tv/master.m3u8",
        strings = strings,
        languageName = { languageNames[it] },
        preferredAudioLanguages = preferredAudio,
    )

    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Italiano",LANGUAGE="ita",DEFAULT=YES,URI="a/ita.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",LANGUAGE="eng",URI="a/eng.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",LANGUAGE="eng",URI="a/eng-copy.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ec3",NAME="Russkij",LANGUAGE="rus",URI="a/rus.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Italiano forzati",LANGUAGE="it",FORCED=YES,URI="s/it-forced.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",LANGUAGE="en",URI="s/en.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=6200000,CODECS="avc1.640028,mp4a.40.2,wvtt",RESOLUTION=1920x1080,AUDIO="aac",SUBTITLES="subs"
        v/1080.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=6400000,CODECS="avc1.640028,ec-3",RESOLUTION=1920x1080,AUDIO="ec3"
        v/1080-ec3.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=900000,CODECS="avc1.4d401e,mp4a.40.2",RESOLUTION=640x360,AUDIO="aac"
        v/360.m3u8
    """

    @Test
    fun offersOnlyTheAudioOfTheChosenQuality() {
        val choices = choices(master)
        val aacVideo = choices.videoOptions.first { it.playlistUrl.endsWith("1080.m3u8") }

        val audio = choices.audioOptions(aacVideo)

        // Russian belongs to another audio group and the duplicated English playlist is dropped.
        assertEquals(listOf("Italiano [ita]", "Inglese [eng]"), audio.map { it.label })
        assertEquals(setOf("https://h.tv/a/ita.m3u8"), choices.defaultAudioIds(audio))
    }

    @Test
    fun selectsTheAudioInThePreferredLanguageOfTheSettings() {
        val choices = choices(master, preferredAudio = listOf("de", "en"))
        val video = choices.videoOptions.first { it.playlistUrl.endsWith("1080.m3u8") }

        // No German track: the second preferred language wins over the stream's DEFAULT (Italian).
        assertEquals(setOf("https://h.tv/a/eng.m3u8"), choices.defaultAudioIds(choices.audioOptions(video)))
    }

    @Test
    fun labelsVideoVersionsAndSortsThemByQuality() {
        val labels = choices(master).videoOptions.map { it.label }

        assertEquals(
            listOf("1080p · H.264 · 6.4 Mbps", "1080p · H.264 · 6.2 Mbps", "360p · H.264 · 0.9 Mbps"),
            labels,
        )
    }

    @Test
    fun marksForcedSubtitlesAndKeepsTheOriginalValue() {
        val choices = choices(master)
        val video = choices.videoOptions.first { it.playlistUrl.endsWith("1080.m3u8") }

        val subtitles = choices.subtitleOptions(video).map { it.label }

        assertEquals(listOf("Italiano (forzati) [it]", "Inglese [en]"), subtitles)
    }

    @Test
    fun addsDetailsOnlyToTellTracksApart() {
        val renditions = listOf(
            rendition(language = "ita", name = "Italiano", channels = "2"),
            rendition(language = "ita", name = "Italiano", channels = "6"),
            rendition(language = "eng", name = "English"),
            rendition(language = "eng", name = "Commentary"),
            rendition(language = "xx", name = "xyz audio"),
            rendition(language = null, name = null),
            rendition(language = "ita", name = "Italiano AD", characteristics = "public.accessibility.describes-video"),
        )

        val labels = labelHlsRenditions(renditions, strings) { languageNames[it] }

        assertEquals(
            listOf(
                "Italiano · 2.0 [ita]",
                "Italiano · 5.1 [ita]",
                // "English" is just the language name: only the commentary needs its name.
                "Inglese [eng]",
                "Inglese · Commentary [eng]",
                "Xyz audio [xx]",
                "Traccia 6",
                "Italiano · Audiodescrizione [ita]",
            ),
            labels,
        )
    }

    @Test
    fun predictsTheContainerFromTheCodecs() {
        assertEquals(HlsOutputContainer.Mp4, predictHlsContainer(listOf("avc1.640028", "mp4a.40.2", "wvtt")))
        assertEquals(HlsOutputContainer.Mkv, predictHlsContainer(listOf("avc1.640028", "ec-3")))
        assertEquals(HlsOutputContainer.Mkv, predictHlsContainer(listOf("hvc1.2.4.L150", "mp4a.40.34")))
        assertEquals(HlsOutputContainer.Mkv, predictHlsContainer(emptyList()))
        assertEquals(listOf("ec-3"), hlsMp4IncompatibleCodecs(listOf("avc1.64", "mp4a.40.2", "ec-3")))
    }

    @Test
    fun buildsTheSpecFromTheSelection() {
        val choices = choices(master)
        val video = choices.videoOptions.first { it.playlistUrl.endsWith("1080-ec3.m3u8") }
        val audio = choices.audioOptions(video)

        val spec = choices.buildSpec(video, audio, emptyList(), durationSeconds = 120.0)

        assertEquals("https://h.tv/v/1080-ec3.m3u8", spec.videoPlaylistUrl)
        assertEquals(HlsOutputContainer.Mkv, spec.container)
        assertEquals(listOf("rus"), spec.audio.map { it.language })
        assertTrue(spec.audio.single().isDefault)
        assertFalse(spec.includeMuxedAudio)
    }

    @Test
    fun keepsAudioCarriedInsideTheVideo() {
        val choices = choices(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="g",NAME="Italiano",LANGUAGE="ita",DEFAULT=YES
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="g",NAME="English",LANGUAGE="eng",URI="eng.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS="avc1.64,mp4a.40.2",RESOLUTION=1280x720,AUDIO="g"
            v.m3u8
            """,
        )
        val video = choices.videoOptions.single()
        val audio = choices.audioOptions(video)
        assertTrue(audio.first().isMuxed)

        val spec = choices.buildSpec(video, audio, emptyList(), null)

        assertTrue(spec.includeMuxedAudio)
        assertEquals("ita", spec.muxedAudioLanguage)
        assertEquals(listOf("eng"), spec.audio.map { it.language })
        assertFalse(spec.audio.single().isDefault)
    }

    @Test
    fun streamWithoutVariantsHasASingleQualityAndItsOwnAudio() {
        val choices = HlsDownloadChoices(null, "https://h.tv/media.m3u8", strings, languageName = { null })
        val video = choices.videoOptions.single()

        assertEquals("Qualità unica", video.label)
        assertTrue(choices.audioOptions(video).isEmpty())
        assertTrue(choices.buildSpec(video, emptyList(), emptyList(), null).includeMuxedAudio)
    }

    @Test
    fun formatsChannels() {
        assertEquals("2.0", formatHlsChannels("2"))
        assertEquals("5.1", formatHlsChannels("6"))
        assertEquals("7.1", formatHlsChannels("8"))
        assertEquals("Atmos", formatHlsChannels("16/JOC"))
    }

    private fun rendition(
        language: String?,
        name: String?,
        channels: String? = null,
        characteristics: String? = null,
    ) = HlsRendition(
        type = HlsRenditionType.Audio,
        groupId = "g",
        name = name,
        language = language,
        uri = "https://h.tv/${language}_${name}_$channels.m3u8",
        isDefault = false,
        autoSelect = false,
        forced = false,
        channels = channels,
        characteristics = characteristics,
    )
}
