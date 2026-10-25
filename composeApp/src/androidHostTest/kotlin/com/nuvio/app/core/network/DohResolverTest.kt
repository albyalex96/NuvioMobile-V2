package com.nuvio.app.core.network

import com.nuvio.app.core.network.dns.DnsOverHttpsEndpoint
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DohResolverTest {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val deviceAddress = InetAddress.getByAddress("device", byteArrayOf(10, 0, 0, 7))

    private class CountingDns(private val answer: List<InetAddress>) : Dns {
        val calls = AtomicInteger()
        override fun lookup(hostname: String): List<InetAddress> {
            calls.incrementAndGet()
            return answer
        }
    }

    @Test
    fun answersFromTheDohServerAreUsedAndCached() {
        MockWebServer().use { server ->
            // A and AAAA queries: both answered with the same A record.
            repeat(2) { server.enqueue(dnsAnswer(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))) }
            val system = CountingDns(listOf(deviceAddress))
            val resolver = DohResolver(
                endpoint = DnsOverHttpsEndpoint(server.url("/dns-query").toString(), emptyList()),
                fallbackToSystem = true,
                bootstrapClient = client,
                systemDns = system,
            )

            val first = resolver.lookup("example.com")
            val second = resolver.lookup("EXAMPLE.com.")

            assertTrue(first.any { it.hostAddress == "93.184.216.34" }, "$first")
            assertEquals(first, second)
            assertEquals(2, server.requestCount)
            assertEquals(0, system.calls.get())
            assertTrue(server.takeRequest().path.orEmpty().startsWith("/dns-query?dns="))
        }
    }

    @Test
    fun failuresFallBackToTheDeviceDnsWhenAllowed() {
        MockWebServer().use { server ->
            repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }
            val system = CountingDns(listOf(deviceAddress))
            val resolver = DohResolver(
                endpoint = DnsOverHttpsEndpoint(server.url("/dns-query").toString(), emptyList()),
                fallbackToSystem = true,
                bootstrapClient = client,
                systemDns = system,
            )

            assertEquals(listOf(deviceAddress), resolver.lookup("example.com"))
            assertEquals(1, system.calls.get())
        }
    }

    @Test
    fun failuresAreReportedWithoutFallback() {
        MockWebServer().use { server ->
            repeat(4) { server.enqueue(MockResponse().setResponseCode(500)) }
            val system = CountingDns(listOf(deviceAddress))
            val resolver = DohResolver(
                endpoint = DnsOverHttpsEndpoint(server.url("/dns-query").toString(), emptyList()),
                fallbackToSystem = false,
                bootstrapClient = client,
                systemDns = system,
            )

            assertFailsWith<UnknownHostException> { resolver.lookup("example.com") }
            assertEquals(0, system.calls.get())
        }
    }

    @Test
    fun localNamesAndIpLiteralsUseTheDeviceDns() {
        MockWebServer().use { server ->
            val system = CountingDns(listOf(deviceAddress))
            val resolver = DohResolver(
                endpoint = DnsOverHttpsEndpoint(server.url("/dns-query").toString(), emptyList()),
                fallbackToSystem = false,
                bootstrapClient = client,
                systemDns = system,
            )

            listOf("localhost", "nas", "jellyfin.local", "router.lan", "media.home.arpa", "192.168.1.10", "fe80::1")
                .forEach { host -> assertEquals(listOf(deviceAddress), resolver.lookup(host), host) }
            assertEquals(0, server.requestCount)
        }
    }

    /** DNS wire response with one A record for `example.com` (RFC 1035). */
    private fun dnsAnswer(ipv4: ByteArray): MockResponse {
        val body = Buffer()
            .writeShort(0) // id
            .writeShort(0x8180) // response, recursion available, no error
            .writeShort(1) // questions
            .writeShort(1) // answers
            .writeShort(0)
            .writeShort(0)
            .writeByte(7).writeUtf8("example")
            .writeByte(3).writeUtf8("com")
            .writeByte(0)
            .writeShort(1) // A
            .writeShort(1) // IN
            .writeShort(0xC00C) // name pointer to the question
            .writeShort(1)
            .writeShort(1)
            .writeInt(300) // ttl
            .writeShort(4)
            .write(ipv4)
        return MockResponse()
            .setHeader("Content-Type", "application/dns-message")
            .setBody(body)
    }
}
