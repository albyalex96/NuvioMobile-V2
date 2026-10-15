package com.nuvio.app.core.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeAppLogExportFile(fileName: String, text: String): String? {
    val directory = "${NSTemporaryDirectory().trimEnd('/')}/app_logs"
    val manager = NSFileManager.defaultManager
    // Keep only the latest export: older ones are useless once shared.
    manager.removeItemAtPath(directory, null)
    if (!manager.createDirectoryAtPath(directory, true, null, null)) return null
    val path = "$directory/$fileName"
    return path.takeIf { NSString.create(string = text).writeToFile(it, true, NSUTF8StringEncoding, null) }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberAppLogFileSharer(): (path: String) -> Unit {
    val viewController = LocalUIViewController.current
    return remember(viewController) {
        { path ->
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
            viewController.presentViewController(activity, animated = true, completion = null)
        }
    }
}
