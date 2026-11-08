package com.nuvio.app.features.livetv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import com.nuvio.app.features.downloads.SafDownloadTree
import com.nuvio.app.features.settings.AppIconPlatform
import com.nuvio.app.features.settings.ThemeSettingsStorage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_recording_channel_name
import nuvio.composeapp.generated.resources.live_tv_recording_notification_failed
import nuvio.composeapp.generated.resources.live_tv_recording_notification_finished
import nuvio.composeapp.generated.resources.live_tv_recording_notification_progress
import nuvio.composeapp.generated.resources.live_tv_recording_notification_stop
import nuvio.composeapp.generated.resources.live_tv_recording_notification_title
import org.jetbrains.compose.resources.getString

/**
 * Foreground service that runs the recordings (several at once if they overlap). It keeps a
 * partial wake lock while recording, so the stream keeps flowing with the screen off.
 */
class LiveTvRecordingService : Service() {
    private val log = Logger.withTag("LiveTvRecording")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val recorders = ConcurrentHashMap<String, LiveTvStreamRecorder>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LiveTvRecordingPlatform.initialize(this)
        ThemeSettingsStorage.initialize(applicationContext)
        LiveTvStorage.initialize(applicationContext)
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_STOP -> {
                if (id != null) stopRecording(id)
                stopIfIdle()
            }
            ACTION_START -> {
                // Foreground at once: Android kills a service that doesn't within a few seconds.
                goForeground()
                if (id != null) startRecording(id) else stopIfIdle()
            }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val notification = progressNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nuvio:LiveTvRecording")
                ?.apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) }
        }
    }

    private fun startRecording(id: String) {
        if (jobs.containsKey(id)) return
        val recording = LiveTvRecordingsRepository.get(id)
        if (recording == null || recording.status != LiveTvRecordingStatus.Scheduled) {
            log.w { "Recording $id not started: ${recording?.status ?: "unknown"}" }
            stopIfIdle()
            return
        }
        jobs[id] = scope.launch {
            var output: OutputStream? = null
            var fileUri: String? = null
            try {
                val streamUrl = resolveStreamUrl(recording)
                val (stream, uri, name) = openOutput(recording)
                output = stream
                fileUri = uri
                LiveTvRecordingsRepository.update(id) {
                    it.copy(status = LiveTvRecordingStatus.Recording, fileUri = uri, fileName = name, errorMessage = null)
                }
                refreshNotification()
                log.i { "Recording started: '${recording.title}' on ${recording.channelName} → $name" }
                val recorder = LiveTvStreamRecorder(
                    url = streamUrl,
                    headers = recording.headers,
                    endMillis = recording.endMillis,
                    output = stream,
                    onProgress = { bytes ->
                        LiveTvRecordingsRepository.update(id) { it.copy(sizeBytes = bytes) }
                        refreshNotification()
                    },
                )
                recorders[id] = recorder
                try {
                    recorder.run()
                } catch (stopped: CancellationException) {
                    // Stopped by the user: what was recorded is kept.
                }
                LiveTvRecordingsRepository.update(id) {
                    it.copy(
                        status = if (recorder.bytesWritten > 0) LiveTvRecordingStatus.Completed else LiveTvRecordingStatus.Failed,
                        sizeBytes = recorder.bytesWritten,
                        endMillis = minOf(it.endMillis, System.currentTimeMillis()),
                        errorMessage = if (recorder.bytesWritten > 0) null else "No data received",
                    )
                }
                log.i { "Recording finished: '${recording.title}', ${recorder.bytesWritten / 1_048_576} MB" }
                notifyResult(recording, success = recorder.bytesWritten > 0)
            } catch (error: Throwable) {
                log.e(error) { "Recording failed: '${recording.title}'" }
                LiveTvRecordingsRepository.update(id) {
                    it.copy(status = LiveTvRecordingStatus.Failed, errorMessage = error.message ?: error::class.simpleName)
                }
                notifyResult(recording, success = false)
            } finally {
                runCatching { output?.close() }
                if (LiveTvRecordingsRepository.get(id)?.sizeBytes == 0L) {
                    fileUri?.let { runCatching { LiveTvRecordingPlatform.deleteFile(it) } }
                }
                recorders.remove(id)
                jobs.remove(id)
                refreshNotification()
                stopIfIdle()
            }
        }
    }

    private fun stopRecording(id: String) {
        recorders[id]?.abort()
        jobs[id]?.cancel()
    }

    /** Portal (Stalker) links expire: a fresh one is asked at the start; the saved one otherwise. */
    private suspend fun resolveStreamUrl(recording: LiveTvRecording): String {
        if (recording.stalkerCommand.isNullOrBlank()) return recording.streamUrl
        return runCatching {
            LiveTvRepository.ensureLoaded()
            LiveTvRepository.prepareForPlayback(
                LiveTvChannel(
                    id = recording.channelId,
                    name = recording.channelName,
                    streamUrl = recording.streamUrl,
                    headers = recording.headers,
                    stalkerCommand = recording.stalkerCommand,
                    playlistId = recording.playlistId,
                ),
            ).streamUrl
        }.onFailure { log.w(it) { "Portal link not refreshed, using the saved one" } }
            .getOrDefault(recording.streamUrl)
    }

    /** The output file: in the chosen folder (SAF) or in the app's own Recordings folder. */
    private fun openOutput(recording: LiveTvRecording): Triple<OutputStream, String, String> {
        val name = recordingFileName(recording)
        val tree = LiveTvRecordingPlatform.treeUri()
        if (tree != null) {
            runCatching {
                val uri = SafDownloadTree(this, tree).createFile(name, "video/mp2t")
                val stream = contentResolver.openOutputStream(uri, "w") ?: error("Folder not writable")
                return Triple(stream, uri.toString(), name)
            }.onFailure { log.w(it) { "Chosen recordings folder not usable, using the app folder" } }
        }
        val file = File(LiveTvRecordingPlatform.appFolder(this), name)
        return Triple(FileOutputStream(file), Uri.fromFile(file).toString(), name)
    }

    private fun recordingFileName(recording: LiveTvRecording): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.ROOT)
            .format(java.util.Date(recording.startMillis))
        val safeTitle = recording.title.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(80)
        return "$safeTitle - ${recording.channelName.replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(40)} - $date.ts"
    }

    private fun stopIfIdle() {
        if (jobs.isNotEmpty()) return
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        recorders.values.forEach { it.abort() }
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------------------- notifications

    private fun refreshNotification() {
        if (jobs.isEmpty()) return
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, progressNotification()) }
    }

    private fun progressNotification(): Notification {
        val active = LiveTvRecordingsRepository.recordings.value.filter { it.id in jobs.keys }
        val first = active.firstOrNull()
        val title = runBlocking {
            getString(Res.string.live_tv_recording_notification_title, first?.title ?: "…")
        }
        val text = first?.let {
            runBlocking {
                getString(
                    Res.string.live_tv_recording_notification_progress,
                    it.channelName,
                    formatRecordingSize(it.sizeBytes),
                    formatRecordingClock(it.endMillis),
                )
            }
        }.orEmpty()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(if (active.size > 1) "$title (+${active.size - 1})" else title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
        if (first != null) {
            val stop = PendingIntent.getService(
                this,
                first.id.hashCode(),
                Intent(this, LiveTvRecordingService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, first.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, runBlocking { getString(Res.string.live_tv_recording_notification_stop) }, stop)
        }
        return builder.build()
    }

    private fun notifyResult(recording: LiveTvRecording, success: Boolean) {
        val text = runBlocking {
            getString(
                if (success) Res.string.live_tv_recording_notification_finished else Res.string.live_tv_recording_notification_failed,
                recording.channelName,
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(recording.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(recording.id.hashCode(), notification) }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        NOTIFICATION_ID,
        Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(this@LiveTvRecordingService)
            action = Intent.ACTION_VIEW
            data = Uri.parse("nuvio://recordings")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "com.nuvio.app.livetv.RECORDING_START"
        const val ACTION_STOP = "com.nuvio.app.livetv.RECORDING_STOP"
        const val EXTRA_ID = "recording_id"
        private const val CHANNEL_ID = "live_tv_recordings"
        private const val NOTIFICATION_ID = 46_201

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    runBlocking { getString(Res.string.live_tv_recording_channel_name) },
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }
}
