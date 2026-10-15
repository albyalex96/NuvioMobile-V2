package com.nuvio.app.core.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

@OptIn(ExperimentalForeignApi::class)
internal actual object AppLogFileStorage {
    private val directory: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
        "${base.orEmpty()}/app_logs"
    }
    private val path: String get() = "$directory/app_log.jsonl"

    actual fun readText(): String? =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    actual fun append(text: String) {
        val bytes = text.encodeToByteArray()
        if (bytes.isEmpty()) return
        ensureDirectory()
        val file = fopen(path, "ab") ?: return
        try {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
        } finally {
            fclose(file)
        }
    }

    actual fun overwrite(text: String) {
        ensureDirectory()
        NSString.create(string = text).writeToFile(path, true, NSUTF8StringEncoding, null)
    }

    actual fun clear() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    private fun ensureDirectory() {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }
}

internal actual object AppLoggerSettingsStorage {
    private const val ENABLED_KEY = "app_logger_enabled"

    actual fun loadEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey(ENABLED_KEY) != null) defaults.boolForKey(ENABLED_KEY) else null
    }

    actual fun saveEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ENABLED_KEY)
    }
}

internal actual object AppLogPlatform {
    actual val supportsSystemLogImport: Boolean = false

    actual fun readSystemLog(): List<PendingAppLogEntry> = emptyList()

    actual fun formatTimestamp(epochMs: Long, pattern: String): String {
        val formatter = NSDateFormatter().apply {
            dateFormat = pattern
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
        }
        return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
    }
}
