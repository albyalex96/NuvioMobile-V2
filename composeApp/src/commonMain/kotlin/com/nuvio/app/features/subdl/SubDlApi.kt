package com.nuvio.app.features.subdl

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What to search on SubDL: a movie or one episode, by IMDb or TMDB id. */
internal data class SubDlQuery(
    /** With the `tt` prefix, as SubDL expects it. */
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val isEpisode: Boolean
        get() = season != null && episode != null
}

@Serializable
internal data class SubDlSearchResponse(
    val status: Boolean = false,
    val error: String? = null,
    val subtitles: List<SubDlSubtitleItem> = emptyList(),
)

@Serializable
internal data class SubDlSubtitleItem(
    @SerialName("release_name") val releaseName: String? = null,
    val name: String? = null,
    /** SubDL language code (`EN`, `IT`, `BR_PT`…). */
    val language: String? = null,
    /** Language name (`english`), used when [language] is missing. */
    val lang: String? = null,
    val author: String? = null,
    /** Archive path on `dl.subdl.com`, e.g. `/subtitle/3197651-3213944.zip`. */
    val url: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("episode_from") val episodeFrom: Int? = null,
    @SerialName("episode_end") val episodeEnd: Int? = null,
    @SerialName("full_season") val fullSeason: Boolean = false,
    val hi: Boolean = false,
    @SerialName("ai_translated") val aiTranslated: Boolean = false,
)

internal sealed interface SubDlKeyCheck {
    data object Valid : SubDlKeyCheck
    data object Invalid : SubDlKeyCheck
    /** Network or server problem: the key could not be checked. */
    data object Unknown : SubDlKeyCheck
}

/**
 * REST client of `api.subdl.com` (v1). The API key travels in the query string, so search URLs
 * are never logged.
 */
internal object SubDlApi {
    private const val BASE_URL = "https://api.subdl.com/api/v1/subtitles"
    internal const val DOWNLOAD_BASE_URL = "https://dl.subdl.com"
    private const val SUBS_PER_PAGE = 30

    // Any title works for the key check; a missing one only gives "can't find".
    private const val KEY_CHECK_IMDB_ID = "tt0133093"

    private val log = Logger.withTag(SUBDL_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to "Nuvio v${AppVersionConfig.VERSION_NAME.ifBlank { "1.0.0" }}",
        "Accept" to "application/json",
    )

    internal fun searchUrl(apiKey: String, query: SubDlQuery, languages: List<String>): String {
        val params = buildList {
            add("api_key" to apiKey)
            query.imdbId?.let { add("imdb_id" to it) }
            if (query.imdbId == null) query.tmdbId?.let { add("tmdb_id" to it.toString()) }
            if (query.season != null) {
                add("type" to "tv")
                add("season_number" to query.season.toString())
                // Without the episode the search covers the whole season (packs included).
                query.episode?.let { add("episode_number" to it.toString()) }
            } else {
                add("type" to "movie")
            }
            if (languages.isNotEmpty()) add("languages" to languages.joinToString(","))
            add("subs_per_page" to SUBS_PER_PAGE.toString())
        }
        return params.joinToString(separator = "&", prefix = "$BASE_URL?") { (key, value) ->
            "$key=${value.encodeURLParameter()}"
        }
    }

    suspend fun search(apiKey: String, query: SubDlQuery, languages: List<String>): List<SubDlSubtitleItem> {
        val response = httpRequestRaw(
            method = "GET",
            url = searchUrl(apiKey, query, languages),
            headers = headers(),
            body = "",
            maxResponseBodyBytes = 4 * 1024 * 1024,
        )
        val parsed = runCatching { json.decodeFromString<SubDlSearchResponse>(response.body) }.getOrNull()
        if (response.status !in 200..299) {
            throw SubDlException(response.status, parsed?.error ?: response.body.take(200))
        }
        parsed ?: throw SubDlException(response.status, "Unreadable search response")
        if (!parsed.status) {
            // "can't find" is SubDL's way of saying there are no subtitles.
            if (parsed.error?.contains("can't find", ignoreCase = true) == true) return emptyList()
            throw SubDlException(response.status, parsed.error ?: "Search failed")
        }
        return parsed.subtitles
    }

    /** Downloads the archive at [path] (relative to `dl.subdl.com`); counts against the daily limit. */
    suspend fun downloadArchive(path: String): ByteArray {
        val url = if (path.startsWith("http", ignoreCase = true)) path else "$DOWNLOAD_BASE_URL/${path.trimStart('/')}"
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = headers() + ("Accept" to "*/*"),
            body = "",
            maxResponseBodyBytes = 16 * 1024 * 1024,
        )
        if (response.status !in 200..299) {
            throw SubDlException(response.status, response.body.take(200))
        }
        return response.bodyBytes
    }

    suspend fun verifyApiKey(apiKey: String): SubDlKeyCheck = try {
        val response = httpRequestRaw(
            method = "GET",
            url = searchUrl(apiKey, SubDlQuery(imdbId = KEY_CHECK_IMDB_ID), languages = listOf("EN")),
            headers = headers(),
            body = "",
        )
        val parsed = runCatching { json.decodeFromString<SubDlSearchResponse>(response.body) }.getOrNull()
        val error = parsed?.error.orEmpty()
        val mentionsKey = error.contains("key", ignoreCase = true) ||
            error.contains("auth", ignoreCase = true) ||
            error.contains("forbidden", ignoreCase = true)
        when {
            response.status in 200..299 && parsed?.status == true -> SubDlKeyCheck.Valid
            error.contains("can't find", ignoreCase = true) -> SubDlKeyCheck.Valid
            mentionsKey || response.status == 401 -> SubDlKeyCheck.Invalid
            else -> {
                log.w { "API key check returned HTTP ${response.status}: ${error.take(120)}" }
                SubDlKeyCheck.Unknown
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        log.w(error) { "API key check failed" }
        SubDlKeyCheck.Unknown
    }
}

internal class SubDlException(
    val status: Int,
    detail: String,
) : Exception("SubDL HTTP $status: $detail")
