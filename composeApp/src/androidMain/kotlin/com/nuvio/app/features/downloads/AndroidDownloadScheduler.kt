package com.nuvio.app.features.downloads

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.NuvioDns
import com.nuvio.app.core.network.dns.DnsOverHttpsRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.nuvio.app.features.downloads.hls.HlsDownloadTask
import com.nuvio.app.features.downloads.hls.HlsOutputContainer
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

internal class AndroidDownloadScheduler(val context: Context) {
    val store = AndroidDownloadStore(File(context.filesDir, "download-transfers"))
    val directory = File(context.filesDir, "downloads")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(item: DownloadItem): AndroidDownloadTransfer {
        val previous = store.get(item.fileName)
        val transfer = store.begin(item)
        val existing = previous?.generation == transfer.generation
        if (existing && Build.VERSION.SDK_INT >= 34) return transfer
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                check(jobScheduler().schedule(buildJob(transfer)) == JobScheduler.RESULT_SUCCESS) {
                    "Android could not start the download. Open the app and try again."
                }
            } else {
                val work = OneTimeWorkRequestBuilder<DownloadsTransferWorker>()
                    .setInputData(workDataOf(FILE_NAME to item.fileName, GENERATION to transfer.generation))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                val operation = WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(transfer), if (existing) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, work,
                )
                operation.result.addListener({
                    try {
                        operation.result.get()
                    } catch (error: Exception) {
                        fail(transfer, error)
                        notifyCurrent(item.fileName)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (error: Exception) {
            fail(transfer, error)
        }
        return store.get(item.fileName) ?: transfer
    }

    @RequiresApi(34)
    internal fun buildJob(transfer: AndroidDownloadTransfer): JobInfo =
        JobInfo.Builder(transfer.jobId, ComponentName(context, DownloadsTransferJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setEstimatedNetworkBytes(transfer.item.totalBytes ?: JobInfo.NETWORK_BYTES_UNKNOWN.toLong(), 0L)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(PersistableBundle().apply {
                putString(FILE_NAME, transfer.item.fileName)
                putString(GENERATION, transfer.generation)
            })
            .build()

    @RequiresApi(34)
    private fun jobScheduler(): JobScheduler =
        context.getSystemService(JobScheduler::class.java).forNamespace(JOB_NAMESPACE)

    fun restore(item: DownloadItem): DownloadItem {
        var transfer = store.get(item.fileName)?.takeIf { it.item.id == item.id }
            ?: return if (item.status == DownloadStatus.Downloading) {
                item.copy(status = DownloadStatus.Paused, errorMessage = null)
            } else item
        if (transfer.item.status == DownloadStatus.Downloading && Build.VERSION.SDK_INT >= 34 &&
            jobScheduler().getPendingJob(transfer.jobId) == null) {
            pause(item.fileName)
            transfer = store.get(item.fileName) ?: transfer
        }
        return transfer.item
    }

    fun pause(fileName: String) {
        val transfer = store.get(fileName) ?: return
        if (transfer.item.status != DownloadStatus.Downloading) return
        val paused = store.update(fileName, transfer.generation) {
            it.copy(item = it.item.copy(status = DownloadStatus.Paused, errorMessage = null, conversionProgress = null))
        }
        cancelScheduled(transfer)
        paused?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    fun remove(fileName: String, storageTreeUri: String? = null) {
        val transfer = store.get(fileName)
        store.remove(fileName)
        transfer?.let(::cancelScheduled)
        transfer?.let { DownloadsLiveStatusPlatform.removeNotification(it.item.id) }
        val treeUri = transfer?.item?.storageTreeUri ?: storageTreeUri
        cleanupScope.launch {
            lock(fileName).withLock {
                if (store.get(fileName) == null) {
                    runCatching { destinationFor(fileName, treeUri).deletePartial() }
                    runCatching { hlsWorkDir(fileName).deleteRecursively() }
                    DownloadSubtitleStorage(subtitleAnchorUri(fileName)).remove()
                }
            }
        }
    }

    /** Subtitles always stay in the app storage, next to where an app-storage video would be. */
    fun subtitleAnchorUri(fileName: String): String = File(directory, fileName).toURI().toString()

    private fun destinationFor(fileName: String, storageTreeUri: String?): DownloadDestination =
        storageTreeUri?.let { TreeDownloadDestination(context, Uri.parse(it), fileName) }
            ?: FileDownloadDestination(directory, fileName)

    private fun cancelScheduled(transfer: AndroidDownloadTransfer) {
        if (Build.VERSION.SDK_INT >= 34) jobScheduler().cancel(transfer.jobId)
        else WorkManager.getInstance(context).cancelUniqueWork(workName(transfer))
    }

    suspend fun execute(
        transfer: AndroidDownloadTransfer,
        network: Network? = null,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): Boolean = lock(transfer.item.fileName).withLock {
        val fileName = transfer.item.fileName
        if (!isActive(transfer)) return@withLock false
        val destination = destinationFor(fileName, transfer.item.storageTreeUri)
        val client = if (network != null) {
            downloadHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                // The DNS over HTTPS option wins over the resolver of the bound network.
                .dns { host ->
                    if (DnsOverHttpsRepository.settings.value.isEnabled) NuvioDns.lookup(host)
                    else network.getAllByName(host).toList()
                }
                .connectionPool(ConnectionPool())
                .build()
        } else downloadHttpClient
        try {
            DownloadSubtitles.prepare(transfer.item, subtitleAnchorUri(fileName))
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            var lastProgressAt = 0L
            val existing = withContext(Dispatchers.IO) { destination.completedFile() }
            if (existing == null && transfer.item.hls != null) {
                val completed = executeHls(transfer, destination, client, onProgress)
                currentCoroutineContext().ensureActive()
                if (!isActive(transfer)) return@withLock false
                updateActive(transfer) { current ->
                    current.copy(item = current.item.copy(
                        status = DownloadStatus.Completed,
                        localFileUri = completed.uri,
                        downloadedBytes = completed.sizeBytes,
                        totalBytes = completed.sizeBytes,
                        conversionProgress = null,
                        errorMessage = null,
                    ))
                }
                return@withLock false
            }
            if (existing == null) transferAndroidDownload(
                item = transfer.item,
                destination = destination,
                validator = transfer.validator,
                client = client,
                onHeaders = { total, validator ->
                    updateActive(transfer) { it.copy(validator = validator, item = it.item.copy(totalBytes = total)) }
                },
                onProgress = { bytes, total ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressAt >= 1_000L || bytes == total) {
                        lastProgressAt = now
                        updateActive(transfer) {
                            it.copy(item = it.item.copy(downloadedBytes = bytes, totalBytes = total))
                        }?.let(onProgress)
                    }
                },
            )
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            val completed = existing ?: withContext(Dispatchers.IO) { destination.completePartial() }
            updateActive(transfer) { current ->
                val bytes = completed.sizeBytes
                current.copy(item = current.item.copy(
                    status = DownloadStatus.Completed,
                    localFileUri = completed.uri,
                    downloadedBytes = bytes,
                    totalBytes = bytes,
                    errorMessage = null,
                ))
            }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            val retry = shouldRetryAndroidDownload(error, transfer.retryCount)
            if (retry) updateActive(transfer) { it.copy(retryCount = it.retryCount + 1) }
            else fail(transfer, error)
            retry
        } finally {
            if (network != null) withContext(NonCancellable + Dispatchers.IO) { client.connectionPool.evictAll() }
        }
    }

    /** Downloads the HLS segments, merges them and moves the result into [destination]. */
    private suspend fun executeHls(
        transfer: AndroidDownloadTransfer,
        destination: DownloadDestination,
        client: OkHttpClient,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): CompletedDownloadFile {
        val item = transfer.item
        val baseName = item.fileName.substringBeforeLast('.')
        val otherNames = HlsOutputContainer.entries.map { "$baseName.${it.extension}" }
        // A previous run may have finished the file under the other container's name.
        withContext(Dispatchers.IO) { otherNames.firstNotNullOfOrNull { destination.completedFile(it) } }?.let { return it }

        val workDir = hlsWorkDir(item.fileName)
        var lastUpdateAt = 0L
        fun throttled(force: Boolean, update: (AndroidDownloadTransfer) -> AndroidDownloadTransfer) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastUpdateAt < 1_000L) return
            lastUpdateAt = now
            updateActive(transfer, update)?.let(onProgress)
        }
        val result = HlsDownloadTask(item, workDir, client).run(
            onProgress = { bytes, total ->
                throttled(force = false) {
                    it.copy(item = it.item.copy(downloadedBytes = bytes, totalBytes = total, conversionProgress = null))
                }
            },
            onConverting = { fraction ->
                throttled(force = fraction <= 0f || fraction >= 1f) {
                    it.copy(item = it.item.copy(conversionProgress = fraction))
                }
            },
        )
        val finalName = "$baseName.${result.container.extension}"
        if (finalName != item.fileName) Logger.withTag("HlsDownload").i { "Saved as ${result.container} instead of the predicted format" }
        val completed = withContext(Dispatchers.IO) { destination.adoptFile(result.file, finalName) }
        // MP4 cannot hold the subtitles: they go next to the video, like the addon subtitles.
        result.sidecarSubtitles.forEach { (track, text) ->
            runCatching {
                DownloadSubtitles.saveStreamTrack(
                    anchorUri = subtitleAnchorUri(item.fileName),
                    sourceKey = "hls:${track.playlistUrl}",
                    text = text,
                    extension = "srt",
                    language = track.language ?: "und",
                    name = track.name ?: track.label,
                )
            }.onFailure { error -> Logger.withTag("HlsDownload").w(error) { "Could not save the subtitle ${track.label}" } }
        }
        withContext(Dispatchers.IO) { workDir.deleteRecursively() }
        Logger.withTag("HlsDownload").i { "HLS download ${item.id} completed (${completed.sizeBytes} bytes, ${result.container})" }
        return completed
    }

    /** Segments and state of an HLS download while it is in progress. */
    private fun hlsWorkDir(fileName: String): File = File(File(context.filesDir, "download-hls"), fileName.substringBeforeLast('.'))

    fun isActive(transfer: AndroidDownloadTransfer): Boolean = store.get(transfer.item.fileName)?.let {
        it.generation == transfer.generation && it.item.status == DownloadStatus.Downloading
    } == true

    fun fail(transfer: AndroidDownloadTransfer, error: Exception) {
        val message = if (error is SecurityException && transfer.item.storageTreeUri != null) {
            "The downloads folder is no longer accessible. Pick it again in Settings → Downloads."
        } else {
            error.message ?: "Download failed"
        }
        Logger.withTag("Downloads").e(error) { "Download ${transfer.item.id} failed" }
        updateActive(transfer) {
            it.copy(item = it.item.copy(status = DownloadStatus.Failed, errorMessage = message, conversionProgress = null))
        }
    }

    fun notifyCurrent(fileName: String) {
        store.get(fileName)?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    private fun updateActive(
        transfer: AndroidDownloadTransfer,
        update: (AndroidDownloadTransfer) -> AndroidDownloadTransfer,
    ): AndroidDownloadTransfer? = store.update(transfer.item.fileName, transfer.generation) {
        if (it.item.status == DownloadStatus.Downloading) {
            update(it).let { changed -> changed.copy(item = changed.item.copy(updatedAtEpochMs = System.currentTimeMillis())) }
        } else it
    }

    private fun lock(fileName: String): Mutex = locks.getOrPut(fileName) { Mutex() }
    private fun workName(transfer: AndroidDownloadTransfer) = "nuvio-download-${transfer.jobId}"

    companion object {
        const val FILE_NAME = "download_file_name"
        const val GENERATION = "download_generation"
        const val JOB_NAMESPACE = "nuvio-downloads"
    }
}

internal fun shouldRetryAndroidDownload(error: Exception, retries: Int): Boolean =
    retries < 4 && when (error) {
        is DownloadHttpException -> error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }
