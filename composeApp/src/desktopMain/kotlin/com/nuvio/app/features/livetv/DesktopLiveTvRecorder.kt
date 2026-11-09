package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.features.downloads.hls.HlsConversionRequest
import com.nuvio.app.features.downloads.hls.HlsConverter
import com.nuvio.app.features.downloads.hls.HlsMediaSource
import com.nuvio.app.features.downloads.hls.HlsSegmentFormat
import com.nuvio.app.features.downloads.hls.HlsSourceKind
import com.sun.jna.platform.win32.Kernel32
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs the desktop recordings (several at once if they overlap), like the Android service:
 * the stream is written to a .ts in the app data folder, converted to MKV without re-encoding at
 * the end (FFmpeg, like the downloads) and moved to the recordings folder. While something is
 * recording Windows is kept from going to sleep.
 */
internal object DesktopLiveTvRecorder {
    private val log = Logger.withTag("LiveTvRecording")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val recorders = ConcurrentHashMap<String, LiveTvStreamRecorder>()

    private val _active = MutableStateFlow(0)

    /** Number of recordings running or converting (the window asks before closing). */
    val active: StateFlow<Int> = _active.asStateFlow()

    fun start(id: String) {
        if (jobs.containsKey(id)) return
        val recording = LiveTvRecordingsRepository.get(id)
        if (recording == null || recording.status != LiveTvRecordingStatus.Scheduled) {
            log.w { "Recording $id not started: ${recording?.status ?: "unknown"}" }
            return
        }
        WindowsRecordingTasks.delete(id)
        jobs[id] = scope.launch {
            var output: OutputStream? = null
            var audioStream: OutputStream? = null
            var audioFile: File? = null
            var videoFile: File? = null
            try {
                SleepBlocker.acquire(id)
                val streamUrl = resolveStreamUrl(recording)
                val convert = LiveTvRecordingsRepository.options.value.convertToMp4
                val name = recordingFileName(recording)
                val file = File(LiveTvRecordingPlatform.workDirectory(), name)
                videoFile = file
                val stream = FileOutputStream(file)
                output = stream
                LiveTvRecordingsRepository.update(id) {
                    it.copy(
                        status = LiveTvRecordingStatus.Recording,
                        fileUri = file.toURI().toString(),
                        fileName = name,
                        errorMessage = null,
                    )
                }
                log.i { "Recording started: '${recording.title}' on ${recording.channelName} → $name" }
                val recorder = LiveTvStreamRecorder(
                    url = streamUrl,
                    headers = recording.headers,
                    endMillis = recording.endMillis,
                    output = stream,
                    audioOutput = {
                        val audio = File(file.parentFile, file.nameWithoutExtension + ".audio")
                        audioFile = audio
                        FileOutputStream(audio).also { audioStream = it }
                    },
                    onProgress = { bytes -> LiveTvRecordingsRepository.update(id) { it.copy(sizeBytes = bytes) } },
                )
                recorders[id] = recorder
                try {
                    recorder.run()
                } catch (stopped: CancellationException) {
                    // Stopped by the user: what was recorded is kept.
                }
                runCatching { stream.close() }
                runCatching { audioStream?.close() }
                output = null
                audioStream = null
                val recorded = recorder.bytesWritten > 0
                // A separate audio rendition must be merged, or the file would have no sound.
                val separateAudio = recorder.separateAudio
                val merge = convert || separateAudio != null
                LiveTvRecordingsRepository.update(id) {
                    it.copy(
                        status = when {
                            !recorded -> LiveTvRecordingStatus.Failed
                            merge -> LiveTvRecordingStatus.Converting
                            else -> LiveTvRecordingStatus.Completed
                        },
                        sizeBytes = recorder.bytesWritten,
                        endMillis = minOf(it.endMillis, System.currentTimeMillis()),
                        errorMessage = if (recorded) null else "No data received",
                    )
                }
                if (recorded) {
                    val final = finish(
                        id = id,
                        tsFile = file,
                        videoFormat = recorder.videoFormat,
                        audioFile = audioFile?.takeIf { separateAudio != null },
                        separateAudio = separateAudio,
                        convert = merge,
                    )
                    LiveTvRecordingsRepository.update(id) {
                        it.copy(
                            status = LiveTvRecordingStatus.Completed,
                            fileUri = final.toURI().toString(),
                            fileName = final.name,
                            sizeBytes = final.length(),
                            conversionProgress = null,
                        )
                    }
                }
                log.i { "Recording finished: '${recording.title}', ${recorder.bytesWritten / 1_048_576} MB" }
            } catch (error: Throwable) {
                log.e(error) { "Recording failed: '${recording.title}'" }
                LiveTvRecordingsRepository.update(id) {
                    it.copy(status = LiveTvRecordingStatus.Failed, errorMessage = error.message ?: error::class.simpleName)
                }
            } finally {
                runCatching { output?.close() }
                runCatching { audioStream?.close() }
                audioFile?.delete()
                if (LiveTvRecordingsRepository.get(id)?.sizeBytes == 0L) videoFile?.delete()
                recorders.remove(id)
                jobs.remove(id)
                _active.value = jobs.size
                SleepBlocker.release(id)
            }
        }
        _active.value = jobs.size
    }

