package com.nuvio.app.core.mediaproxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisguisedMediaTest {

    private fun tsPackets(count: Int) = ByteArray(188 * count) { index -> if (index % 188 == 0) 0x47 else 0x11 }

    @Test
    fun findsTsAfterAFakePngHeader() {
        val fakeHeader = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(120)
        val start = findDisguisedMediaStart(fakeHeader + tsPackets(10))
        assertEquals(MediaStart(124, "video/mp2t"), start)
    }

    @Test
    fun plainTsStartsAtZero() {
        assertEquals(MediaStart(0, "video/mp2t"), findDisguisedMediaStart(tsPackets(5)))
    }

    @Test
    fun findsFmp4Box() {
        val box = byteArrayOf(0, 0, 0, 24) + "styp".toByteArray() + ByteArray(16)
        assertEquals(MediaStart(50, "video/mp4"), findDisguisedMediaStart(ByteArray(50) { 0x20 } + box))
    }

    @Test
    fun randomDataHasNoMedia() {
        assertNull(findDisguisedMediaStart(ByteArray(4096) { (it * 31 + 7).toByte() }))
        assertNull(findDisguisedMediaStart(ByteArray(16)))
    }

    @Test
    fun disguisedTypes() {
        assertTrue(isDisguisedMediaType("text/html; charset=utf-8"))
        assertTrue(isDisguisedMediaType("image/png"))
        assertTrue(isDisguisedMediaType(null))
        assertFalse(isDisguisedMediaType("video/mp2t"))
        assertFalse(isDisguisedMediaType("audio/aac"))
    }
}
