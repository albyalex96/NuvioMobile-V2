package com.nuvio.app.features.downloads

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

internal data class CompletedDownloadFile(val uri: String, val sizeBytes: Long)

/**
 * Where a transfer writes its bytes: "<name>.part" while downloading, renamed to "<name>" at
 * the end. Either the app's private folder or a folder picked by the user.
 */
internal interface DownloadDestination {
    /** Bytes already in the partial file, 0 when there is none. */
    fun partialLength(): Long

    /** Opens the partial file keeping its first [keepBytes] bytes (0 starts over) and writing after them. */
    fun openPartial(keepBytes: Long): FileOutputStream

    /** The final file, when the download already completed. */
    fun completedFile(): CompletedDownloadFile?

    /** Renames the partial file to the final name. */
    fun completePartial(): CompletedDownloadFile

    fun deletePartial()

    /** The final file saved under another name ([name], e.g. an HLS download that became MKV). */
    fun completedFile(name: String): CompletedDownloadFile?

    /** Moves (or copies) the finished [source] file here as [name], replacing a file with that name. */
    fun adoptFile(source: File, name: String): CompletedDownloadFile
}

internal class FileDownloadDestination(
    private val directory: File,
    fileName: String,
) : DownloadDestination {
    val partialFile = File(directory, "$fileName.part")
    private val finalFile = File(directory, fileName)

    override fun partialLength(): Long = partialFile.takeIf(File::isFile)?.length() ?: 0L

    override fun openPartial(keepBytes: Long): FileOutputStream {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create downloads directory" }
        return FileOutputStream(partialFile, keepBytes > 0L)
    }

    override fun completedFile(): CompletedDownloadFile? =
        finalFile.takeIf(File::isFile)?.let { CompletedDownloadFile(it.toURI().toString(), it.length()) }

    override fun completePartial(): CompletedDownloadFile {
        if (!partialFile.renameTo(finalFile)) throw IOException("Could not finalize the downloaded file")
        return CompletedDownloadFile(finalFile.toURI().toString(), finalFile.length())
    }

    override fun deletePartial() {
        partialFile.delete()
    }

    override fun completedFile(name: String): CompletedDownloadFile? =
        File(directory, name).takeIf(File::isFile)?.let { CompletedDownloadFile(it.toURI().toString(), it.length()) }

    override fun adoptFile(source: File, name: String): CompletedDownloadFile {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create downloads directory" }
        val target = File(directory, name)
        target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return CompletedDownloadFile(target.toURI().toString(), target.length())
    }
}

/** A folder picked through the Storage Access Framework; files are written through the provider. */
internal class TreeDownloadDestination(
    private val context: Context,
    treeUri: Uri,
    private val fileName: String,
) : DownloadDestination {
    private val tree = SafDownloadTree(context, treeUri)
    private val partialName = "$fileName.part"

    override fun partialLength(): Long = tree.find(partialName)?.sizeBytes ?: 0L

    override fun openPartial(keepBytes: Long): FileOutputStream {
        val uri = tree.find(partialName)?.uri ?: tree.createFile(partialName)
        // "rw" + explicit truncate: "w" does not truncate on every provider and "wa" is not always supported.
        val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw FileNotFoundException("Cannot open the partial download file")
        val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
        try {
            output.channel.truncate(keepBytes)
            output.channel.position(keepBytes)
        } catch (error: IOException) {
            output.close()
            throw error
        }
        return output
    }

    override fun completedFile(): CompletedDownloadFile? =
        tree.find(fileName)?.let { CompletedDownloadFile(it.uri.toString(), it.sizeBytes) }

    override fun completePartial(): CompletedDownloadFile {
        val partial = tree.find(partialName) ?: throw IOException("Could not finalize the downloaded file")
        val renamed = try {
            tree.rename(partial.uri, fileName)
        } catch (error: Exception) {
            throw IOException("Could not finalize the downloaded file", error)
        }
        return CompletedDownloadFile(renamed.toString(), partial.sizeBytes)
    }

    override fun deletePartial() {
        tree.find(partialName)?.let { SafDownloadTree.deleteDocument(context, it.uri) }
    }

    override fun completedFile(name: String): CompletedDownloadFile? =
        tree.find(name)?.let { CompletedDownloadFile(it.uri.toString(), it.sizeBytes) }

    override fun adoptFile(source: File, name: String): CompletedDownloadFile {
        val partial = "$name.part"
        val uri = tree.find(partial)?.uri ?: tree.createFile(partial)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw FileNotFoundException("Cannot open the download file")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            output.channel.truncate(0L)
            source.inputStream().use { input -> input.copyTo(output, 1024 * 1024) }
            output.fd.sync()
        }
        tree.find(name)?.let { SafDownloadTree.deleteDocument(context, it.uri) }
        val renamed = try {
            tree.rename(uri, name)
        } catch (error: Exception) {
            throw IOException("Could not finalize the downloaded file", error)
        }
        val size = source.length()
        source.delete()
        return CompletedDownloadFile(renamed.toString(), size)
    }
}
