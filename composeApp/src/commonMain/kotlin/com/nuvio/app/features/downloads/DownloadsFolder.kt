package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import com.nuvio.app.features.streams.StreamSubtitle
import kotlinx.coroutines.flow.StateFlow

/** Folder picked by the user to save new downloads in (Android: a SAF tree). */
data class DownloadsFolder(
    val treeUri: String,
    val displayName: String,
)

/** Free and total space of the disk that holds the downloads folder. */
data class DownloadsStorageStats(
    val freeBytes: Long,
    val totalBytes: Long,
)

/** A file found in the downloads folder by [DownloadsFolderPlatform.listFolderFiles]. */
data class DownloadsFolderEntry(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long?,
)

enum class ExternalVideoOrigin {
    /** Not saved by the app (copied there by the user or another app). */
    External,

    /** Saved by the app for another profile: its download list belongs to that profile. */
    OtherProfile,
}

/** A video in the downloads folder that is not in the current profile's download list. */
data class ExternalVideoFile(
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long?,
    val origin: ExternalVideoOrigin,
    val subtitles: List<StreamSubtitle> = emptyList(),
) {
    /** Name without the extension, shown as the title. */
    val displayName: String
        get() = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
}

internal expect object DownloadsFolderPlatform {
    /** The platform lets the user pick a folder (Android with a document tree picker). */
    val isFolderPickerSupported: Boolean

    val selectedFolder: StateFlow<DownloadsFolder?>

    /** Stores the folder returned by the picker (taking the persistable permission). */
    fun selectFolder(treeUri: String): Boolean

    /** Goes back to the app's private storage for new downloads. */
    fun clearFolder()

    /** The app can still read and write [treeUri] (permission granted and folder present). */
    fun isFolderAccessible(treeUri: String): Boolean

    /**
     * Tree URI for a new download: the selected folder when it is accessible, null (app storage)
     * otherwise.
     */
    fun treeUriForNewDownload(): String?

    /** Space of the disk holding [treeUri] (app storage when null). */
    fun storageStats(treeUri: String?): DownloadsStorageStats?

    /** Files directly inside [treeUri]; empty when the folder cannot be read. */
    fun listFolderFiles(treeUri: String): List<DownloadsFolderEntry>

    /** Opens a file manager on [treeUri] (app storage when null). */
    fun openFolder(treeUri: String?): Boolean

    fun shareFile(fileUri: String, fileName: String): Boolean

    /** Deletes a file found by [listFolderFiles]. */
    fun deleteFolderFile(fileUri: String): Boolean

    /** Readable location of a file (path inside the folder or the app storage). */
    fun describeLocation(treeUri: String?, fileName: String): String
}

/** Returns a launcher for the folder picker, or null when the platform has none. */
@Composable
internal expect fun rememberDownloadsFolderPicker(onFolderPicked: (treeUri: String) -> Unit): (() -> Unit)?

internal val DOWNLOAD_VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "webm", "mov", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg")
internal val DOWNLOAD_SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

// Downloads are named "<title>_<id>.<ext>", the id being "<epoch ms base 36>_<ordinal base 36>".
private val APP_DOWNLOAD_FILE_NAME = Regex("_[0-9a-z]{7,9}_[0-9a-z]{1,6}\\.[0-9a-z]{2,5}$")

/** The file name has the shape of a file saved by the app ([DownloadsRepository] naming). */
internal fun isAppDownloadFileName(fileName: String): Boolean =
    APP_DOWNLOAD_FILE_NAME.containsMatchIn(fileName)

private val String.extension: String
    get() = substringAfterLast('.', missingDelimiterValue = "").lowercase()

/**
 * Videos of [entries] that are not in [knownItems]: files saved for this profile are matched by
 * URI or by file name, partial files and subtitle folders are skipped. Subtitles with the same
 * base name ("Movie.srt", "Movie.it.srt") are attached to their video.
 */
internal fun findExternalVideos(
    entries: List<DownloadsFolderEntry>,
    knownItems: List<DownloadItem>,
): List<ExternalVideoFile> {
    val knownUris = knownItems.mapNotNullTo(mutableSetOf()) { it.localFileUri }
    val knownNames = knownItems.mapTo(mutableSetOf()) { it.fileName }
    val subtitles = entries.filter { it.name.extension in DOWNLOAD_SUBTITLE_EXTENSIONS }
    return entries
        .asSequence()
        .filter { it.name.extension in DOWNLOAD_VIDEO_EXTENSIONS }
        .filterNot { it.uri in knownUris || it.name in knownNames }
        .map { entry ->
            ExternalVideoFile(
                uri = entry.uri,
                fileName = entry.name,
                sizeBytes = entry.sizeBytes,
                modifiedAtEpochMs = entry.modifiedAtEpochMs,
                origin = if (isAppDownloadFileName(entry.name)) ExternalVideoOrigin.OtherProfile else ExternalVideoOrigin.External,
                subtitles = sidecarSubtitles(entry.name, subtitles),
            )
        }
        .sortedByDescending { it.modifiedAtEpochMs ?: 0L }
        .toList()
}

internal fun sidecarSubtitles(videoName: String, subtitles: List<DownloadsFolderEntry>): List<StreamSubtitle> {
    val base = videoName.substringBeforeLast('.', missingDelimiterValue = videoName)
    return subtitles.mapNotNull { subtitle ->
        val subtitleBase = subtitle.name.substringBeforeLast('.', missingDelimiterValue = subtitle.name)
        val language = when {
            subtitleBase == base -> "und"
            subtitleBase.startsWith("$base.") -> subtitleBase.removePrefix("$base.").takeIf { it.isNotBlank() } ?: "und"
            else -> return@mapNotNull null
        }
        StreamSubtitle(url = subtitle.uri, language = language, name = subtitle.name)
    }
}
