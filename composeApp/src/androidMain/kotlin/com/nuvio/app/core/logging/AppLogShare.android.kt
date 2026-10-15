package com.nuvio.app.core.logging

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

private var exportDirectory: File? = null

internal fun initializeAppLogExportDirectory(context: Context) {
    exportDirectory = File(context.cacheDir, "app_logs")
}

internal actual fun writeAppLogExportFile(fileName: String, text: String): String? = runCatching {
    val directory = requireNotNull(exportDirectory)
    directory.mkdirs()
    // Keep only the latest export: older ones are useless once shared.
    directory.listFiles()?.forEach { it.delete() }
    File(directory, fileName).apply { writeText(text) }.absolutePath
}.getOrNull()

@Composable
internal actual fun rememberAppLogFileSharer(): (path: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { path ->
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, file.name).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
