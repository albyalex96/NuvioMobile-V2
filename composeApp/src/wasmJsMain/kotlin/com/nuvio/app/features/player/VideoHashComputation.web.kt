package com.nuvio.app.features.player

internal actual suspend fun computeVideoHash(
    url: String,
    headers: Map<String, String>,
): Pair<String, Long>? = null // Not implemented on this platform.
