package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * "Watch privately" for a single movie or episode, chosen from the stream menu. The request is
 * attached to the next player launch of the same content ([applyTo], called where launches are
 * registered or handed to an external player), so every launch path (debrid, P2P, server streams…)
 * honors it without carrying a flag through each of them.
 */
object PrivatePlaybackRequests {
    private val log = Logger.withTag("PrivateViewing")

    /** A request that never turned into a launch (e.g. the source failed) expires on its own. */
    private val requestLifetime = 10.minutes

    private class Pending(val key: String, val mark: TimeSource.Monotonic.ValueTimeMark)

    private var pending: Pending? = null

    fun request(parentMetaId: String, videoId: String?) {
        pending = Pending(contentKey(parentMetaId, videoId), TimeSource.Monotonic.markNow())
        log.i { "Private playback requested for the next launch" }
    }

    fun clear() {
        if (pending != null) log.d { "Private playback request dropped" }
        pending = null
    }

    /** Marks [launch] as private when a pending request targets the same content, consuming it. */
    fun applyTo(launch: PlayerLaunch): PlayerLaunch {
        val request = pending ?: return launch
        if (request.mark.elapsedNow() > requestLifetime) {
            pending = null
            return launch
        }
        if (request.key != contentKey(launch.parentMetaId, launch.videoId)) return launch
        pending = null
        log.i { "Private playback applied to the launch" }
        return launch.copy(privateViewing = true)
    }

    internal fun contentKey(parentMetaId: String, videoId: String?): String =
        "$parentMetaId|${videoId ?: parentMetaId}"
}
