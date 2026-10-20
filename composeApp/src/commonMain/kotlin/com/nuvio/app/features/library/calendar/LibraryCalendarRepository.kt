package com.nuvio.app.features.library.calendar

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.profiles.MAX_PROFILES
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * Release calendar of the library. Metadata is fetched per library item and cached on disk per
 * profile, so opening the calendar only fetches new items and entries older than [CacheTtlMs];
 * [refresh] with force = true refetches everything.
 */
object LibraryCalendarRepository {
    private const val CacheTtlMs = 12L * 60 * 60 * 1000
    private const val FetchConcurrency = 4
    private const val LibraryWaitMs = 10_000L
    private const val ManifestWaitMs = 10_000L

    private val log = Logger.withTag("LibraryCalendar")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val syncMutex = Mutex()

    private val _uiState = MutableStateFlow(LibraryCalendarUiState())
    val uiState: StateFlow<LibraryCalendarUiState> = _uiState.asStateFlow()

    @Volatile
    private var loadedProfileId: Int? = null
    @Volatile
    private var entriesByKey: Map<String, LibraryCalendarCacheEntry> = emptyMap()
    @Volatile
    private var lastUpdatedEpochMs: Long = 0L
    private var syncJob: Job? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadFromDisk(profileId)
    }

    /** Updates the calendar: only missing or expired items unless [force] is set. */
    fun refresh(force: Boolean) {
        ensureLoaded()
        if (syncJob?.isActive == true) {
            log.d { "Refresh skipped: a sync is already running (force=$force)" }
            return
        }
        syncJob = scope.launch {
            runCatching { sync(force) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    log.e(error) { "Calendar sync failed" }
                    _uiState.update { it.copy(isRefreshing = false) }
                }
        }
    }

    fun clearLocalState() {
        syncJob?.cancel()
        syncJob = null
        (1..MAX_PROFILES).forEach { profileId ->
            runCatching { AppFileCache.write(cachePath(profileId), "") }
        }
        loadedProfileId = null
        entriesByKey = emptyMap()
        lastUpdatedEpochMs = 0L
        _uiState.value = LibraryCalendarUiState()
    }

    private fun loadFromDisk(profileId: Int) {
        syncJob?.cancel()
        syncJob = null
        val payload = runCatching {
            AppFileCache.read(cachePath(profileId))
                ?.takeIf(String::isNotBlank)
                ?.let { json.decodeFromString<LibraryCalendarCachePayload>(it) }
        }.onFailure { error ->
            log.w(error) { "Failed to read the calendar cache, starting empty" }
        }.getOrNull()?.takeIf { it.version == LibraryCalendarCacheVersion }

        loadedProfileId = profileId
        entriesByKey = payload?.entries.orEmpty().associateBy { it.key }
        lastUpdatedEpochMs = payload?.lastUpdatedEpochMs ?: 0L
        log.d { "Loaded calendar cache for profile $profileId: ${entriesByKey.size} items" }
        publish(isRefreshing = false, failedCount = 0)
    }

    private suspend fun sync(force: Boolean) = syncMutex.withLock {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId != profileId) {
            log.d { "Sync skipped: the cache belongs to another profile" }
            return@withLock
        }

        LibraryRepository.ensureLoaded()
        val library = withTimeoutOrNull(LibraryWaitMs) {
            LibraryRepository.uiState.first { it.isLoaded }
        } ?: LibraryRepository.uiState.value
        val targets = library.items
            .mapNotNull { it.toLibraryCalendarTarget() }
            .distinctBy { it.key }
        val targetKeys = targets.mapTo(mutableSetOf()) { it.key }
        val now = EpisodeReleaseDatePlatform.nowEpochMs()

        val kept = entriesByKey.filterKeys { it in targetKeys }
        val removedCount = entriesByKey.size - kept.size
        val toFetch = targets.filter { target ->
            val entry = kept[target.key]
            force || entry == null || now - entry.fetchedAtEpochMs >= CacheTtlMs
        }

        if (toFetch.isEmpty()) {
            if (removedCount > 0) {
                entriesByKey = kept
                persist(profileId)
            }
            log.d { "Calendar cache is fresh (${targets.size} items, $removedCount removed)" }
            publish(isRefreshing = false, failedCount = 0, trackedKeys = targetKeys)
            return@withLock
        }

        log.i { "Refreshing calendar: ${toFetch.size}/${targets.size} items (force=$force)" }
        entriesByKey = kept
        _uiState.update {
            it.copy(isRefreshing = true, progressDone = 0, progressTotal = toFetch.size)
        }

        AddonRepository.initialize()
        if (withTimeoutOrNull(ManifestWaitMs) { AddonRepository.awaitManifestsLoaded() } == null) {
            log.w { "Addon manifests not ready after ${ManifestWaitMs}ms, fetching anyway" }
        }

        val minDateIso = plusDaysIso(CurrentDateProvider.todayIsoDate(), -LibraryCalendarPastWindowDays)
            ?: CurrentDateProvider.todayIsoDate()
        val semaphore = Semaphore(FetchConcurrency)
        val results = coroutineScope {
            toFetch.map { target ->
                async {
                    semaphore.withPermit {
                        val events = fetchEvents(target, minDateIso)
                        _uiState.update { it.copy(progressDone = it.progressDone + 1) }
                        target to events
                    }
                }
            }.awaitAll()
        }

        if (ProfileRepository.activeProfileId != profileId) {
            log.i { "Profile changed during the calendar sync, results discarded" }
            return@withLock
        }

        val updated = kept.toMutableMap()
        var failedCount = 0
        results.forEach { (target, events) ->
            if (events == null) {
                failedCount++
            } else {
                updated[target.key] = LibraryCalendarCacheEntry(
                    key = target.key,
                    fetchedAtEpochMs = now,
                    events = events,
                )
            }
        }
        entriesByKey = updated
        lastUpdatedEpochMs = now
        persist(profileId)
        publish(isRefreshing = false, failedCount = failedCount, trackedKeys = targetKeys)
        log.i {
            "Calendar refreshed: ${_uiState.value.events.size} releases from ${targets.size} items, $failedCount failed"
        }
    }

    /** Returns null when the metadata could not be loaded, so the previous cache entry is kept. */
    private suspend fun fetchEvents(target: LibraryCalendarTarget, minDateIso: String): List<LibraryCalendarEvent>? {
        val meta = try {
            MetaDetailsRepository.fetch(type = target.type, id = target.id, cacheResult = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Failed to load metadata for ${target.key}" }
            null
        }
        if (meta == null) {
            log.d { "No metadata for ${target.key}" }
            return null
        }
        return buildLibraryCalendarEvents(target, meta, minDateIso)
    }

    private fun publish(
        isRefreshing: Boolean,
        failedCount: Int,
        trackedKeys: Set<String>? = null,
    ) {
        val visibleEntries = trackedKeys?.let { keys -> entriesByKey.filterKeys { it in keys } } ?: entriesByKey
        _uiState.value = LibraryCalendarUiState(
            isLoaded = true,
            isRefreshing = isRefreshing,
            events = visibleEntries.values.flatMap { it.events }.sortedWith(libraryCalendarEventOrder),
            lastUpdatedEpochMs = lastUpdatedEpochMs.takeIf { it > 0L },
            failedCount = failedCount,
            trackedCount = visibleEntries.size,
        )
    }

    private fun persist(profileId: Int) {
        runCatching {
            val payload = LibraryCalendarCachePayload(
                lastUpdatedEpochMs = lastUpdatedEpochMs,
                entries = entriesByKey.values.sortedBy { it.key },
            )
            AppFileCache.write(cachePath(profileId), json.encodeToString(payload))
        }.onFailure { error ->
            log.e(error) { "Failed to write the calendar cache" }
        }
    }

    private fun cachePath(profileId: Int): String = "library_calendar/profile_$profileId.json"
}
