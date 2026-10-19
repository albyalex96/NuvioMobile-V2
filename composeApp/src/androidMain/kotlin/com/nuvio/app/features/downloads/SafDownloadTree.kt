package com.nuvio.app.features.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException

internal data class SafDocument(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long?,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
}

/** Files directly inside a folder picked with the Storage Access Framework. */
internal class SafDownloadTree(private val context: Context, val treeUri: Uri) {
    private val resolver get() = context.contentResolver
    val rootDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)
    val rootUri: Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)

    fun children(): List<SafDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)
        val cursor = resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
            ?: throw FileNotFoundException("Downloads folder is not readable")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val documentId = it.getString(0) ?: continue
                    add(
                        SafDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            documentId = documentId,
                            name = it.getString(1).orEmpty(),
                            mimeType = it.getString(2),
                            sizeBytes = if (it.isNull(3)) 0L else it.getLong(3),
                            modifiedAtEpochMs = if (it.isNull(4)) null else it.getLong(4).takeIf { time -> time > 0L },
                        ),
                    )
                }
            }
        }
    }

    fun find(name: String): SafDocument? = children().firstOrNull { !it.isDirectory && it.name == name }

    fun createFile(name: String, mimeType: String = "application/octet-stream"): Uri =
        DocumentsContract.createDocument(resolver, rootUri, mimeType, name)
            ?: throw FileNotFoundException("Cannot create a file in the downloads folder")

    /** Renames [uri]; returns the URI of the renamed document (providers may change it). */
    fun rename(uri: Uri, name: String): Uri = DocumentsContract.renameDocument(resolver, uri, name) ?: uri

    fun displayName(): String? = runCatching {
        resolver.query(rootUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    companion object {
        private val CHILD_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        fun deleteDocument(context: Context, uri: Uri): Boolean =
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)

        /** The document behind [uri] still exists and is readable. */
        fun exists(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }
}
