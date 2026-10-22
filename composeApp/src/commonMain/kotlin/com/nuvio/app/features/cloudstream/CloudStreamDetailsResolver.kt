package com.nuvio.app.features.cloudstream

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Maps items listed by CloudStream catalogs to the ids used by the rest of the app, so they can be
 * opened on the standard details page when [CloudStreamUiState.useFullDetails] is enabled.
 */
object CloudStreamDetailsResolver {
    private val log = Logger.withTag("CloudStreamDetails")
    private val cache = mutableMapOf<String, String?>()
    private val cacheMutex = Mutex()

    fun shouldResolve(type: String, id: String): Boolean =
        CloudStreamRepository.uiState.value.useFullDetails &&
            type in resolvableTypes &&
            parseCloudStreamRouteId(id) != null

    /** Returns the standard id (IMDb, else `tmdb:<id>`) for a CloudStream route, or null to keep the plugin details. */
    suspend fun resolve(type: String, id: String): String? {
        val route = parseCloudStreamRouteId(id) ?: return null
        cacheMutex.withLock { if (id in cache) return cache[id] }
        val resolved = try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveRoute(type, route) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            log.w(error) { "CloudStream id resolution failed provider=${route.providerId}" }
            null
        }
        if (resolved == null) {
            log.i { "CloudStream item not matched, using plugin details provider=${route.providerId}" }
        }
        cacheMutex.withLock { cache[id] = resolved }
        return resolved
    }

    private suspend fun resolveRoute(type: String, route: CloudStreamRouteData): String? {
        val item = CloudStreamRepository.load(route.providerId, route.data)
            .onFailure { error -> log.w(error) { "CloudStream load failed provider=${route.providerId}" } }
            .getOrNull()
            ?: return null
        item.imdbId?.trim()?.takeIf { it.startsWith("tt", ignoreCase = true) }?.let { imdbId ->
            log.d { "CloudStream item matched by plugin IMDb id=$imdbId name=${item.name}" }
            return imdbId
        }
        val tmdbId = item.tmdbId?.trim()?.toIntOrNull()
            ?: TmdbService.searchTmdbId(title = item.name, year = item.year, mediaType = type)
            ?: return null
        val imdbId = TmdbService.tmdbToImdb(tmdbId = tmdbId, mediaType = type)
        log.d { "CloudStream item matched name=${item.name} tmdb=$tmdbId imdb=$imdbId" }
        return imdbId ?: "tmdb:$tmdbId"
    }

    private val resolvableTypes = setOf("movie", "series")
    private const val RESOLVE_TIMEOUT_MS = 30_000L
}