    /**
     * Ends the recording now: the recorder stops reading and the job goes on with the conversion.
     * Before the stream opens, the job is simply cancelled.
     */
    fun stop(id: String) {
        val recorder = recorders[id]
        if (recorder != null) recorder.abort() else jobs[id]?.cancel()
    }

    /** Closing Nuvio: the recordings stop where they are (the .ts files stay playable). */
    fun stopAll() {
        recorders.values.forEach { it.abort() }
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

    /**
     * Converts the .ts to MKV without re-encoding (when asked, or to merge a separate audio
     * rendition), then moves the result to the recordings folder. The .ts is deleted only when
     * the conversion worked; otherwise it is kept as it is.
     */
    private suspend fun finish(
        id: String,
        tsFile: File,
        videoFormat: HlsSegmentFormat,
        audioFile: File?,
        separateAudio: LiveTvStreamRecorder.SeparateAudio?,
        convert: Boolean,
    ): File {
        var result = tsFile
        if (convert) {
            val durationUs = ((LiveTvRecordingsRepository.get(id)?.durationMillis ?: 0L) * 1000L).coerceAtLeast(1L)
            runCatching {
                HlsConverter(
                    HlsConversionRequest(
                        sources = listOfNotNull(
                            HlsMediaSource(
                                kind = HlsSourceKind.Video,
                                track = null,
                                format = videoFormat,
                                groupFiles = mapOf(0 to tsFile),
                            ),
                            if (audioFile != null && separateAudio != null) {
                                HlsMediaSource(
                                    kind = HlsSourceKind.Audio,
                                    track = null,
                                    format = separateAudio.format,
                                    groupFiles = mapOf(0 to audioFile),
                                    packedPtsByGroup = separateAudio.packedPts?.let { mapOf(0 to it) }.orEmpty(),
                                )
                            } else {
                                null
                            },
                        ),
                        subtitles = emptyList(),
                        groupOffsetsUs = listOf(0L),
                        totalDurationUs = durationUs,
                        includeMuxedAudio = separateAudio == null,
                        muxedAudioLanguage = null,
                        muxedAudioName = null,
                    ),
                ).convert(File(tsFile.parentFile, tsFile.nameWithoutExtension)) { progress ->
                    LiveTvRecordingsRepository.update(id) { it.copy(conversionProgress = progress) }
                }.file
            }.onSuccess { converted ->
                log.i { "Recording converted to ${converted.extension.uppercase()}: ${converted.name}" }
                tsFile.delete()
                result = converted
            }.onFailure { error ->
                if (error is CancellationException) throw error
                log.w(error) { "Recording conversion failed, the .ts file is kept" }
            }
        }
        return moveToOutputFolder(result)
    }

    private fun moveToOutputFolder(file: File): File {
        val target = uniqueFile(LiveTvRecordingPlatform.outputDirectory(), file.name)
        return runCatching {
            file.copyTo(target)
            file.delete()
            target
        }.onFailure { log.w(it) { "Recording not moved to the recordings folder, kept in the app folder" } }
            .getOrDefault(file)
    }

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${name.substringBeforeLast('.')} ($index).${name.substringAfterLast('.')}")
            index++
        }
        return candidate
    }

    private fun recordingFileName(recording: LiveTvRecording): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.ROOT)
            .format(java.util.Date(recording.startMillis))
        val unsafe = Regex("""[\\/:*?"<>|]""")
        val safeTitle = recording.title.replace(unsafe, " ").trim().take(80)
        return "$safeTitle - ${recording.channelName.replace(unsafe, " ").trim().take(40)} - $date.ts"
    }
}

/**
 * Keeps Windows awake while recording (SetThreadExecutionState). The state belongs to the thread
 * that set it, so a single dedicated thread sets and clears it.
 */
private object SleepBlocker {
    private val log = Logger.withTag("LiveTvRecording")
    private val holders = mutableSetOf<String>()
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "nuvio-recording-awake").apply { isDaemon = true } }
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_SYSTEM_REQUIRED = 0x00000001

    @Synchronized
    fun acquire(id: String) {
        if (holders.add(id) && holders.size == 1) set(ES_CONTINUOUS or ES_SYSTEM_REQUIRED, "Sleep blocked while recording")
    }

    @Synchronized
    fun release(id: String) {
        if (holders.remove(id) && holders.isEmpty()) set(ES_CONTINUOUS, "Sleep allowed again")
    }

    private fun set(flags: Int, message: String) {
        if (!isWindows) return
        thread.execute {
            runCatching { Kernel32.INSTANCE.SetThreadExecutionState(flags) }
                .onSuccess { log.d { message } }
                .onFailure { log.w(it) { "SetThreadExecutionState failed" } }
        }
    }
}
