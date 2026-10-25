package com.nuvio.app.features.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.i18n.localizedByteUnit
import com.nuvio.app.core.ui.NuvioToastController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

@Serializable
data class AppUpdate(
    val tag: String,
    val title: String,
    val notes: String,
    val releaseUrl: String?,
    val assetName: String,
    val assetUrl: String,
    val assetSizeBytes: Long?,
)

data class AppUpdaterUiState(
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val isChecking: Boolean = false,
    val update: AppUpdate? = null,
    val isUpdateAvailable: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadedApkPath: String? = null,
    val showDialog: Boolean = false,
    val showUnknownSourcesDialog: Boolean = false,
    val errorMessage: String? = null,
    val isDebugTest: Boolean = false,
)

class AppUpdaterController internal constructor(
    private val scope: CoroutineScope,
    private val preferences: UpdatePreferences = UpdatePreferences.shared,
    private val fetchUpdate: suspend (UpdateChannel) -> Result<AppUpdate> = AppUpdaterRepository::getLatestChannelUpdate,
    private val downloadApk: suspend (AppUpdate, (downloadedBytes: Long, totalBytes: Long?) -> Unit) -> Result<String> =
        { update, onProgress -> AppUpdaterPlatform.downloadApk(update.assetUrl, update.assetName, onProgress) },
) {
    private val log = Logger.withTag("AppUpdater")
    private val _uiState = MutableStateFlow(AppUpdaterUiState(updateChannel = preferences.channel.value))
    val uiState: StateFlow<AppUpdaterUiState> = _uiState.asStateFlow()

    private var autoCheckStarted = false
    private var updateCheckJob: Job? = null
    private var downloadJob: Job? = null

    /** True while the current download was started automatically and must not install on completion. */
    private var backgroundDownload = false

    init {
        restorePendingDownload()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.channel.collect { channel ->
                if (channel != _uiState.value.updateChannel) {
                    updateCheckJob?.cancel()
                    downloadJob?.cancel()
                    backgroundDownload = false
                    discardPendingDownload(_uiState.value.downloadedApkPath)
                    _uiState.value = AppUpdaterUiState(updateChannel = channel)
                    if (!AppUpdaterPlatform.isDebugBuild) {
                        checkForUpdates(force = true, showNoUpdateFeedback = false)
                    }
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferences.autoDownload.drop(1).collect { enabled ->
                log.i { "Background update download ${if (enabled) "enabled" else "disabled"}" }
                if (enabled) {
                    startAutoDownloadIfNeeded()
                } else if (backgroundDownload && _uiState.value.isDownloading) {
                    log.i { "Cancelling background update download" }
                    downloadJob?.cancel()
                    backgroundDownload = false
                    _uiState.update { state -> state.copy(isDownloading = false, downloadProgress = null) }
                }
            }
        }
    }

    /**
     * An APK downloaded in a previous session (also before a crash or a restart) is offered again
     * right away, without waiting for the network check.
     */
    private fun restorePendingDownload() {
        if (!AppFeaturePolicy.inAppUpdaterEnabled || !AppUpdaterPlatform.isSupported) return
        val pending = preferences.pendingDownload() ?: return
        val valid = pending.channel == preferences.channel.value.storedValue &&
            VersionUtils.isRemoteNewer(pending.update.tag, AppVersionConfig.VERSION_NAME) &&
            AppUpdaterPlatform.downloadedApkExists(pending.apkPath)
        if (!valid) {
            log.i { "Discarding stale downloaded update ${pending.update.tag}" }
            discardPendingDownload(pending.apkPath)
            return
        }
        log.i { "Restored downloaded update ${pending.update.tag}, ready to install" }
        _uiState.update { state ->
            state.copy(
                update = pending.update,
                isUpdateAvailable = true,
                downloadedApkPath = pending.apkPath,
                showDialog = AppUpdaterPlatform.getIgnoredTag() != pending.update.tag,
            )
        }
    }

    private fun discardPendingDownload(apkPath: String?) {
        apkPath?.let(AppUpdaterPlatform::deleteDownloadedApk)
        preferences.pendingDownload()?.let { pending ->
            if (pending.apkPath != apkPath) AppUpdaterPlatform.deleteDownloadedApk(pending.apkPath)
            preferences.setPendingDownload(null)
        }
    }

    private fun startAutoDownloadIfNeeded() {
        val state = _uiState.value
        val update = state.update ?: return
        if (!state.isUpdateAvailable || state.isDownloading || state.downloadedApkPath != null ||
            state.isDebugTest || AppUpdaterPlatform.getIgnoredTag() == update.tag
        ) {
            return
        }
        startDownload(update, background = true)
    }

    fun ensureAutoCheckStarted() {
        if (autoCheckStarted || !AppFeaturePolicy.inAppUpdaterEnabled ||
            !AppUpdaterPlatform.isSupported || AppUpdaterPlatform.isDebugBuild
        ) {
            return
        }
        autoCheckStarted = true
        checkForUpdates(force = false, showNoUpdateFeedback = false)
    }

    fun checkForUpdates(force: Boolean, showNoUpdateFeedback: Boolean) {
        if (!AppFeaturePolicy.inAppUpdaterEnabled || !AppUpdaterPlatform.isSupported) {
            if (showNoUpdateFeedback) {
                scope.launch {
                    NuvioToastController.show(getString(Res.string.updates_not_available))
                }
            }
            return
        }

        if (_uiState.value.isDownloading) {
            // A manual check during a silent download reveals its progress in the banner.
            if (force) _uiState.update { state -> state.copy(showDialog = true) }
            return
        }
        updateCheckJob?.cancel()
        val channel = preferences.channel.value
        updateCheckJob = scope.launch {
            _uiState.update { state ->
                state.copy(
                    isChecking = true,
                    errorMessage = null,
                    showUnknownSourcesDialog = false,
                    isDebugTest = false,
                )
            }

            val ignoredTag = AppUpdaterPlatform.getIgnoredTag()
            val result = fetchUpdate(channel)
            currentCoroutineContext().ensureActive()
            if (channel != preferences.channel.value) return@launch

            result.onSuccess { update ->
                val remoteNewer = VersionUtils.isRemoteNewer(update.tag, AppVersionConfig.VERSION_NAME)
                val ignored = ignoredTag != null && ignoredTag == update.tag
                val previous = _uiState.value
                val keepDownloaded = previous.downloadedApkPath != null &&
                    remoteNewer && previous.update?.tag == update.tag
                if (previous.downloadedApkPath != null && !keepDownloaded) {
                    log.i { "Downloaded update ${previous.update?.tag} superseded by ${update.tag}" }
                    discardPendingDownload(previous.downloadedApkPath)
                }
                val autoDownload = remoteNewer && !ignored && !keepDownloaded &&
                    preferences.autoDownload.value
                // Background downloads stay silent: the banner appears once the APK is ready.
                val shouldShowDialog = remoteNewer && (force || !ignored) && (force || !autoDownload)

                _uiState.update { state ->
                    state.copy(
                        isChecking = false,
                        update = update.takeIf { remoteNewer },
                        isUpdateAvailable = remoteNewer,
                        isDownloading = false,
                        downloadProgress = null,
                        downloadedApkPath = state.downloadedApkPath.takeIf { keepDownloaded },
                        showDialog = shouldShowDialog,
                        showUnknownSourcesDialog = false,
                        errorMessage = null,
                    )
                }

                if (autoDownload) {
                    log.i { "Update ${update.tag} available, downloading in background" }
                    startDownload(update, background = true)
                }

                if (showNoUpdateFeedback && !remoteNewer) {
                    NuvioToastController.show(noUpdateMessage(channel))
                }
            }.onFailure { error ->
                if (_uiState.value.downloadedApkPath != null) {
                    // Offline or GitHub unavailable: keep offering the APK already downloaded.
                    log.w(error) { "Update check failed, keeping the downloaded update" }
                    _uiState.update { state -> state.copy(isChecking = false) }
                    if (showNoUpdateFeedback) {
                        NuvioToastController.show(error.message ?: getString(Res.string.updates_check_failed))
                    }
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        isChecking = false,
                        isDownloading = false,
                        downloadProgress = null,
                        downloadedApkPath = null,
                        update = null,
                        isUpdateAvailable = false,
                        showDialog = force && error !is NoChannelReleaseException,
                        showUnknownSourcesDialog = false,
                        errorMessage = if (force && error !is NoChannelReleaseException) {
                            error.message ?: getString(Res.string.updates_check_failed)
                        } else {
                            null
                        },
                    )
                }

                if (showNoUpdateFeedback) {
                    NuvioToastController.show(
                        if (error is NoChannelReleaseException) noUpdateMessage(channel)
                        else error.message ?: getString(Res.string.updates_check_failed),
                    )
                }
            }
        }
    }

    private suspend fun noUpdateMessage(channel: UpdateChannel): String = getString(
        if (channel == UpdateChannel.STABLE && VersionUtils.isPrerelease(AppVersionConfig.VERSION_NAME)) {
            Res.string.updates_waiting_for_stable
        } else {
            Res.string.updates_latest_version
        },
    )

    fun dismissDialog() {
        _uiState.update { state ->
            state.copy(
                showDialog = false,
                showUnknownSourcesDialog = false,
                errorMessage = null,
            )
        }
    }

    fun ignoreThisVersion() {
        val tag = _uiState.value.update?.tag ?: return
        AppUpdaterPlatform.setIgnoredTag(tag)
        dismissDialog()
    }

    fun downloadUpdate() {
        if (_uiState.value.isDownloading) return
        val update = _uiState.value.update ?: return
        if (_uiState.value.isDebugTest) {
            runDebugDownloadTest()
            return
        }
        startDownload(update, background = false)
    }

    private fun startDownload(update: AppUpdate, background: Boolean) {
        val previousDownload = downloadJob
        previousDownload?.cancel()
        val channel = preferences.channel.value
        backgroundDownload = background
        downloadJob = scope.launch {
            previousDownload?.join()
            val downloadContext = currentCoroutineContext()
            _uiState.update { state ->
                state.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    errorMessage = null,
                )
            }

            val result = downloadApk(update) { downloadedBytes, totalBytes ->
                downloadContext.ensureActive()
                val progress = if (totalBytes != null && totalBytes > 0L) {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
                _uiState.update { state ->
                    if (state.updateChannel == channel) state.copy(downloadProgress = progress) else state
                }
            }
            currentCoroutineContext().ensureActive()
            if (channel != preferences.channel.value) return@launch
            backgroundDownload = false
            result.onSuccess { path ->
                log.i { "Update ${update.tag} downloaded${if (background) " in background" else ""}" }
                preferences.setPendingDownload(
                    PendingUpdateDownload(channel = channel.storedValue, apkPath = path, update = update),
                )
                _uiState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        downloadedApkPath = path,
                        errorMessage = null,
                        showDialog = if (background) {
                            state.showDialog || AppUpdaterPlatform.getIgnoredTag() != update.tag
                        } else {
                            state.showDialog
                        },
                    )
                }
                if (!background) installDownloadedUpdate()
            }.onFailure { error ->
                log.e(error) { "Update ${update.tag} download failed${if (background) " in background" else ""}" }
                if (background) {
                    // The partial file stays on disk and the next attempt resumes it; meanwhile
                    // the regular banner lets the user retry by hand.
                    _uiState.update { state ->
                        state.copy(
                            isDownloading = false,
                            downloadProgress = null,
                            showDialog = state.showDialog || AppUpdaterPlatform.getIgnoredTag() != update.tag,
                        )
                    }
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        downloadedApkPath = null,
                        errorMessage = error.message ?: getString(Res.string.updates_download_failed),
                        showDialog = true,
                    )
                }
            }
        }
    }

    fun installDownloadedUpdate() {
        val apkPath = _uiState.value.downloadedApkPath ?: return
        if (!AppUpdaterPlatform.canRequestPackageInstalls()) {
            _uiState.update { state -> state.copy(showUnknownSourcesDialog = true, showDialog = true) }
            return
        }

        AppUpdaterPlatform.installDownloadedApk(apkPath).onSuccess {
            _uiState.update { state -> state.copy(showUnknownSourcesDialog = false) }
        }.onFailure { error ->
            scope.launch {
                val fallbackMessage = error.message ?: getString(Res.string.updates_install_failed)
                _uiState.update { state ->
                    state.copy(
                        errorMessage = fallbackMessage,
                        showDialog = true,
                    )
                }
            }
        }
    }

    fun resumeInstallation() {
        if (AppUpdaterPlatform.canRequestPackageInstalls()) {
            installDownloadedUpdate()
        } else {
            AppUpdaterPlatform.openUnknownSourcesSettings()
        }
    }

    fun showDebugTestUpdate() {
        if (!AppUpdaterPlatform.isDebugBuild || !AppUpdaterPlatform.isSupported) return

        updateCheckJob?.cancel()
        downloadJob?.cancel()
        _uiState.value = AppUpdaterUiState(
            updateChannel = preferences.channel.value,
            update = AppUpdate(
                tag = "9.9.9",
                title = "Nuvio 9.9.9",
                notes = """
                    - 515408581 feat(integrations): AniSkip Settings Page @albyalex96
                    - 7cf83b23e feat(integrations): SkipDB Timestamps, Provider Priority and AniSkip Page @albyalex96
                    - 1dd2aa586 feat(network): DNS over HTTPS @albyalex96
                    - 27ed43a01 feat(ui): Horizontal List Episode Card Style @albyalex96
                    - 81983491 fix(tv): Sidebar Selection Focus And Player Info Panel Remote Keys @albyalex96
                    - perf(player): Local preview entry without a commit link

                    **Full changelog:** $ReleaseRepositoryUrl/compare/v0.7.4...v0.7.5
                """.trimIndent(),
                releaseUrl = "$ReleaseRepositoryUrl/releases/tag/v0.7.5",
                assetName = "Nuvio-debug-preview.apk",
                assetUrl = "debug://update-preview",
                assetSizeBytes = 185L * 1024L * 1024L,
            ),
            isUpdateAvailable = true,
            showDialog = true,
            isDebugTest = true,
        )
    }

    private fun runDebugDownloadTest() {
        downloadJob = scope.launch {
            _uiState.update { state ->
                state.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    errorMessage = null,
                )
            }

            for (step in 1..100) {
                delay(35)
                _uiState.update { state -> state.copy(downloadProgress = step / 100f) }
            }

            _uiState.update { state ->
                state.copy(
                    isDownloading = false,
                    isUpdateAvailable = false,
                    downloadProgress = 1f,
                )
            }
        }
    }
}

@Composable
fun rememberAppUpdaterController(): AppUpdaterController {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppUpdaterController(scope) }
}

internal fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 ${localizedByteUnit("B")}"
    val units = listOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val roundedValue = if (value >= 10 || unitIndex == 0) {
        value.toInt().toString()
    } else {
        ((value * 10).toInt() / 10.0).toString()
    }
    return "$roundedValue ${localizedByteUnit(units[unitIndex])}"
}
