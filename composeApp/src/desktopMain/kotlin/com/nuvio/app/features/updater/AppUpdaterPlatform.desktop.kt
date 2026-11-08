package com.nuvio.app.features.updater

import co.touchlab.kermit.Logger
import com.nuvio.app.desktop.compat.DesktopContext
import com.nuvio.app.desktop.compat.DesktopPreferencesRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Desktop updates: the release MSI is downloaded to the cache, then the bundled Nuvio updater
 * window (`resources/updater/nuvio-updater.ps1`) replaces the current version and the app closes.
 * The shared API still speaks of "APK": here it is the MSI file.
 */
actual object AppUpdaterPlatform {
    private val log = Logger.withTag("DesktopUpdater")
    private val prefs by lazy { DesktopContext.getSharedPreferences("nuvio_updater", 0) }
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    // Only the installed app updates itself: a `gradlew run` session has no MSI install to replace.
    actual val isSupported: Boolean =
        isWindows && System.getProperty("compose.application.resources.dir") != null

    actual val isDebugBuild: Boolean = false

    actual fun getSupportedAbis(): List<String> = listOf("windows-x64")

    actual fun getIgnoredTag(): String? = prefs.getString("ignored_tag", null)

    actual fun setIgnoredTag(tag: String?) = prefs.edit().putString("ignored_tag", tag).apply()

    actual fun getUpdateChannel(): String? = prefs.getString("channel", null)

    actual fun setUpdateChannel(channel: String) = prefs.edit().putString("channel", channel).apply()

    actual fun getAutoDownload(): Boolean = prefs.getBoolean("auto_download", false)

    actual fun setAutoDownload(enabled: Boolean) = prefs.edit().putBoolean("auto_download", enabled).apply()

    actual fun getPendingDownload(): String? = prefs.getString("pending_download", null)

    actual fun setPendingDownload(value: String?) = prefs.edit().putString("pending_download", value).apply()

    actual fun downloadedApkExists(path: String): Boolean = File(path).isFile

    actual fun deleteDownloadedApk(path: String) {
        File(path).delete()
    }

    private val updatesDir: File
        get() = File(DesktopContext.cacheDir, "updates").apply { mkdirs() }

    actual suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            log.i { "Downloading update $assetName" }
            val target = File(updatesDir, assetName.substringAfterLast('/').ifBlank { "Nuvio-update.msi" })
            val partial = File(target.path + ".part")
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
            val response = client.send(
                HttpRequest.newBuilder(URI(assetUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
            val total = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull()
            var downloaded = 0L
            response.body().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            target.delete()
            check(partial.renameTo(target)) { "Cannot finalize ${target.name}" }
            log.i { "Update downloaded (${target.length()} bytes)" }
            target.absolutePath
        }.onFailure { log.e(it) { "Update download failed" } }
    }

    // No "unknown sources" permission on Windows.
    actual fun canRequestPackageInstalls(): Boolean = true

    actual fun openUnknownSourcesSettings() = Unit

    actual fun installDownloadedApk(path: String): Result<Unit> = runCatching {
        val msi = File(path)
        check(msi.isFile) { "Installer not found" }
        DesktopPreferencesRegistry.flush()
        val updaterStarted = runCatching { startNuvioUpdater(msi) }
            .onFailure { log.e(it) { "Nuvio updater window could not start, falling back to msiexec" } }
            .getOrDefault(false)
        if (!updaterStarted) {
            log.w { "Starting msiexec directly for ${msi.name}" }
            ProcessBuilder("msiexec.exe", "/i", msi.absolutePath, "/passive", "/norestart").start()
        }
        // The app must exit so its files can be replaced.
        exitProcess(0)
    }

    /**
     * Starts the Nuvio-styled updater window (PowerShell + WPF script bundled in the resources).
     * It waits for the app to exit, uninstalls the old version, installs the new MSI silently in
     * the same folder, checks Nuvio.exe exists and reopens the app. Returns false if it can't start.
     */
    private fun startNuvioUpdater(msi: File): Boolean {
        val appExe = System.getProperty("jpackage.app-path")?.let(::File)
        val installDir = appExe?.parentFile
        if (installDir == null || !installDir.isDirectory) {
            log.w { "Install folder unknown (jpackage.app-path=${appExe?.path})" }
            return false
        }
        val script = File(updatesDir, "nuvio-updater.ps1")
        val resource = AppUpdaterPlatform::class.java.getResourceAsStream("/updater/nuvio-updater.ps1")
            ?: return false.also { log.w { "Updater script missing from resources" } }
        resource.use { input -> script.outputStream().use { input.copyTo(it) } }
        val lang = if (java.util.Locale.getDefault().language == "it") "it" else "en"
        log.i { "Starting Nuvio updater for ${msi.name} into ${installDir.path} (lang=$lang), closing the app" }
        ProcessBuilder(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
            "-File", script.absolutePath,
            "-Msi", msi.absolutePath,
            "-InstallDir", installDir.absolutePath,
            "-AppPid", ProcessHandle.current().pid().toString(),
            "-Lang", lang,
            "-LogDir", updatesDir.absolutePath,
        ).start()
        return true
    }
}
