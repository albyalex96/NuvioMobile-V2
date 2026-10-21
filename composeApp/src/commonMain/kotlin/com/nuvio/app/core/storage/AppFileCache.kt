package com.nuvio.app.core.storage

internal expect object AppFileCache {
    fun read(path: String): String?
    fun write(path: String, payload: String)

    /** `file://` URI of [path], for players that load cached files themselves. */
    fun fileUri(path: String): String?
}
