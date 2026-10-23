package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class MalListUiState(
    val entries: List<MalListEntry> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fetchedAtEpochMs: Long = 0L,
)

/**
 * The user's MAL anime list: cached per profile, refreshed at most every [AUTOMATIC_REFRESH_INTERVAL_MS]
 * unless the user asks, and updated in place after every write so the library reflects it at once.
 */
object MalListRepository {
    private val log = Logger.withTag("MalList")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val writeMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _uiState = MutableStateFlow(MalListUiState())
    val uiState: StateFlow<MalListUiState> = _uiState.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        val cached = runCatching { AppFileCache.read(cachePath(profileId)) }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<MalListSnapshot>(payload) }
                    .onFailure { error -> log.w(error) { "MAL list cache is unreadable" } }
                    .getOrNull()
            }
        _uiState.value = MalListUiState(
            entries = cached?.entries.orEmpty(),
            hasLoaded = cached != null,
            fetchedAtEpochMs = cached?.fetchedAtEpochMs ?: 0L,
        )
    }

    fun onProfileChanged() {
        loadedProfileId = null
        ensureLoaded()
    }

    fun clearLocalState() {
        loadedProfileId?.let { profileId -> runCatching { AppFileCache.write(cachePath(profileId), "") } }
        loadedProfileId = null
        _uiState.value = MalListUiState()
    }

    fun entry(malId: Long): MalListEntry? {
        ensureLoaded()
        return _uiState.value.entries.firstOrNull { it.malId == malId }
    }

    fun refreshAsync(intent: TrackingRefreshIntent) {
        scope.launch { refresh(intent) }
    }

    suspend fun refresh(intent: TrackingRefreshIntent) {
        ensureLoaded()
        if (!MalAuthRepository.isAuthenticated.value) return
        refreshMutex.withLock {
            val state = _uiState.value
            val age = nowEpochMs() - state.fetchedAtEpochMs
            if (intent == TrackingRefreshIntent.AUTOMATIC && state.hasLoaded && age < AUTOMATIC_REFRESH_INTERVAL_MS) {
                log.d { "MAL list refresh skipped (age=${age / 1_000}s)" }
                return@withLock
            }
            val profileId = ProfileRepository.activeProfileId
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val entries = fetchAll()
                if (profileId != ProfileRepository.activeProfileId) return@withLock
                publishEntries(entries, fetchedAt = nowEpochMs())
                log.i { "MAL list refreshed (${entries.size} anime, intent=$intent)" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "MAL list refresh failed" }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    /** Marks episode [episode] of [malId] as watched (see [planMalEpisodeWatched]). */
    suspend fun markEpisodeWatched(malId: Long, episode: Int): Boolean =
        mutate(malId, "episode $episode watched") { current, total ->
            planMalEpisodeWatched(current, episode, total, today())
        }

    suspend fun markEpisodeUnwatched(malId: Long, episode: Int): Boolean =
        mutate(malId, "episode $episode unwatched") { current, _ ->
            planMalEpisodeUnwatched(current, episode)
        }

    suspend fun setStatus(malId: Long, status: MalListStatus): Boolean =
        mutate(malId, "status ${status.wireValue}") { current, total ->
            planMalStatusChange(current, status, total, today())
        }

    suspend fun remove(malId: Long): Boolean = writeMutex.withLock {
        val response = MalApi.client.executeAllowingMissing(MalHttpMethod.DELETE, "/anime/$malId/my_list_status")
        log.i { "MAL anime $malId removed from the list (status=${response.status})" }
        publishEntries(_uiState.value.entries.filterNot { it.malId == malId })
        true
    }

    /**
     * Reads the current list status of [malId] from MAL (so dates and counters written elsewhere
     * are respected), plans the update and sends it. Returns false when nothing had to change.
     */
    private suspend fun mutate(
        malId: Long,
        reason: String,
        plan: (current: MalListStatusState?, totalEpisodes: Int) -> MalListUpdate?,
    ): Boolean = writeMutex.withLock {
        val node = fetchAnime(malId)
        val update = plan(node.myListStatus, node.numEpisodes)
        if (update == null) {
            log.d { "MAL anime $malId: nothing to update for $reason" }
            upsert(node.toListEntry(node.myListStatus))
            return@withLock false
        }
        val response = MalApi.client.execute(
            method = MalHttpMethod.PATCH,
            path = "/anime/$malId/my_list_status",
            formBody = update.toForm(),
        )
        val saved = runCatching { json.decodeFromString<MalListStatusState>(response.body) }.getOrNull()
            ?: update.applyTo(node.myListStatus)
        upsert(node.toListEntry(saved))
        log.i {
            "MAL anime $malId updated for $reason: status=${saved.statusWire} " +
                "episodes=${saved.watchedEpisodes}/${node.numEpisodes}"
        }
        true
    }

    internal suspend fun fetchAnime(malId: Long): MalAnimeNode {
        val response = MalApi.client.execute(
            method = MalHttpMethod.GET,
            path = "/anime/$malId",
            query = mapOf("fields" to "$MAL_ANIME_NODE_FIELDS,my_list_status"),
        )
        return json.decodeFromString<MalAnimeNode>(response.body)
    }

    private suspend fun fetchAll(): List<MalListEntry> {
        val entries = mutableListOf<MalListEntry>()
        var offset = 0
        repeat(MAX_PAGES) {
            val response = MalApi.client.execute(
                method = MalHttpMethod.GET,
                path = "/users/@me/animelist",
                query = mapOf(
                    "fields" to "list_status,$MAL_ANIME_NODE_FIELDS",
                    "limit" to PAGE_SIZE.toString(),
                    "offset" to offset.toString(),
                    "nsfw" to "true",
                ),
            )
            val page = json.decodeFromString<MalAnimeListPage>(response.body)
            page.data.mapTo(entries) { item -> item.node.toListEntry(item.listStatus) }
            val next = page.paging?.next?.takeIf(String::isNotBlank) ?: return entries
            offset = runCatching { Url(next).parameters["offset"]?.toInt() }.getOrNull()
                ?: (offset + page.data.size)
            if (page.data.isEmpty()) return entries
        }
        log.w { "MAL list truncated after $MAX_PAGES pages" }
        return entries
    }

    private fun upsert(entry: MalListEntry) {
        val current = _uiState.value.entries
        val updated = if (entry.statusWire == null) {
            current.filterNot { it.malId == entry.malId }
        } else if (current.any { it.malId == entry.malId }) {
            current.map { if (it.malId == entry.malId) entry else it }
        } else {
            listOf(entry) + current
        }
        publishEntries(updated)
    }

    private fun publishEntries(entries: List<MalListEntry>, fetchedAt: Long = _uiState.value.fetchedAtEpochMs) {
        _uiState.value = MalListUiState(
            entries = entries,
            hasLoaded = true,
            isLoading = false,
            fetchedAtEpochMs = fetchedAt,
        )
        val profileId = loadedProfileId ?: return
        runCatching {
            AppFileCache.write(cachePath(profileId), json.encodeToString(MalListSnapshot(entries, fetchedAt)))
        }.onFailure { error -> log.w(error) { "Failed to cache the MAL list" } }
    }

    private fun cachePath(profileId: Int): String = "mal/anime_list_$profileId.json"

    private fun today(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private const val PAGE_SIZE = 1000
    private const val MAX_PAGES = 20
    private const val AUTOMATIC_REFRESH_INTERVAL_MS = 15L * 60L * 1_000L
}
