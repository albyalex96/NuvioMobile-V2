package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveTvParsingTest {

    private val playlist = """
        #EXTM3U url-tvg="https://example.com/guide.xml.gz,https://example.com/second.xml" x-tvg-url="ftp://ignored"
        #EXTINF:-1 tvg-id="rai1.it" tvg-logo="https://example.com/rai1.png" group-title="Generalisti",Rai 1 HD
        https://stream.example.com/rai1.m3u8
        #EXTINF:-1 group-title="News",
        https://stream.example.com/news/channel.ts?token=abc
        #EXTINF:-1,Duplicate
        https://stream.example.com/rai1.m3u8
    """.trimIndent()

    @Test
    fun parsesChannelsWithAttributes() {
        val channels = parseM3uPlaylist(playlist)

        assertEquals(2, channels.size)
        val rai = channels[0]
        assertEquals("Rai 1 HD", rai.name)
        assertEquals("rai1.it", rai.epgId)
        assertEquals("https://example.com/rai1.png", rai.logoUrl)
        assertEquals("Generalisti", rai.group)
    }

    @Test
    fun fallsBackToFileNameWhenTitleIsMissing() {
        val news = parseM3uPlaylist(playlist)[1]

        assertEquals("channel.ts", news.name)
        assertEquals("News", news.group)
        assertNull(news.epgId)
    }

    @Test
    fun readsGuideUrlsFromHeaderKeepingOnlyHttp() {
        assertEquals(
            listOf("https://example.com/guide.xml.gz", "https://example.com/second.xml"),
            parseM3uEpgUrls(playlist),
        )
    }

    @Test
    fun parsesXmltvTimeWithOffset() {
        // 2026-10-15 20:30:00 +0200 == 18:30:00 UTC
        assertEquals(1_792_089_000_000L, parseXmltvTime("20261015203000 +0200"))
        assertNull(parseXmltvTime("2026"))
    }

    @Test
    fun matchesGuideToChannelsByTvgIdAndFindsCurrentProgram() {
        val start = parseXmltvTime("20261015200000 +0000")!!
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="rai1.it"><display-name>Rai 1</display-name></channel>
              <programme start="20261015200000 +0000" stop="20261015210000 +0000" channel="rai1.it">
                <title>Telegiornale &amp; Meteo</title>
              </programme>
              <programme start="20261015210000 +0000" stop="20261015223000 +0000" channel="rai1.it">
                <title>Film</title>
              </programme>
            </tv>
        """.trimIndent()
        val guide = parseXmltvGuide(xml, windowStartMillis = start - 3_600_000L, windowEndMillis = start + 86_400_000L)
        val channels = parseM3uPlaylist(playlist)

        val programs = matchEpgToChannels(channels, listOf(guide))

        val raiPrograms = programs.getValue(channels[0].id)
        assertEquals("Telegiornale & Meteo", raiPrograms.currentProgram(start + 60_000L)?.title)
        assertEquals("Film", raiPrograms.nextProgram(start + 60_000L)?.title)
        assertNull(programs[channels[1].id])
    }
}
