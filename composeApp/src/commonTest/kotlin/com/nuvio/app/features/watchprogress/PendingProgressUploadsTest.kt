package com.nuvio.app.features.watchprogress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingProgressUploadsTest {

    @Test
    fun `only dirty entries are pending`() {
        val dirty = progressEntry(parentMetaId = "tt1")
        val synced = progressEntry(parentMetaId = "tt2")

        val pending = pendingProgressUploads(
            entries = listOf(dirty, synced),
            dirtyProgressKeys = setOf(dirty.resolvedProgressKey()),
        )

        assertEquals(listOf(dirty), pending)
    }

    @Test
    fun `media server items are never pending`() {
        val serverItem = progressEntry(parentMetaId = "srv1:abc")

        val pending = pendingProgressUploads(
            entries = listOf(serverItem),
            dirtyProgressKeys = setOf(serverItem.resolvedProgressKey()),
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `nothing is pending without dirty keys`() {
        val pending = pendingProgressUploads(
            entries = listOf(progressEntry(parentMetaId = "tt1")),
            dirtyProgressKeys = emptySet(),
        )

        assertTrue(pending.isEmpty())
    }

    private fun progressEntry(parentMetaId: String) = WatchProgressEntry(
        contentType = "series",
        parentMetaId = parentMetaId,
        parentMetaType = "series",
        videoId = "$parentMetaId:1:1",
        title = "Episode",
        seasonNumber = 1,
        episodeNumber = 1,
        lastPositionMs = 30_000L,
        durationMs = 60_000L,
        lastUpdatedEpochMs = 1_000L,
    )
}
