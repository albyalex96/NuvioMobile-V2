package com.nuvio.app.features.downloads

internal data class DownloadPlatformRequest(
    val item: DownloadItem,
) {
    val sourceUrl: String get() = item.sourceUrl
    val sourceHeaders: Map<String, String> get() = item.sourceHeaders
    val destinationFileName: String get() = item.fileName
}

internal interface DownloadsTaskHandle {
    fun cancel()
}

internal expect object DownloadsPlatformDownloader {
    fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
        onPaused: () -> Unit,
    ): DownloadsTaskHandle

    fun restoreItem(item: DownloadItem): DownloadItem

    fun removeFile(localFileUri: String?): Boolean

    fun removePartialFile(item: DownloadItem): Boolean

    fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String?

    /**
     * Video URI the subtitles of [item] are stored next to. It differs from [localFileUri] when
     * the video is in a user folder: subtitles always stay in the app storage.
     */
    fun subtitleAnchorUri(item: DownloadItem, localFileUri: String): String

    fun openDownloadsDirectory(): Boolean
}
