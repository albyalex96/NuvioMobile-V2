package com.nuvio.app.features.player.seekthumb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The per-device options survive storage and sync as JSON, and a bad value falls back to the defaults. */
class SeekThumbnailOptionsTest {

    @Test
    fun optionsRoundTrip() {
        val options = SeekThumbnailOptions(enabled = false, wifiOnly = false, size = SeekThumbnailSize.Large)
        assertEquals(options, SeekThumbnailOptions.decode(options.encode()))
    }

    @Test
    fun missingFieldsTakeTheDefaults() {
        assertEquals(SeekThumbnailOptions(size = SeekThumbnailSize.Small), SeekThumbnailOptions.decode("""{"size":"Small"}"""))
    }

    @Test
    fun brokenValuesAreIgnored() {
        assertNull(SeekThumbnailOptions.decode(null))
        assertNull(SeekThumbnailOptions.decode(""))
        assertNull(SeekThumbnailOptions.decode("""{"size":"Huge"}"""))
    }

    @Test
    fun everyDeviceStartsWithTheDefaults() {
        val defaults = defaultSeekThumbnailOptions()
        assertEquals(SeekThumbnailDevice.entries.toSet(), defaults.keys)
        defaults.values.forEach { assertEquals(SeekThumbnailOptions(), it) }
    }
}
