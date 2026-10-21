package com.nuvio.app.features.subdl

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubDlZipTest {
    private fun zip(vararg files: Pair<String, String>, stored: Boolean = false): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, text) ->
                val bytes = text.encodeToByteArray()
                val entry = ZipEntry(name)
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.setComment("SubDL")
        }
        return output.toByteArray()
    }

    @Test
    fun readsDeflatedEntries() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nCiao, perché no?\n".repeat(200)
        val entries = readZipEntries(zip("folder/" to "", "folder/Show.S01E02.srt" to text, "info.nfo" to "x"))
        assertEquals(listOf("folder/Show.S01E02.srt", "info.nfo"), entries.map { it.name })
        assertEquals(text, entries.first().bytes.decodeToString())
    }

    @Test
    fun readsStoredEntriesAndFilters() {
        val entries = readZipEntries(
            zip("a.srt" to "uno", "b.txt" to "due", stored = true),
            accept = { it.endsWith(".srt") },
        )
        assertEquals(listOf("a.srt"), entries.map { it.name })
        assertEquals("uno", entries.single().bytes.decodeToString())
    }

    @Test
    fun brokenArchiveGivesNothing() {
        assertTrue(readZipEntries("not a zip".encodeToByteArray()).isEmpty())
        assertTrue(readZipEntries(zip("a.srt" to "uno").copyOf(20)).isEmpty())
    }
}
