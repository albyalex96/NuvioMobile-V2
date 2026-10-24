package com.nuvio.app.core.media

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberGalleryImageSaver(): GalleryImageSaver {
    val context = LocalContext.current
    return remember(context) {
        object : GalleryImageSaver {
            override suspend fun save(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, bytes, fileName, mimeType)
                } else {
                    // Before Android 10 writing to the shared Pictures folder needs a storage permission the app does not request.
                    shareImage(context, bytes, fileName, mimeType)
                }

            override suspend fun share(bytes: ByteArray, fileName: String, mimeType: String): GalleryImageSaveResult =
                shareImage(context, bytes, fileName, mimeType)
        }
    }
}

private suspend fun saveToMediaStore(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): GalleryImageSaveResult = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nuvio")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext GalleryImageSaveResult.FAILED
    runCatching {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("No output stream for $uri")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        GalleryImageSaveResult.SAVED
    }.getOrElse { error ->
        resolver.delete(uri, null, null)
        throw error
    }
}

private suspend fun shareImage(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): GalleryImageSaveResult {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "gallery_images")
        directory.mkdirs()
        // Keep only the latest image: older ones are useless once shared.
        directory.listFiles()?.forEach { it.delete() }
        File(directory, fileName).apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, fileName).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
    return GalleryImageSaveResult.SHARED
}
