package com.nuvio.app.features.livetv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live TV recordings are Android only for now. */
internal actual object LiveTvRecordingPlatform {
    actual val isSupported: Boolean = false
    actual val folderLabel: StateFlow<String?> = MutableStateFlow(null)

    actual fun loadIndex(): String? = null
    actual fun saveIndex(raw: String) = Unit
    actual fun arm(recording: LiveTvRecording) = Unit
    actual fun disarm(recordingId: String) = Unit
    actual fun stop(recordingId: String) = Unit
    actual fun deleteFile(fileUri: String) = Unit
    actual fun selectFolder(treeUri: String): Boolean = false
    actual fun clearFolder() = Unit
    actual fun canScheduleExactAlarms(): Boolean = true
    actual fun openExactAlarmSettings() = Unit
}
