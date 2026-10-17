package com.nuvio.app.features.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CastSourceIssueTest {

    @Test
    fun remoteHttpLinksCanBeCast() {
        assertNull(castSourceIssue("https://cdn.example.com/movie.mkv"))
        assertNull(castSourceIssue("http://debrid.example.net:8080/dl/abc?token=1"))
    }

    @Test
    fun lanAddressesCanBeCast() {
        // The receiver sits on the same network, so a LAN server is reachable from it.
        assertNull(castSourceIssue("http://192.168.1.20:8096/videos/1/stream"))
    }

    @Test
    fun loopbackLinksAreLocalOnly() {
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("http://127.0.0.1:11470/abc/0"))
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("http://localhost:8080/stream"))
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("http://[::1]:8080/stream"))
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("http://user:pw@127.0.0.1/stream"))
    }

    @Test
    fun nonHttpSchemesAreLocalOnly() {
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("file:///storage/emulated/0/Movies/a.mp4"))
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("content://downloads/42"))
        assertEquals(CastSourceIssue.LocalOnly, castSourceIssue("/data/user/0/app/files/a.mp4"))
    }

    @Test
    fun separateAudioTrackCannotBeCast() {
        assertEquals(
            CastSourceIssue.SeparateAudio,
            castSourceIssue("https://cdn.example.com/video.mp4", sourceAudioUrl = "https://cdn.example.com/audio.m4a"),
        )
        assertNull(castSourceIssue("https://cdn.example.com/video.mp4", sourceAudioUrl = " "))
    }
}
