package com.nuvio.app.features.player.seekthumb

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A [RangeReader] that counts its traffic for the log and can be closed. */
internal interface CountingRangeReader : RangeReader, Closeable {
    val requests: Int
    val bytes: Long

    /** Cancels the reads under way; later reads still work. */
    fun cancel() = Unit
}

/** The server asked us to slow down (429/503). */
internal class RateLimitedException(val status: Int) : IOException("rate limited ($status)")

internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * Bounded Range reads over the playback OkHttp client, so previews reuse its keep-alive connections. Adapted from
 * Nuvio Arc (GPL-3.0) without its per-host connection ledger: one preview request runs at a time per stream. Never
 * open-ended and never above [ThumbReads.MAX_READ]; a 200 (Range ignored) is refused without reading the body. Never
 * log the URL or the headers.
 */
internal class HttpRangeReader(
    private val url: String,
    private val headers: Map<String, String>,
    private val client: OkHttpClient,
) : CountingRangeReader {
    private companion object {
        val RESOLVED_URL_FAILURES = setOf(401, 403, 404, 410)
    }

    @Volatile
    override var totalLength: Long = -1L
        private set

    private val inFlight: MutableSet<Call> = Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())

    @Volatile
    private var closed = false

    /** URL after redirects, so later requests skip the redirect round trip (0.25-0.7 s each on debrid links). */
    @Volatile
    private var effectiveUrl: String = url

    private val requestCount = AtomicInteger()
    private val byteCount = AtomicLong()

    override val requests: Int get() = requestCount.get()
    override val bytes: Long get() = byteCount.get()

    override fun cancel() {
        for (call in inFlight) call.cancel()
    }

    override fun close() {
        closed = true
        cancel()
    }

    override fun read(offset: Long, length: Int): ByteArray {
        val out = ByteArray(capped(length))
        val n = readInto(offset, out.size, ByteBuffer.wrap(out))
        return if (n == out.size) out else out.copyOf(n)
    }

    override fun readInto(offset: Long, length: Int, dst: ByteBuffer): Int {
        require(offset >= 0 && length > 0)
        val n = capped(minOf(length, dst.remaining()))
        if (n <= 0) return 0
        val target = effectiveUrl
        return try {
            readFrom(target, offset, n, dst)
        } catch (e: HttpStatusException) {
            // The redirect target may be short-lived: retry once from the original.
            if (target != url && e.code in RESOLVED_URL_FAILURES) {
                effectiveUrl = url
                readFrom(url, offset, n, dst)
            } else {
                throw e
            }
        }
    }

    /** A longer read is cut to the cap (callers treat it like a short read at end of file). */
    private fun capped(length: Int): Int = length.coerceAtMost(ThumbReads.MAX_READ)

    private fun readFrom(target: String, offset: Long, length: Int, dst: ByteBuffer): Int {
        if (closed) throw IOException("closed")
        val builder = Request.Builder().url(target)
        for ((k, v) in headers) {
            if (!k.equals("Range", ignoreCase = true)) builder.header(k, v)
        }
        val request = builder
            .header("Range", "bytes=$offset-${offset + length - 1}")
            .header("Accept-Encoding", "identity")
            .build()
        val call = client.newCall(request)
        inFlight.add(call)
        try {
            if (closed) call.cancel()
            requestCount.incrementAndGet()
            call.execute().use { resp ->
                when (resp.code) {
                    206 -> Unit
                    416 -> return 0
                    429, 503 -> throw RateLimitedException(resp.code)
                    200 -> throw UnsupportedMediaException("server ignored Range")
                    else -> throw HttpStatusException(resp.code)
                }
                resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { totalLength = it }
                val finalUrl = resp.request.url.toString()
                if (finalUrl != target) effectiveUrl = finalUrl
                val body = resp.body
                // Straight from OkHttp's segments into dst: no array of the whole range.
                val start = dst.position()
                val limit = dst.limit()
                dst.limit(start + length)
                try {
                    val source = body.source()
                    while (dst.hasRemaining()) {
                        if (source.read(dst) < 0) break
                    }
                } finally {
                    dst.limit(limit)
                }
                val n = dst.position() - start
                byteCount.addAndGet(n.toLong())
                return n
            }
        } finally {
            inFlight.remove(call)
        }
    }
}

/** Reads of a local file (`file://` or a `content://` document, such as an offline download). */
internal class FileRangeReader private constructor(
    private val channel: FileChannel,
    private val owner: Closeable,
) : CountingRangeReader {
    private val requestCount = AtomicInteger()
    private val byteCount = AtomicLong()

    override val requests: Int get() = requestCount.get()
    override val bytes: Long get() = byteCount.get()
    override val totalLength: Long = runCatching { channel.size() }.getOrDefault(-1L)

    override fun read(offset: Long, length: Int): ByteArray {
        val out = ByteArray(length.coerceAtMost(ThumbReads.MAX_READ))
        val n = readInto(offset, out.size, ByteBuffer.wrap(out))
        return if (n == out.size) out else out.copyOf(n)
    }

    override fun readInto(offset: Long, length: Int, dst: ByteBuffer): Int {
        val n = minOf(length, dst.remaining(), ThumbReads.MAX_READ)
        if (n <= 0) return 0
        requestCount.incrementAndGet()
        val start = dst.position()
        val limit = dst.limit()
        dst.limit(start + n)
        try {
            var at = offset
            while (dst.hasRemaining()) {
                val got = channel.read(dst, at)
                if (got <= 0) break
                at += got
            }
        } finally {
            dst.limit(limit)
        }
        val read = dst.position() - start
        byteCount.addAndGet(read.toLong())
        return read
    }

    override fun close() {
        runCatching { owner.close() }
    }

    companion object {
        /** Null when [url] is not a local file or cannot be opened. */
        fun open(context: Context, url: String): FileRangeReader? {
            val uri = Uri.parse(url)
            return when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let { path ->
                    val stream = runCatching { FileInputStream(File(path)) }.getOrNull() ?: return null
                    FileRangeReader(stream.channel, stream)
                }
                "content" -> {
                    val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
                        ?: return null
                    val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                    FileRangeReader(stream.channel, stream)
                }
                else -> null
            }
        }
    }
}
