package com.nuvio.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

// iOS uses the share sheet: it offers "Save Image" and Files without asking for the photo library permission.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberGalleryImageSaver(): GalleryImageSaver {
    val viewController = LocalUIViewController.current
    return remember(viewController) {
        object : GalleryImageSaver {
            override suspend fun save(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult =
                share(bytes, fileName, mimeType)

            override suspend fun share(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult {
                val path = writeTemporaryImage(bytes, fileName) ?: return GalleryImageSaveResult.FAILED
                presentShareSheet(viewController, path)
                return GalleryImageSaveResult.SHARED
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun presentShareSheet(viewController: UIViewController, path: String) {
    withContext(Dispatchers.Main) {
        val activity = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(path)),
            applicationActivities = null,
        )
        // iPad presents the sheet as a popover, which needs an anchor.
        activity.popoverPresentationController()?.let { popover ->
            popover.sourceView = viewController.view
            popover.sourceRect = viewController.view.bounds.useContents {
                CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
        }
        val presenter = viewController.presentedViewController ?: viewController
        presenter.presentViewController(activity, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun writeTemporaryImage(bytes: ByteArray, fileName: String): String? = withContext(Dispatchers.Default) {
    val directory = "${NSTemporaryDirectory().trimEnd('/')}/gallery_images"
    val manager = NSFileManager.defaultManager
    // Keep only the latest image: older ones are useless once shared.
    manager.removeItemAtPath(directory, null)
    if (!manager.createDirectoryAtPath(directory, true, null, null)) return@withContext null
    val path = "$directory/$fileName"
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    path.takeIf { data.writeToFile(it, true) }
}
