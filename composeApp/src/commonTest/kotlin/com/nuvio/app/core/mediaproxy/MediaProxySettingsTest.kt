package com.nuvio.app.core.mediaproxy

import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaProxySettingsTest {

    @AfterTest
    fun reset() {
        MediaProxySettingsRepository.resetToDefaults()
    }

    @Test
    fun ipv4Validation() {
        assertTrue(isValidIpv4("192.168.1.20"))
        assertTrue(isValidIpv4(" 10.0.0.1 "))
        assertFalse(isValidIpv4("192.168.1"))
        assertFalse(isValidIpv4("192.168.1.256"))
        assertFalse(isValidIpv4("fe80::1"))
        assertFalse(isValidIpv4("a.b.c.d"))
    }

    @Test
    fun invalidValuesAreNormalized() {
        val normalized = MediaProxySettings(
            port = 80,
            lanAddressOverride = "not an ip",
            userAgent = " Agent\r\nInjected: 1 ",
            connectTimeoutSeconds = 1,
            readTimeoutSeconds = 1_000,
        ).normalized()

        assertEquals(MEDIA_PROXY_AUTO_PORT, normalized.port)
        assertEquals("", normalized.lanAddressOverride)
        assertEquals("AgentInjected: 1", normalized.userAgent)
        assertEquals(MEDIA_PROXY_CONNECT_TIMEOUT_RANGE.first, normalized.connectTimeoutSeconds)
        assertEquals(MEDIA_PROXY_READ_TIMEOUT_RANGE.last, normalized.readTimeoutSeconds)
    }

    @Test
    fun syncPayloadRoundTrips() {
        MediaProxySettingsRepository.update {
            it.copy(port = 8765, lanAddressOverride = "192.168.1.20", corsEnabled = false, castAlwaysProxy = true)
        }
        val payload = MediaProxySettingsRepository.exportToSyncPayload()

        MediaProxySettingsRepository.resetToDefaults()
        MediaProxySettingsRepository.applyFromSyncPayload(payload)

        val restored = MediaProxySettingsRepository.settings.value
        assertEquals(8765, restored.port)
        assertEquals("192.168.1.20", restored.lanAddressOverride)
        assertFalse(restored.corsEnabled)
        assertTrue(restored.castAlwaysProxy)
    }

    @Test
    fun blobsWithoutProxyKeysKeepTheLocalChoices() {
        MediaProxySettingsRepository.update { it.copy(port = 9000, keepAwake = false) }

        MediaProxySettingsRepository.applyFromSyncPayload(JsonObject(emptyMap()))

        val current = MediaProxySettingsRepository.settings.value
        assertEquals(9000, current.port)
        assertFalse(current.keepAwake)
    }
}
