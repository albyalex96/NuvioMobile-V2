package com.nuvio.app.features.streams.parser

import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamDebridCacheState
import com.nuvio.app.features.streams.StreamDebridCacheStatus
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericStreamParserTest {
    private val shawshank = "The Shawshank Redemption"

    // --- AIOStreams (real payload) -----------------------------------------------------

    @Test
    fun `aiostreams cached 4k remux`() {
        val info = parse(
            aio(
                name = "🎬 The Shawshank Redemption ",
                description = "🔥4K UHD \n🎥 BluRay REMUX 📺 HDR 🎞️ HEVC \n🗣️ 🇮🇹 / 🇬🇧\n📦 58.3 GB 📊 54.7 Mbps \n⚡Ready (TB) 🔍IlCorsaroViola ",
                url = "https://icv.example/torbox-stream/key/magnet/movie/x",
                bingeGroup = "aiostreams-nightly.viren070.com|2160p|BluRay REMUX",
                videoSize = 58282365780,
                filename = "The Shawshank Redemption 1994 4K HDR 2160p BDRemux Ita Eng x265",
            ),
        )

        assertEquals(StreamParserResolution.UHD_4K, info.resolution)
        assertEquals(listOf("REMUX", "HDR", "HEVC"), info.labels())
        assertEquals(listOf(StreamParserLanguage.ITALIAN, StreamParserLanguage.ENGLISH), info.languages)
        assertEquals(58282365780, info.sizeBytes)
        assertEquals("IlCorsaroViola", info.sourceLabel)
        assertTrue(info.isCached)
        assertFalse(info.isTorrent)
        assertFalse(info.isProxied)
    }

    @Test
    fun `aiostreams p2p torrent with release group`() {
        val info = parse(
            aio(
                description = "🔥4K UHD \n🎥 BluRay REMUX 📺 HDR 🎞️ HEVC \n🗣️ 🇮🇹 / 🇬🇧\n📦 58.3 GB 📊 54.7 Mbps \n🏷️ NAHOM 📡 EasyJack \n⚠️ P2P 🔍EasyStreams | EP Personale ",
                infoHash = "b34f2891a4e8a72a293464beedf6a49efd8ec614",
                bingeGroup = "aiostreams-nightly.viren070.com|2160p|BluRay REMUX|NAHOM",
                videoSize = 58282222280,
                filename = "The.Shawshank.Redemption.1994.4K.HDR.2160p.BDRemux Ita Eng x265-NAHOM.mkv",
            ),
        )

        assertEquals(StreamParserResolution.UHD_4K, info.resolution)
        assertEquals(listOf("REMUX", "HDR", "HEVC"), info.labels())
        assertEquals("EasyStreams", info.sourceLabel)
        assertFalse(info.isCached)
        assertTrue(info.isTorrent)
    }

    @Test
    fun `aiostreams audio list ignores subtitle flags`() {
        val info = parse(
            aio(
                description = "🔥4K UHD \n🎥 BluRay REMUX 📺 HDR10 🎞️ HEVC ⏱️ 2h:22m:32s \n🎧 DTS-HD MA | DTS | DD 🔊 5.1 | 2.0 🗣️ 🇮🇹 / 🇬🇧 / 🇫🇷 / 🇩🇪 / 🇪🇸 / 🇨🇳 / 🇭🇺 / 🇵🇱 / 🇯🇵📝 🇮🇹 / 🇬🇧 / 🇯🇵 / 🇫🇷 / 🇩🇪 / 🇪🇸 / 🇳🇱 / 🇨🇳 / 🇰🇷 / 🇨🇿 / 🇩🇰 / 🇫🇮 / 🇳🇴 / 🇵🇱 / 🇸🇪 / 🇭🇺\n📦 62.3 GB 📊 58.2 Mbps \n🏷️ FGT 📡 EasyJack \n⚠️ P2P 🔍EasyStreams | EP Personale ",
                infoHash = "32770c6dd35a4dd71deb80d5deff7a4e084eedab",
                bingeGroup = "aiostreams-nightly.viren070.com|2160p|BluRay REMUX|FGT",
                videoSize = 62254402955,
                filename = "The.Shawshank.Redemption.1994.2160p.BluRay.REMUX.HEVC.DTS-HD.MA.5.1-FGT.mkv",
            ),
        )

        assertEquals(listOf("REMUX", "HDR10", "HEVC", "DTS-HD MA", "DD", "5.1", "2.0"), info.labels())
        assertEquals(
            listOf(
                StreamParserLanguage.ITALIAN,
                StreamParserLanguage.ENGLISH,
                StreamParserLanguage.FRENCH,
                StreamParserLanguage.GERMAN,
                StreamParserLanguage.SPANISH,
                StreamParserLanguage.CHINESE,
                StreamParserLanguage.HUNGARIAN,
                StreamParserLanguage.POLISH,
                StreamParserLanguage.JAPANESE,
            ),
            info.languages,
        )
        assertEquals(2L * 3600 + 22 * 60 + 32, info.durationSeconds)
    }

    @Test
    fun `aiostreams dolby vision merges filename and description`() {
        val info = parse(
            aio(
                name = "🎬 Le Ali Della Liberta' - The Shawshank Redemption ",
                description = "🔥4K UHD \n🎥 BluRay 📺 DV | HDR10 | 10bit 🎞️ HEVC ⏱️ 2h:26m:2s \n🎧 DD 🔊 5.1 🗣️ 🇮🇹 / 🇬🇧📝 🇮🇹 / 🇬🇧\n📦 6.48 GB 📊 5.92 Mbps \n⚡Ready (TB) 🔍IlCorsaroViola ",
                url = "https://icv.example/torbox-stream/key/packfile/x.mkv",
                bingeGroup = "aiostreams-nightly.viren070.com|2160p|BluRay",
                videoSize = 6482226557,
                filename = "Le Ali della Liberta' - The Shawshank Redemption (1994) 2160p H265 10 bit DV HDR10+ ita eng AC3 5.1 sub ita eng Licdom.mkv",
            ),
        )

        assertEquals(StreamParserResolution.UHD_4K, info.resolution)
        assertEquals(listOf("BluRay", "Dolby Vision", "HDR10+", "10bit", "HEVC", "DD", "5.1"), info.labels())
        assertEquals(listOf(StreamParserLanguage.ITALIAN, StreamParserLanguage.ENGLISH), info.languages)
        assertTrue(info.isCached)
    }

    @Test
    fun `aiostreams full hd avc remux`() {
        val info = parse(
            aio(
                description = "🚀 FHD \n🎥 BluRay REMUX 🎞️ AVC ⏱️ 2h:22m:32s \n🎧 DTS-HD MA | DD 🔊 5.1 | 2.0 🗣️ 🇮🇹 / 🇬🇧 📝 🇮🇹\n📦 31.4 GB 📊 29.4 Mbps \n🏷️ FGT 📡 EasyJack \n⚠️ P2P 🔍EasyStreams | EP Personale ",
                infoHash = "8f19010c25ea5d01f00a17e39acf7596322c9df2",
                bingeGroup = "aiostreams-nightly.viren070.com|1080p|BluRay REMUX|FGT",
                videoSize = 31382047118,
                filename = "The.Shawshank.Redemption.1994.REMASTERED.1080p.BluRay.REMUX.AVC.DTS-HD.MA.5.1-FGT.mkv",
            ),
        )

        assertEquals(StreamParserResolution.FHD, info.resolution)
        assertEquals(listOf("REMUX", "AVC", "DTS-HD MA", "DD", "5.1", "2.0"), info.labels())
        assertTrue(info.isTorrent)
    }

    // --- Other addon formats -----------------------------------------------------------

    @Test
    fun `torrentio style stream`() {
        val info = parse(
            StreamItem(
                name = "Torrentio\n4k DV | HDR",
                title = "The.Shawshank.Redemption.1994.2160p.UHD.BluRay.x265.DV.HDR.DDP5.1.Atmos-GROUP\n👤 52 💾 18.4 GB ⚙️ ThePirateBay\nMulti Audio / 🇬🇧 / 🇮🇹",
                description = "The.Shawshank.Redemption.1994.2160p.UHD.BluRay.x265.DV.HDR.DDP5.1.Atmos-GROUP\n👤 52 💾 18.4 GB ⚙️ ThePirateBay\nMulti Audio / 🇬🇧 / 🇮🇹",
                infoHash = "abcdef",
                addonName = "Torrentio",
                addonId = "torrentio",
            ),
        )

        assertEquals(StreamParserResolution.UHD_4K, info.resolution)
        assertEquals(listOf("BluRay", "Dolby Vision", "HDR", "HEVC", "Atmos", "DD+", "5.1"), info.labels())
        assertEquals(listOf(StreamParserLanguage.ENGLISH, StreamParserLanguage.ITALIAN), info.languages)
        assertEquals("ThePirateBay", info.sourceLabel)
        assertEquals((18.4 * 1024 * 1024 * 1024).toLong(), info.sizeBytes)
        assertTrue(info.isTorrent)
    }

    @Test
    fun `debrid instant markers`() {
        val rd = parse(stream(name = "[RD+] Torrentio\n1080p", description = "Movie 2020 1080p WEB-DL"))
        assertTrue(rd.isCached)

        val downloading = parse(stream(name = "[RD download] Torrentio\n1080p", description = "Movie 2020 1080p WEB-DL"))
        assertFalse(downloading.isCached)

        val fromStatus = parse(
            stream(name = "Addon", description = "1080p").copy(
                debridCacheStatus = StreamDebridCacheStatus("realdebrid", "Real-Debrid", StreamDebridCacheState.CACHED),
            ),
        )
        assertTrue(fromStatus.isCached)
    }

    @Test
    fun `cached wins over torrent`() {
        val info = parse(
            stream(name = "[RD+] Torrentio", description = "Movie 2020 1080p WEB-DL ⚠️ P2P").copy(infoHash = "abcdef"),
        )
        assertTrue(info.isCached)
        assertFalse(info.isTorrent)
    }

    @Test
    fun `plugin streams are never cached`() {
        val info = parse(
            StreamItem(
                name = "⚡ Instant 1080p",
                description = "Ready (TB) cached",
                url = "https://cdn.example/video.mp4",
                addonName = "Plugin",
                addonId = "plugin:p",
            ).copy(debridCacheStatus = StreamDebridCacheStatus("torbox", "TorBox", StreamDebridCacheState.CACHED)),
        )
        assertFalse(info.isCached)
    }

    @Test
    fun `source label is cleaned from emoji`() {
        val marker = parse(stream(description = "1080p\n🔍 🇮🇹 IlCorsaro⚡Viola 🎬 | EP Personale"))
        assertEquals("IlCorsaro Viola", marker.sourceLabel)

        val fallback = parse(
            StreamItem(url = "https://x.example/v.mkv", addonName = "🍿 AIOStreams ✨", addonId = "aiostreams"),
        )
        assertEquals("AIOStreams", fallback.sourceLabel)
    }

    @Test
    fun `plugin stream with only quality size and language`() {
        val info = parse(
            StreamItem(
                name = "MixDrop",
                description = "1080p • 1.2 GB • Italian",
                url = "https://cdn.example/video.mp4",
                sourceName = "StreamingCommunity",
                addonName = "StreamingCommunity",
                addonId = "plugin:sc",
            ),
        )

        assertEquals(StreamParserResolution.FHD, info.resolution)
        assertEquals(emptyList(), info.tags)
        assertEquals(listOf(StreamParserLanguage.ITALIAN), info.languages)
        assertEquals((1.2 * 1024 * 1024 * 1024).toLong(), info.sizeBytes)
        assertEquals("StreamingCommunity", info.sourceLabel)
        assertFalse(info.isTorrent)
    }

    @Test
    fun `empty stream falls back to defaults`() {
        val info = parse(StreamItem(url = "https://x.example/v.m3u8", addonName = "Plugin", addonId = "plugin:p"))

        assertEquals(StreamParserResolution.SD, info.resolution)
        assertTrue(info.tags.isEmpty())
        assertTrue(info.languages.isEmpty())
        assertNull(info.sizeBytes)
        assertEquals("Plugin", info.sourceLabel)
        assertFalse(info.isCached || info.isTorrent || info.isProxied)
    }

    // --- False positives ---------------------------------------------------------------

    @Test
    fun `dvdrip is not dolby vision and hdtv is not 720p`() {
        val info = parse(stream(description = "Movie.2004.DVDRip.XviD-GRP"))
        assertEquals(listOf("DVD", "XviD"), info.labels())

        val hdtv = parse(stream(description = "Show.S01E02.HDTV.x264-GRP"))
        assertEquals(StreamParserResolution.SD, hdtv.resolution)
        assertEquals(listOf("HDTV", "AVC"), hdtv.labels())
    }

    @Test
    fun `title words are not tags or languages`() {
        val info = parse(
            stream(
                name = "Chi ha incastrato Roger Rabbit",
                description = "Chi.ha.incastrato.Roger.Rabbit.1988.1080p.BluRay.x264.ITA",
            ),
            knownTitle = "Chi ha incastrato Roger Rabbit",
        )
        assertEquals(listOf(StreamParserLanguage.ITALIAN), info.languages)

        val web = parse(
            stream(name = "Charlotte's Web", description = "Charlotte's Web 2006 1080p BluRay"),
            knownTitle = "Charlotte's Web",
        )
        assertEquals(listOf("BluRay"), web.labels())
    }

    @Test
    fun `size and bitrate numbers are not channels`() {
        val info = parse(stream(description = "1080p WEB-DL 5.0 GB 📊 5.1 Mbps AAC 2.0"))
        assertEquals(listOf("WEB-DL", "AAC", "2.0"), info.labels())
        assertEquals((5.0 * 1024 * 1024 * 1024).toLong(), info.sizeBytes)
    }

    @Test
    fun `size with comma decimal`() {
        val info = parse(stream(description = "720p 💾 6,48 GB"))
        assertEquals((6.48 * 1024 * 1024 * 1024).toLong(), info.sizeBytes)
        assertEquals(StreamParserResolution.HD, info.resolution)
    }

    @Test
    fun `proxy markers`() {
        assertTrue(parse(stream(description = "1080p Proxied")).isProxied)
        assertTrue(parse(stream(description = "1080p proxy (ON)")).isProxied)
        assertTrue(parse(stream(description = "1080p Proxy on")).isProxied)
        assertFalse(parse(stream(description = "1080p proxy: off")).isProxied)
        assertFalse(parse(stream(description = "1080p")).isProxied)
    }

    @Test
    fun `fused audio and channels`() {
        val info = parse(stream(description = "Movie 2021 2160p WEB-DL DDP5.1 Atmos H.265"))
        assertEquals(listOf("WEB-DL", "HEVC", "Atmos", "DD+", "5.1"), info.labels())
    }

    @Test
    fun `size estimate is deterministic and plausible`() {
        val info = StreamParserInfo(
            resolution = StreamParserResolution.UHD_4K,
            tags = listOf(StreamParserTag(StreamParserTagKind.SOURCE, "REMUX")),
        )
        val first = StreamParserSizeEstimator.estimateBytes(info, isEpisode = false, seedKey = "abc")
        val second = StreamParserSizeEstimator.estimateBytes(info, isEpisode = false, seedKey = "abc")
        assertEquals(first, second)
        val gib = first / (1024.0 * 1024 * 1024)
        assertTrue(gib in 30.0..80.0, "4K REMUX movie estimated at $gib GiB")

        val episode = StreamParserSizeEstimator.estimateBytes(
            StreamParserInfo(resolution = StreamParserResolution.FHD),
            isEpisode = true,
            seedKey = "abc",
        ) / (1024.0 * 1024 * 1024)
        assertTrue(episode in 0.5..3.0, "1080p episode estimated at $episode GiB")
    }

    // --- Helpers -----------------------------------------------------------------------

    private fun parse(stream: StreamItem, knownTitle: String? = shawshank): StreamParserInfo =
        GenericStreamParser.parse(stream, knownTitle)

    private fun StreamParserInfo.labels(): List<String> = tags.map { it.label }

    private fun stream(name: String? = null, description: String? = null): StreamItem =
        StreamItem(name = name, description = description, url = "https://x.example/v.mkv", addonName = "Addon", addonId = "addon")

    private fun aio(
        name: String = "🎬 The Shawshank Redemption ",
        description: String,
        url: String? = null,
        infoHash: String? = null,
        bingeGroup: String,
        videoSize: Long,
        filename: String,
    ): StreamItem = StreamItem(
        name = name,
        description = description,
        url = url,
        infoHash = infoHash,
        addonName = "AIOStreams",
        addonId = "aiostreams",
        behaviorHints = StreamBehaviorHints(
            bingeGroup = bingeGroup,
            videoSize = videoSize,
            filename = filename,
        ),
    )
}
