package com.nuvio.app.core.media

import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryImageSaverTest {
    @Test
    fun fileNameKeepsTheTmdbPathAndExtension() {
        assertEquals(
            "nuvio_abc123.png",
            galleryImageFileName("https://image.tmdb.org/t/p/original/abc123.png?x=1", "nuvio"),
        )
        assertEquals("nuvio_image.jpg", galleryImageFileName("https://example.com/", "nuvio"))
        assertEquals("nuvio_poster.jpg", galleryImageFileName("https://example.com/poster.gif", "../"))
    }

    @Test
    fun mimeTypeFollowsTheExtension() {
        assertEquals("image/png", galleryImageMimeType("a.png"))
        assertEquals("image/jpeg", galleryImageMimeType("a.jpg"))
    }
}
