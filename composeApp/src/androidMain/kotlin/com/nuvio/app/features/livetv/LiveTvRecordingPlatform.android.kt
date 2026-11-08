package com.nuvio.app.features.livetv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal actual object LiveTvRecordingPlatform {
    private val log = Logger.withTag("LiveTvRecording")
    private const val PREFS = "nuvio_live_tv_recordings"
    private const val KEY_INDEX = "index"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_TREE_LABEL = "tree_label"

    private var appContext: Context? = null
    private var preferences: SharedPreferences? = null
    private val folder = MutableStateFlow<String?>(null)

    actual val isSupported: Boolean = true
    actual val folderLabel: StateFlow<String?> = folder.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences = prefs
        folder.value = prefs.getString(KEY_TREE_URI, null)?.let { prefs.getString(KEY_TREE_LABEL, null) ?: it }
    }

    internal fun treeUri(): Uri? = preferences?.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    /** The app's own folder (Android/data/<app>/files/Movies/Recordings): no permission needed. */
    internal fun appFolder(context: Context): File =
        File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir, "Recordings")
            .apply { mkdirs() }

    actual fun loadIndex(): String? = preferences?.getString(KEY_INDEX, null)

    actual fun saveIndex(raw: String) {
        preferences?.edit()?.putString(KEY_INDEX, raw)?.apply()
    }

    actual fun arm(recording: LiveTvRecording) {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        if (recording.startMillis <= now + 5_000L) {
            log.i { "Recording '${recording.title}' starts now" }
            startService(context, recording.id)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmIntent(context, recording.id)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recording.startMillis, pendingIntent)
            log.i { "Exact alarm set for '${recording.title}'" }
        } else {
            // Without the permission Android may start it a few minutes late.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recording.startMillis, pendingIntent)
            log.w { "Exact alarms not allowed: '${recording.title}' may not start with the app closed" }
        }
    }

    actual fun disarm(recordingId: String) {
        val context = appContext ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context, recordingId))
    }

    actual fun stop(recordingId: String) {
        val context = appContext ?: return
        context.startService(
            Intent(context, LiveTvRecordingService::class.java)
                .setAction(LiveTvRecordingService.ACTION_STOP)
                .putExtra(LiveTvRecordingService.EXTRA_ID, recordingId),
        )
    }

    actual fun deleteFile(fileUri: String) {
        val context = appContext ?: return
        val uri = Uri.parse(fileUri)
        if (uri.scheme == "content") {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            File(uri.path ?: return).delete()
        }
    }

    actual fun selectFolder(treeUri: String): Boolean {
        val context = appContext ?: return false
        val uri = Uri.parse(treeUri)
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val label = DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { treeUri }
            preferences?.edit()?.putString(KEY_TREE_URI, treeUri)?.putString(KEY_TREE_LABEL, label)?.apply()
            folder.value = label
            log.i { "Recordings folder selected: $label" }
        }.onFailure { log.e(it) { "Cannot use the picked recordings folder" } }.isSuccess
    }

    actual fun clearFolder() {
        preferences?.edit()?.remove(KEY_TREE_URI)?.remove(KEY_TREE_LABEL)?.apply()
        folder.value = null
        log.i { "Recordings folder reset to app storage" }
    }

    actual fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val context = appContext ?: return false
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    actual fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val context = appContext ?: return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { log.w(it) { "Exact alarm settings not available" } }
    }

    internal fun startService(context: Context, recordingId: String) {
        val intent = Intent(context, LiveTvRecordingService::class.java)
            .setAction(LiveTvRecordingService.ACTION_START)
            .putExtra(LiveTvRecordingService.EXTRA_ID, recordingId)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { error ->
                log.e(error) { "Recording service could not start" }
                LiveTvRecordingsRepository.update(recordingId) {
                    it.copy(status = LiveTvRecordingStatus.Failed, errorMessage = error.message ?: "Service not started")
                }
            }
    }

    private fun alarmIntent(context: Context, recordingId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            recordingId.hashCode(),
            Intent(context, LiveTvRecordingAlarmReceiver::class.java)
                .setAction(LiveTvRecordingAlarmReceiver.ACTION_ALARM)
                .putExtra(LiveTvRecordingService.EXTRA_ID, recordingId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
