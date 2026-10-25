package com.nuvio.app.core.network.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsOverHttpsSettingsTest {
    private fun valid(input: String): DohAddressParseResult.Valid {
        val result = parseDnsOverHttpsAddress(input)
        assertTrue(result is DohAddressParseResult.Valid, "Expected $input to be valid, got $result")
        return result
    }

    @Test
    fun hostNameGetsTheStandardPath() {
        val result = valid("dns.adguard-dns.com")
        assertEquals("https://dns.adguard-dns.com/dns-query", result.url)
        assertEquals("dns.adguard-dns.com", result.host)
        assertFalse(result.hostIsIp)
    }

    @Test
    fun ipv4AddressIsUsedAsHostAndBootstrap() {
        val result = valid("1.1.1.1")
        assertEquals("https://1.1.1.1/dns-query", result.url)
        assertTrue(result.hostIsIp)
        val endpoint = resolveDnsOverHttpsEndpoint(
            DnsOverHttpsSettings(provider = "custom", customAddress = "1.1.1.1", customBootstrap = "9.9.9.9"),
        )
        assertEquals(listOf("1.1.1.1"), endpoint?.bootstrapAddresses)
    }

    @Test
    fun ipv6AddressIsWrappedInBrackets() {
        assertEquals("https://[2606:4700:4700::1111]/dns-query", valid("2606:4700:4700::1111").url)
        assertEquals("https://[2606:4700:4700::1111]:8443/dns-query", valid("[2606:4700:4700::1111]:8443").url)
        assertEquals("2606:4700:4700::1111", valid("[2606:4700:4700::1111]").host)
    }

    @Test
    fun fullUrlsKeepTheirPathAndPort() {
        assertEquals("https://dns.nextdns.io/abc123", valid("https://dns.nextdns.io/abc123").url)
        assertEquals("https://dns.example.com:8443/resolve", valid("dns.example.com:8443/resolve").url)
        assertEquals("https://dns.google/dns-query", valid("HTTPS://DNS.Google/").url)
    }

    @Test
    fun invalidAddressesAreRejected() {
        assertEquals(DohAddressParseResult.Error.Empty, parseDnsOverHttpsAddress("  "))
        assertEquals(DohAddressParseResult.Error.NotHttps, parseDnsOverHttpsAddress("http://dns.google/dns-query"))
        assertEquals(DohAddressParseResult.Error.NotHttps, parseDnsOverHttpsAddress("tls://dns.google"))
        assertEquals(DohAddressParseResult.Error.InvalidHost, parseDnsOverHttpsAddress("localhost"))
        assertEquals(DohAddressParseResult.Error.InvalidHost, parseDnsOverHttpsAddress("1.1.1.300"))
        assertEquals(DohAddressParseResult.Error.InvalidHost, parseDnsOverHttpsAddress("dns example.com"))
        assertEquals(DohAddressParseResult.Error.InvalidHost, parseDnsOverHttpsAddress("dns.example.com:99999"))
        assertEquals(DohAddressParseResult.Error.InvalidHost, parseDnsOverHttpsAddress("user@dns.example.com"))
    }

    @Test
    fun bootstrapAcceptsIpv4AndIpv6Lists() {
        assertEquals(
            DohBootstrapParseResult.Valid(listOf("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff")),
            parseDnsOverHttpsBootstrap("94.140.14.14, 94.140.15.15 [2a10:50c0::ad1:ff]"),
        )
        assertEquals(DohBootstrapParseResult.Valid(emptyList()), parseDnsOverHttpsBootstrap(""))
        assertEquals(DohBootstrapParseResult.Invalid("dns.google"), parseDnsOverHttpsBootstrap("8.8.8.8, dns.google"))
    }

    @Test
    fun ipv6Validation() {
        listOf("::1", "::", "2001:db8::1", "fe80:0:0:0:0:0:0:1", "::ffff:192.168.1.1").forEach { value ->
            assertTrue(isValidIpv6(value), value)
        }
        listOf("1:::2", ":1::2", "1::2::3", "12345::1", "g::1", "1:2:3:4:5:6:7", "1.1.1.1").forEach { value ->
            assertFalse(isValidIpv6(value), value)
        }
    }

    @Test
    fun endpointFollowsTheProvider() {
        assertNull(resolveDnsOverHttpsEndpoint(DnsOverHttpsSettings()))
        val cloudflare = resolveDnsOverHttpsEndpoint(DnsOverHttpsSettings(provider = "cloudflare"))
        assertEquals("https://cloudflare-dns.com/dns-query", cloudflare?.url)
        assertTrue(cloudflare?.bootstrapAddresses.orEmpty().contains("1.1.1.1"))
        val custom = resolveDnsOverHttpsEndpoint(
            DnsOverHttpsSettings(provider = "custom", customAddress = "dns.example.com", customBootstrap = "203.0.113.5"),
        )
        assertEquals(DnsOverHttpsEndpoint("https://dns.example.com/dns-query", listOf("203.0.113.5")), custom)
        // An invalid custom address keeps the device DNS.
        assertNull(resolveDnsOverHttpsEndpoint(DnsOverHttpsSettings(provider = "custom", customAddress = "nope")))
        assertFalse(DnsOverHttpsSettings(provider = "custom").isEnabled)
    }

    @Test
    fun unknownProviderFallsBackToTheDeviceDns() {
        val settings = DnsOverHttpsSettings(provider = "future-provider").normalized()
        assertEquals(DnsOverHttpsProvider.System, settings.providerType)
        assertEquals("system", settings.provider)
    }

    @Test
    fun presetsHaveValidUrlsAndBootstrapAddresses() {
        DnsOverHttpsProvider.entries
            .filter { it != DnsOverHttpsProvider.System && it != DnsOverHttpsProvider.Custom }
            .forEach { provider ->
                val url = provider.url
                assertTrue(url != null && parseDnsOverHttpsAddress(url) is DohAddressParseResult.Valid, provider.name)
                assertTrue(provider.bootstrapAddresses.isNotEmpty(), provider.name)
                assertTrue(
                    parseDnsOverHttpsBootstrap(provider.bootstrapAddresses.joinToString(",")) is DohBootstrapParseResult.Valid,
                    provider.name,
                )
            }
    }
}
