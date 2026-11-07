package com.nuvio.app.features.updater

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Changelogs of the versions published before the installed one (Settings -> About ->
 * What's new in previous versions). Like [InstalledReleaseNotes], the dialog is drawn by
 * [AppUpdaterHost], so settings rows and search results only call [show].
 */
object PreviousReleaseNotes {
    private val log = Logger.withTag("ReleaseNotes")
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Published releases do not change often: one successful fetch per session is enough. */
    private var cached: List<ReleaseNotesSource>? = null

    val isAvailable: Boolean
        get() = InstalledReleaseNotes.isAvailable

    fun show() {
        if (!isAvailable) return
        log.i { "Opening release notes of the versions before ${InstalledReleaseNotes.installedVersion}" }
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }

    internal suspend fun load(
        fetch: suspend () -> Result<List<ReleaseNotesSource>> = AppUpdaterRepository::getPublishedReleaseNotes,
    ): Result<List<ReleaseNotesSource>> {
        cached?.let { return Result.success(it) }
        val installed = VersionUtils.parse(InstalledReleaseNotes.installedVersion)
        return fetch()
            .map { releases -> previousReleases(releases, installed) }
            .onSuccess { releases ->
                log.d { "Loaded ${releases.size} previous releases" }
                cached = releases
            }
            .onFailure { error -> log.e(error) { "Unable to load the previous releases" } }
    }

    /** Releases older than [installed], newest first; with an unknown installed version, all of them. */
    internal fun previousReleases(
        releases: List<ReleaseNotesSource>,
        installed: SemanticVersion?,
    ): List<ReleaseNotesSource> = releases
        .mapNotNull { release -> VersionUtils.parse(release.tag)?.let { it to release } }
        .filter { (version, _) -> installed == null || version < installed }
        .sortedByDescending { (version, _) -> version }
        .map { (_, release) -> release }
}
