package com.nuvio.app.core.network.dns

import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DnsOverHttpsSyncTest {
    private val quad9 = DnsOverHttpsSettings(provider = "quad9", fallbackToSystem = false)

    @BeforeTest
    fun reset() {
        DnsOverHttpsRepository.ensureLoaded()
        DnsOverHttpsRepository.setSyncAcrossDevices(false)
        DnsOverHttpsRepository.update { DnsOverHttpsSettings() }
    }

    @AfterTest
    fun cleanUp() = reset()

    @Test
    fun settingsTravelOnlyWhileSyncIsOn() {
        DnsOverHttpsRepository.update { quad9 }

        val local = DnsOverHttpsRepository.exportToSyncPayload()
        assertTrue("dns_over_https_sync" in local)
        assertFalse("dns_over_https_settings" in local)

        DnsOverHttpsRepository.setSyncAcrossDevices(true)
        val synced = DnsOverHttpsRepository.exportToSyncPayload()
        assertTrue("dns_over_https_settings" in synced)
    }

    @Test
    fun blobsWithoutTheKeysKeepTheLocalValues() {
        DnsOverHttpsRepository.update { quad9 }
        DnsOverHttpsRepository.setSyncAcrossDevices(true)

        DnsOverHttpsRepository.applyFromSyncPayload(JsonObject(emptyMap()))

        assertEquals(quad9, DnsOverHttpsRepository.settings.value)
        assertTrue(DnsOverHttpsRepository.syncAcrossDevices.value)
    }

    @Test
    fun remoteSettingsAreAppliedWhenSyncIsOn() {
        val remote = DnsOverHttpsSettings(provider = "custom", customAddress = "1.1.1.1")
        DnsOverHttpsRepository.applyFromSyncPayload(payload(sync = true, settings = remote))

        assertTrue(DnsOverHttpsRepository.syncAcrossDevices.value)
        assertEquals(remote.normalized(), DnsOverHttpsRepository.settings.value)
    }

    @Test
    fun syncOffFromAnotherDeviceKeepsTheLocalSettings() {
        DnsOverHttpsRepository.update { quad9 }
        DnsOverHttpsRepository.setSyncAcrossDevices(true)

        DnsOverHttpsRepository.applyFromSyncPayload(
            payload(sync = false, settings = DnsOverHttpsSettings(provider = "google")),
        )

        assertFalse(DnsOverHttpsRepository.syncAcrossDevices.value)
        assertEquals(quad9, DnsOverHttpsRepository.settings.value)
    }

    private fun payload(sync: Boolean, settings: DnsOverHttpsSettings): JsonObject = buildJsonObject {
        put("dns_over_https_sync", encodeSyncBoolean(sync))
        put(
            "dns_over_https_settings",
            encodeSyncString(kotlinx.serialization.json.Json.encodeToString(DnsOverHttpsSettings.serializer(), settings)),
        )
    }
}
