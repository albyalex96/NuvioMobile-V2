package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.desktop.compat.DesktopContext
import com.nuvio.app.features.downloads.DownloadsFolderPlatform
import java.io.File
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop recordings. While Nuvio is open a timer starts each recording on time; for the times
 * when Nuvio is closed every scheduled recording also gets a Windows scheduled task
 * ([WindowsRecordingTasks]) that opens Nuvio minimised (waking the PC from sleep) a minute
 * before the start. Only one Nuvio window records: the first one takes [recorderLock].
 */
internal actual object LiveTvRecordingPlatform {
    private val log = Logger.withTag("LiveTvRecording")
    private const val PREFS = "nuvio_live_tv_recordings"
    private const val KEY_INDEX = "index"
    private const val KEY_FOLDER = "folder_uri"

    /** Command-line flag of the Nuvio launched by a scheduled task: it opens minimised. */
    const val LAUNCH_FLAG = "--live-tv-recording"

    private val prefs by lazy { DesktopContext.getSharedPreferences(PREFS, 0) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timers = ConcurrentHashMap<String, Job>()
    private val folder = MutableStateFlow(savedFolder()?.absolutePath)

    actual val isSupported: Boolean = true
    actual val usesExactAlarms: Boolean = false
    actual val defaultFolderLabel: String? get() = defaultDirectory.absolutePath
    actual val folderLabel: StateFlow<String?> = folder.asStateFlow()

    /** Videos\Nuvio\Recordings, next to the downloads. */
    private val defaultDirectory: File
        get() = File(DownloadsFolderPlatform.defaultDirectory, "Recordings")

    /** Where the finished recordings go: the chosen folder, or [defaultDirectory]. */
    internal fun outputDirectory(): File =
        (savedFolder()?.takeIf { it.isDirectory && it.canWrite() } ?: defaultDirectory).apply { mkdirs() }

    /** The .ts is written here while recording (local disk, read by the converter). */
    internal fun workDirectory(): File = DesktopStorage.rootDir.resolve("recordings").toFile().apply { mkdirs() }

    private var lockChannel: FileChannel? = null
    private var lock: FileLock? = null

    /**
     * True when this Nuvio is the one that records. A second Nuvio (opened by a scheduled task
     * while one is already running, or twice by the user) leaves the recordings to the first.
     */
    @Synchronized
    fun recorderLock(): Boolean {
        if (lock != null) return true
        return runCatching {
            val channel = FileChannel.open(
                DesktopStorage.rootDir.resolve("live-tv-recorder.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val acquired = channel.tryLock()
            if (acquired == null) channel.close() else { lockChannel = channel; lock = acquired }
            acquired != null
        }.onFailure { log.w(it) { "Recorder lock not available" } }.getOrDefault(false)
    }

    actual fun loadIndex(): String? = prefs.getString(KEY_INDEX, null)

    actual fun saveIndex(raw: String) {
        prefs.edit().putString(KEY_INDEX, raw).apply()
    }

    actual fun arm(recording: LiveTvRecording) {
        val now = System.currentTimeMillis()
        if (recording.startMillis > now + 60_000L) {
            // A scheduled task also when this Nuvio records: the app may be closed by then.
            WindowsRecordingTasks.create(recording)
        }
        if (!recorderLock()) {
            log.i { "Another Nuvio window records: '${recording.title}' left to it" }
            return
        }
        timers.remove(recording.id)?.cancel()
        if (recording.startMillis <= now + 5_000L) {
            log.i { "Recording '${recording.title}' starts now" }
            DesktopLiveTvRecorder.start(recording.id)
            return
        }
        timers[recording.id] = scope.launch {
            delay(recording.startMillis - System.currentTimeMillis())
            timers.remove(recording.id)
            DesktopLiveTvRecorder.start(recording.id)
        }
        log.i { "Timer set for '${recording.title}'" }
    }

    actual fun disarm(recordingId: String) {
        timers.remove(recordingId)?.cancel()
        WindowsRecordingTasks.delete(recordingId)
    }

    actual fun stop(recordingId: String) {
        DesktopLiveTvRecorder.stop(recordingId)
    }

    actual fun deleteFile(fileUri: String) {
        runCatching { File(URI(fileUri)) }.getOrNull()?.delete()
    }

    actual fun selectFolder(treeUri: String): Boolean {
        val directory = DownloadsFolderPlatform.toDirectory(treeUri) ?: return false
        if (!directory.isDirectory || !directory.canWrite()) {
            log.w { "Recordings folder not writable, selection ignored" }
            return false
        }
        prefs.edit().putString(KEY_FOLDER, directory.toURI().toString()).apply()
        folder.value = directory.absolutePath
        log.i { "Recordings folder selected" }
        return true
    }

    actual fun clearFolder() {
        prefs.edit().remove(KEY_FOLDER).apply()
        folder.value = null
        log.i { "Recordings folder reset to the default one" }
    }

    actual fun canScheduleExactAlarms(): Boolean = true

    actual fun openExactAlarmSettings() = Unit

    private fun savedFolder(): File? = prefs.getString(KEY_FOLDER, null)?.let(DownloadsFolderPlatform::toDirectory)
}
