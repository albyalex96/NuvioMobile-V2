package com.nuvio.app.core.mediaproxy

import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** End-to-end: a loopback "upstream" that requires a header, served through the proxy. */
class LanMediaProxyLoopbackTest {
    private lateinit var upstream: HttpServer
    private val segment = ByteArray(1_000) { (it % 251).toByte() }
    private var upstreamPort = 0

    @BeforeTest
    fun startUpstream() {
        upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/") { exchange ->
            if (exchange.requestHeaders.getFirst("X-Token") != "secret") {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return@createContext
            }
            when (exchange.requestURI.path) {
                "/hls/index.m3u8" -> {
                    val body = "#EXTM3U\n#EXTINF:4.0,\nseg.ts\n".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                "/hls/seg.ts" -> {
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (range == "bytes=100-199") {
                        exchange.responseHeaders.add("Content-Range", "bytes 100-199/${segment.size}")
                        exchange.sendResponseHeaders(206, 100)
                        exchange.responseBody.use { it.write(segment, 100, 100) }
                    } else {
                        exchange.sendResponseHeaders(200, segment.size.toLong())
                        exchange.responseBody.use { it.write(segment) }
                    }
                }
                else -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                }
            }
        }
        upstream.start()
        upstreamPort = upstream.address.port
    }

    @AfterTest
    fun stopUpstream() {
        upstream.stop(0)
    }

    @Test
    fun playlistSegmentsHeadersRangesAndCorsGoThroughTheProxy() {
        val handle = assertNotNull(
            LanMediaProxy.register(
                MediaProxySource.Remote(
                    url = "http://127.0.0.1:$upstreamPort/hls/index.m3u8",
                    requestHeaders = mapOf("X-Token" to "secret"),
                ),
                MediaProxyOptions(scope = MediaProxyScope.Loopback, label = "Test"),
            ),
        )
        try {
            val playlist = get(handle.url)
            assertEquals(200, playlist.code)
            assertEquals("*", playlist.headers["Access-Control-Allow-Origin"])
            val segmentUrl = playlist.text.lines().first { it.isNotBlank() && !it.startsWith("#") }
            assertTrue(segmentUrl.startsWith(handle.url.substringBefore("/hls/")), segmentUrl)

            val partial = get(segmentUrl, range = "bytes=100-199")
            assertEquals(206, partial.code)
            assertEquals("bytes 100-199/1000", partial.headers["Content-Range"])
            assertTrue(segment.copyOfRange(100, 200).contentEquals(partial.bytes))

            // A loopback source cannot be used to reach other services on this device.
            val otherPort = handle.url.replace("/127.0.0.1:$upstreamPort/", "/127.0.0.1:1/")
            assertEquals(403, get(otherPort).code)
        } finally {
            LanMediaProxy.unregister(handle)
        }
        // Unregistered tokens are gone.
        assertEquals(true, runCatching { get(handle.url) }.map { it.code == 404 }.getOrDefault(true))
    }

    private class Response(val code: Int, val headers: Map<String, String>, val bytes: ByteArray) {
        val text: String get() = bytes.toString(Charsets.UTF_8)
    }

    private fun get(url: String, range: String? = null): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        range?.let { connection.setRequestProperty("Range", it) }
        return try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val headers = connection.headerFields.filterKeys { it != null }.mapValues { it.value.first() }
            Response(code, headers, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }
}
