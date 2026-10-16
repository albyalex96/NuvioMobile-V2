package com.nuvio.app.features.player

import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginScraper
import com.nuvio.app.features.streams.StreamParser
import com.nuvio.app.features.streams.toStreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerAudioDelayTest {

    // --- AudioDelayParts: total ---

    @Test
    fun streamDelayIsAddedToTheDeviceDelay() {
        assertEquals(1_450, AudioDelayParts(deviceMs = 200).withStream(1_250).totalMs)
        assertEquals(-800, AudioDelayParts(deviceMs = 200).withStream(-1_000).totalMs)
        assertEquals(200, AudioDelayParts(deviceMs = 200).withStream(0).totalMs)
    }

    @Test
    fun totalDelayIsClampedToThePlayerRange() {
        assertEquals(AUDIO_DELAY_MAX_MS, AudioDelayParts(deviceMs = 50_000).withStream(20_000).totalMs)
        assertEquals(AUDIO_DELAY_MIN_MS, AudioDelayParts(deviceMs = -50_000).withStream(-20_000).totalMs)
    }

    @Test
    fun streamAndDeviceDelaysAreClampedOnInput() {
        assertEquals(AUDIO_DELAY_MAX_MS, AudioDelayParts().withStream(100_000).streamMs)
        assertEquals(AUDIO_DELAY_MIN_MS, AudioDelayParts().withStream(-100_000).streamMs)
        assertEquals(AUDIO_DELAY_MAX_MS, AudioDelayParts().withDevice(100_000).deviceMs)
    }

    // --- AudioDelayParts: what gets persisted ---

    @Test
    fun manualChangeWithoutStreamDelayUpdatesAndPersistsTheDeviceDelay() {
        val parts = AudioDelayParts(deviceMs = 100).withManualTotal(325)

        assertEquals(325, parts.deviceMs)
        assertEquals(0, parts.sessionAdjustMs)
        assertEquals(325, parts.totalMs)
        assertEquals(325, parts.persistableDeviceMs)
    }

    @Test
    fun manualChangeOnAStreamWithDelayIsASessionTweakAndIsNotPersisted() {
        val parts = AudioDelayParts(deviceMs = 100).withStream(5_000).withManualTotal(5_300)

        assertEquals(5_300, parts.totalMs)
        assertEquals(100, parts.deviceMs)
        assertEquals(200, parts.sessionAdjustMs)
        assertNull(parts.persistableDeviceMs)
    }

    @Test
    fun hugeStreamDelayCannotProduceAWildPersistedDeviceDelay() {
        val parts = AudioDelayParts().withStream(100_000).withManualTotal(AUDIO_DELAY_MAX_MS - 25)

        assertEquals(0, parts.deviceMs)
        assertNull(parts.persistableDeviceMs)
    }

    // --- AudioDelayParts: stream switch / route change ---

    @Test
    fun tweakOnAStreamWithDelayDoesNotLeakIntoTheNextStream() {
        val onStreamA = AudioDelayParts(deviceMs = 0).withStream(5_000).withManualTotal(5_200)

        val onStreamB = onStreamA.withStream(0)

        assertEquals(0, onStreamB.totalMs)
        assertEquals(0, onStreamB.sessionAdjustMs)
        assertEquals(25, onStreamB.withManualTotal(25).persistableDeviceMs)
    }

    @Test
    fun clampedTotalDoesNotCorruptTheDeviceDelayOnStreamSwitch() {
        val clamped = AudioDelayParts(deviceMs = 10_000).withStream(55_000)
        assertEquals(AUDIO_DELAY_MAX_MS, clamped.totalMs)

        val next = clamped.withStream(0)

        assertEquals(10_000, next.deviceMs)
        assertEquals(10_000, next.totalMs)
    }

    @Test
    fun switchingBetweenStreamsWithDelayKeepsTheDeviceDelayAndDropsTheTweak() {
        val parts = AudioDelayParts(deviceMs = 150).withStream(5_000).withManualTotal(5_400).withStream(-2_000)

        assertEquals(150, parts.deviceMs)
        assertEquals(0, parts.sessionAdjustMs)
        assertEquals(-1_850, parts.totalMs)
    }

    @Test
    fun reloadingTheStoredDeviceDelayKeepsTheTweakOnTheCurrentStream() {
        val parts = AudioDelayParts(deviceMs = 100).withStream(5_000).withManualTotal(5_300)

        val sameRoute = parts.withDevice(100)
        val bluetoothRoute = parts.withDevice(250)

        assertEquals(5_300, sameRoute.totalMs)
        assertEquals(5_450, bluetoothRoute.totalMs)
        assertEquals(200, bluetoothRoute.sessionAdjustMs)
    }

    // --- Hint normalization and parsing ---

    @Test
    fun hintValuesAreRoundedClampedAndNonFiniteValuesDropped() {
        assertEquals(1_250, 1_250.toStreamAudioDelayMs())
        assertEquals(1_250, 1_250.4.toStreamAudioDelayMs())
        assertEquals(1_251, 1_250.6.toStreamAudioDelayMs())
        assertEquals(-1_251, (-1_250.6).toStreamAudioDelayMs())
        assertEquals(1_250, 1_250L.toStreamAudioDelayMs())
        assertEquals(AUDIO_DELAY_MAX_MS, 100_000.0.toStreamAudioDelayMs())
        assertEquals(AUDIO_DELAY_MIN_MS, (-100_000.0).toStreamAudioDelayMs())
        assertEquals(AUDIO_DELAY_MAX_MS, 1e12.toStreamAudioDelayMs())
        assertNull(Double.NaN.toStreamAudioDelayMs())
        assertNull(Double.POSITIVE_INFINITY.toStreamAudioDelayMs())
        assertNull((null as Number?).toStreamAudioDelayMs())
    }

    @Test
    fun addonResponseWithIntegerDecimalStringOrMissingAudioDelayParses() {
        val payload = """
            {"streams":[
              {"url":"https://a","behaviorHints":{"audioDelayMs":1250}},
              {"url":"https://b","behaviorHints":{"audioDelayMs":1250.6}},
              {"url":"https://c","behaviorHints":{"audioDelayMs":-400.0}},
              {"url":"https://d","behaviorHints":{"audioDelayMs":250000}},
              {"url":"https://e","behaviorHints":{"audioDelayMs":"1250"}},
              {"url":"https://f","behaviorHints":{"bingeGroup":"x"}},
              {"url":"https://g"}
            ]}
        """.trimIndent()

        val delays = StreamParser.parse(payload, addonName = "Addon", addonId = "addon")
            .map { it.behaviorHints.audioDelayMs }

        assertEquals(listOf(1_250, 1_251, -400, AUDIO_DELAY_MAX_MS, null, null, null), delays)
    }

    @Test
    fun pluginResultAudioDelayReachesTheStreamHints() {
        val scraper = PluginScraper(
            id = "scraper",
            repositoryUrl = "https://repo",
            name = "Scraper",
            description = "",
            version = "1.0.0",
            filename = "scraper.js",
            supportedTypes = listOf("movie"),
            enabled = true,
            manifestEnabled = true,
            code = "",
        )
        val withHeaders = PluginRuntimeResult(
            title = "A",
            url = "https://a",
            headers = mapOf("Referer" to "https://x"),
            audioDelayMs = 900,
        ).toStreamItem(scraper)
        val withoutHeaders = PluginRuntimeResult(title = "B", url = "https://b", audioDelayMs = -300)
            .toStreamItem(scraper)

        assertEquals(900, withHeaders.behaviorHints.audioDelayMs)
        assertEquals(-300, withoutHeaders.behaviorHints.audioDelayMs)
    }

    @Test
    fun formatsTheDelayWithSignAndMilliseconds() {
        assertEquals("0.000s", formatAudioDelay(0))
        assertEquals("+1.250s", formatAudioDelay(1_250))
        assertEquals("-0.025s", formatAudioDelay(-25))
    }
}
