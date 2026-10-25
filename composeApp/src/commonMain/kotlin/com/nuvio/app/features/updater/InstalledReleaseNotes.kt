package com.nuvio.app.features.updater

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Changelog of the version installed on the device (Settings -> About -> What's new).
 * The dialog is drawn by [AppUpdaterHost], so settings rows and search results only call [show].
 */
object InstalledReleaseNotes {
    private val log = Logger.withTag("ReleaseNotes")
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Release notes do not change after publishing: one successful fetch per session is enough. */
    private var cached: ReleaseNotesSource? = null

    val isAvailable: Boolean
        get() = AppFeaturePolicy.inAppUpdaterEnabled && AppUpdaterPlatform.isSupported

    val installedVersion: String
        get() = AppVersionConfig.VERSION_NAME

    fun show() {
        if (!isAvailable) return
        log.i { "Opening release notes of installed version $installedVersion" }
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }

    internal suspend fun load(
        fetch: suspend (String) -> Result<ReleaseNotesSource> = AppUpdaterRepository::getReleaseNotes,
    ): Result<ReleaseNotesSource> {
        cached?.let { return Result.success(it) }
        return fetch(installedVersion)
            .onSuccess { notes ->
                log.d { "Loaded release notes of ${notes.tag}" }
                cached = notes
            }
            .onFailure { error ->
                if (error is NoChannelReleaseException) {
                    log.w { "No GitHub release found for installed version $installedVersion" }
                } else {
                    log.e(error) { "Unable to load release notes of installed version $installedVersion" }
                }
            }
    }
}
