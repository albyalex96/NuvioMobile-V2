package com.nuvio.app.features.downloads

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import android.system.Os
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal actual object DownloadsFolderPlatform {
    private const val PREFERENCES_NAME = "nuvio_downloads_folder"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    private val log = Logger.withTag("Downloads")
    private var appContext: Context? = null
    private var preferences: SharedPreferences? = null
    private val folder = MutableStateFlow<DownloadsFolder?>(null)

    actual val selectedFolder: StateFlow<DownloadsFolder?> = folder.asStateFlow()

    actual val isFolderPickerSupported: Boolean
        get() {
            val context = appContext ?: return false
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(context.packageManager) != null
        }

    fun initialize(context: Context) {
        if (preferences != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences = prefs
        val treeUri = prefs.getString(KEY_TREE_URI, null)
        folder.value = treeUri?.let { DownloadsFolder(it, prefs.getString(KEY_DISPLAY_NAME, null) ?: it) }
    }

    actual fun selectFolder(treeUri: String): Boolean {
        val context = appContext ?: return false
        val uri = Uri.parse(treeUri)
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            // Permissions of the previous folder are kept: its downloads stay where they are.
            val selected = DownloadsFolder(treeUri, folderLabel(context, uri))
            preferences?.edit()
                ?.putString(KEY_TREE_URI, selected.treeUri)
                ?.putString(KEY_DISPLAY_NAME, selected.displayName)
                ?.apply()
            folder.value = selected
            log.i { "Downloads folder selected: ${selected.displayName}" }
            true
        } catch (error: Exception) {
            log.e(error) { "Cannot use the picked downloads folder" }
            false
        }
    }

    actual fun clearFolder() {
        preferences?.edit()?.remove(KEY_TREE_URI)?.remove(KEY_DISPLAY_NAME)?.apply()
        folder.value = null
        log.i { "Downloads folder reset to app storage" }
    }

    actual fun isFolderAccessible(treeUri: String): Boolean {
        val context = appContext ?: return false
        val uri = Uri.parse(treeUri)
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return granted && runCatching {
            SafDownloadTree.exists(context, SafDownloadTree(context, uri).rootUri)
        }.getOrDefault(false)
    }

    actual fun treeUriForNewDownload(): String? {
        val selected = folder.value ?: return null
        if (isFolderAccessible(selected.treeUri)) return selected.treeUri
        log.w { "Downloads folder ${selected.displayName} is not accessible: saving in app storage" }
        return null
    }

    actual fun storageStats(treeUri: String?): DownloadsStorageStats? {
        val context = appContext ?: return null
        if (treeUri == null) {
            return runCatching {
                val stat = StatFs(context.filesDir.path)
                DownloadsStorageStats(stat.availableBytes, stat.totalBytes)
            }.getOrNull()
        }
        val uri = Uri.parse(treeUri)
        return fileSystemStats(context, uri) ?: rootStats(context, uri)
    }

    // statvfs of any file of the folder gives the exact numbers of its disk.
    private fun fileSystemStats(context: Context, treeUri: Uri): DownloadsStorageStats? = runCatching {
        val file = SafDownloadTree(context, treeUri).children().firstOrNull { !it.isDirectory } ?: return null
        context.contentResolver.openFileDescriptor(file.uri, "r")?.use { descriptor ->
            val stat = Os.fstatvfs(descriptor.fileDescriptor)
            DownloadsStorageStats(stat.f_bavail * stat.f_frsize, stat.f_blocks * stat.f_frsize)
        }
    }.getOrNull()

    // Without files in the folder, ask the provider's root that contains it.
    private fun rootStats(context: Context, treeUri: Uri): DownloadsStorageStats? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootsUri = DocumentsContract.buildRootsUri(treeUri.authority)
        val projection = arrayOf(
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            "capacity_bytes", // Root.COLUMN_CAPACITY_BYTES, API 29
        )
        context.contentResolver.query(rootsUri, projection, null, null, null)?.use { cursor ->
            var best: DownloadsStorageStats? = null
            var bestLength = -1
            while (cursor.moveToNext()) {
                val rootDocumentId = cursor.getString(0) ?: continue
                if (!documentId.startsWith(rootDocumentId) || rootDocumentId.length <= bestLength) continue
                val free = if (cursor.isNull(1)) continue else cursor.getLong(1)
                val total = cursor.getColumnIndex("capacity_bytes")
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong)
                    ?: free
                best = DownloadsStorageStats(free, total)
                bestLength = rootDocumentId.length
            }
            best
        }
    }.getOrNull()

    actual fun listFolderFiles(treeUri: String): List<DownloadsFolderEntry> {
        val context = appContext ?: return emptyList()
        return try {
            SafDownloadTree(context, Uri.parse(treeUri)).children()
                .filterNot { it.isDirectory }
                .map { DownloadsFolderEntry(it.uri.toString(), it.name, it.sizeBytes, it.modifiedAtEpochMs) }
        } catch (error: Exception) {
            log.w(error) { "Cannot read the downloads folder" }
            emptyList()
        }
    }

    actual fun openFolder(treeUri: String?): Boolean {
        val context = appContext ?: return false
        if (treeUri == null) return DownloadsPlatformDownloader.openAppDownloadsDirectory(context)
        val tree = Uri.parse(treeUri)
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return false
        val candidates = listOf(
            DocumentsContract.buildDocumentUri(tree.authority, documentId),
            DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
        )
        val opened = candidates.any { uri ->
            listOf(DocumentsContract.Document.MIME_TYPE_DIR, "resource/folder").any { type ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, type)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                    true
                }.getOrDefault(false)
            }
        }
        if (!opened) log.w { "No file manager could open the downloads folder" }
        return opened
    }

    actual fun shareFile(fileUri: String, fileName: String): Boolean {
        val context = appContext ?: return false
        val uri = if (fileUri.isContentUri()) {
            Uri.parse(fileUri)
        } else {
            val file = runCatching { File(URI(fileUri)) }.getOrNull() ?: return false
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull() ?: return false
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(videoMimeType(fileName))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        return runCatching {
            context.startActivity(
                Intent.createChooser(send, fileName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            true
        }.onFailure { log.w(it) { "Cannot share a downloaded file" } }.getOrDefault(false)
    }

    actual fun deleteFolderFile(fileUri: String): Boolean {
        val context = appContext ?: return false
        val deleted = SafDownloadTree.deleteDocument(context, Uri.parse(fileUri))
        log.i { "Delete a file of the downloads folder: deleted=$deleted" }
        return deleted
    }

    actual fun describeLocation(treeUri: String?, fileName: String): String {
        val context = appContext ?: return fileName
        if (treeUri == null) return File(File(context.filesDir, "downloads"), fileName).absolutePath
        val tree = Uri.parse(treeUri)
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return fileName
        if (tree.authority == EXTERNAL_STORAGE_AUTHORITY) {
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':', missingDelimiterValue = "").trim('/')
            val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
            return listOf(base, path, fileName).filter { it.isNotEmpty() }.joinToString("/")
        }
        return "${folderLabel(context, tree)}/$fileName"
    }

    /** "Download/Nuvio" for folders of the device storage, the provider's name otherwise. */
    private fun folderLabel(context: Context, treeUri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (treeUri.authority == EXTERNAL_STORAGE_AUTHORITY && documentId != null) {
            val path = documentId.substringAfter(':', missingDelimiterValue = "").trim('/')
            if (path.isNotEmpty()) return path
        }
        return runCatching { SafDownloadTree(context, treeUri).displayName() }.getOrNull()
            ?: documentId
            ?: treeUri.toString()
    }
}

internal fun videoMimeType(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        else -> "video/*"
    }

@Composable
internal actual fun rememberDownloadsFolderPicker(onFolderPicked: (treeUri: String) -> Unit): (() -> Unit)? {
    val currentOnFolderPicked = rememberUpdatedState(onFolderPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) currentOnFolderPicked.value(uri.toString())
    }
    if (!DownloadsFolderPlatform.isFolderPickerSupported) return null
    return remember(launcher) {
        {
            val initial = DownloadsFolderPlatform.selectedFolder.value?.treeUri?.let(Uri::parse)
            runCatching { launcher.launch(initial) }
                .onFailure { Logger.withTag("Downloads").w(it) { "Folder picker could not be opened" } }
        }
    }
}
