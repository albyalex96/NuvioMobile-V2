package com.nuvio.app.core.media

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetBytesWithHeaders
import kotlinx.coroutines.CancellationException

enum class GalleryImageSaveResult {
    /** The image is in the device gallery (Android: Pictures/Nuvio). */
    SAVED,

    /** The system share sheet was opened (iOS "Save Image", Files, or older Android versions). */
    SHARED,
    FAILED,
}

/** Saves or shares downloaded image bytes with the platform mechanism; [fileName] includes the extension. */
interface GalleryImageSaver {
    suspend fun save(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult

    /** Opens the system share sheet with the image. */
    suspend fun share(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult
}

@Composable
expect fun rememberGalleryImageSaver(): GalleryImageSaver

private val log = Logger.withTag("GalleryImageSave")

/** Downloads [url] and saves it with [saver]. Never throws (except for cancellation). */
suspend fun downloadAndSaveGalleryImage(
    saver: GalleryImageSaver,
    url: String,
    fileNamePrefix: String = "nuvio",
): GalleryImageSaveResult = downloadGalleryImage(saver, url, fileNamePrefix, share = false)

/** Downloads [url] and opens the share sheet with it. Never throws (except for cancellation). */
suspend fun downloadAndShareGalleryImage(
    saver: GalleryImageSaver,
    url: String,
    fileNamePrefix: String = "nuvio",
): GalleryImageSaveResult = downloadGalleryImage(saver, url, fileNamePrefix, share = true)

private suspend fun downloadGalleryImage(
    saver: GalleryImageSaver,
    url: String,
    fileNamePrefix: String,
    share: Boolean,
): GalleryImageSaveResult {
    val fileName = galleryImageFileName(url, fileNamePrefix)
    val action = if (share) "Sharing" else "Saving"
    log.i { "$action image $fileName" }
    return try {
        val bytes = httpGetBytesWithHeaders(url)
        if (bytes.isEmpty()) {
            log.w { "Empty response for $url" }
            return GalleryImageSaveResult.FAILED
        }
        val mimeType = galleryImageMimeType(fileName)
        val result = if (share) saver.share(bytes, fileName, mimeType) else saver.save(bytes, fileName, mimeType)
        result.also {
            log.i { "$action image $fileName: $result (${bytes.size} bytes)" }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        log.e(error) { "$action image failed for $url" }
        GalleryImageSaveResult.FAILED
    }
}

internal fun galleryImageFileName(url: String, prefix: String): String {
    val lastSegment = url.substringBefore('?').substringAfterLast('/')
    val baseName = lastSegment.substringBeforeLast('.', lastSegment)
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .ifBlank { "image" }
    val extension = lastSegment.substringAfterLast('.', "").lowercase()
        .takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
        ?: "jpg"
    val safePrefix = prefix.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "nuvio" }
    return "${safePrefix}_$baseName.$extension"
}

internal fun galleryImageMimeType(fileName: String): String =
    when (fileName.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
