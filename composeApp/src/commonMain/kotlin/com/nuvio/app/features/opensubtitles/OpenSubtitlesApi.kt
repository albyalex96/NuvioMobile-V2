package com.nuvio.app.features.opensubtitles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What to search on OpenSubtitles: a movie or one episode, by IMDb or TMDB id. */
internal data class OpenSubtitlesQuery(
    val imdbId: Int? = null,
    val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val isEpisode: Boolean
        get() = season != null && episode != null
}

@Serializable
internal data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitlesSearchItem> = emptyList(),
)

@Serializable
internal data class OpenSubtitlesSearchItem(
    val id: String = "",
    val attributes: OpenSubtitlesAttributes = OpenSubtitlesAttributes(),
)

@Serializable
internal data class OpenSubtitlesAttributes(
    val language: String? = null,
    val release: String? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("ai_translated") val aiTranslated: Boolean = false,
    @SerialName("machine_translated") val machineTranslated: Boolean = false,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("from_trusted") val fromTrusted: Boolean = false,
    val files: List<OpenSubtitlesFile> = emptyList(),
)

@Serializable
internal data class OpenSubtitlesFile(
    @SerialName("file_id") val fileId: Long,
    @SerialName("file_name") val fileName: String? = null,
)

@Serializable
internal data class OpenSubtitlesDownloadResponse(
    val link: String? = null,
    val remaining: Int? = null,
    val message: String? = null,
)

internal sealed interface OpenSubtitlesKeyCheck {
    data object Valid : OpenSubtitlesKeyCheck
    data object Invalid : OpenSubtitlesKeyCheck
    /** Network or server problem: the key could not be checked. */
    data object Unknown : OpenSubtitlesKeyCheck
}

/** REST client of `api.opensubtitles.com` (v1). Only the API key is used, no user login. */
internal object OpenSubtitlesApi {
    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"

    private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun headers(apiKey: String): Map<String, String> = mapOf(
        "Api-Key" to apiKey,
        // OpenSubtitles requires "AppName vX.Y.Z" as User-Agent.
        "User-Agent" to "Nuvio v${AppVersionConfig.VERSION_NAME.ifBlank { "1.0.0" }}",
        "Accept" to "application/json",
    )

    /** Builds the search URL; parameters sorted and lowercase, as the API redirects otherwise. */
    internal fun searchUrl(query: OpenSubtitlesQuery, languages: List<String>, settings: OpenSubtitlesSettings): String {
        val params = buildMap {
            if (query.isEpisode) {
                query.imdbId?.let { put("parent_imdb_id", it.toString()) }
                query.tmdbId?.let { put("parent_tmdb_id", it.toString()) }
                put("season_number", query.season.toString())
                put("episode_number", query.episode.toString())
            } else {
                query.imdbId?.let { put("imdb_id", it.toString()) }
                query.tmdbId?.let { put("tmdb_id", it.toString()) }
            }
            if (languages.isNotEmpty()) put("languages", languages.sorted().joinToString(","))
            put("hearing_impaired", if (settings.includeHearingImpaired) "include" else "exclude")
            val translated = if (settings.includeMachineTranslated) "include" else "exclude"
            put("ai_translated", translated)
            put("machine_translated", translated)
        }
        return params.entries
            .sortedBy { it.key }
            .joinToString(separator = "&", prefix = "$BASE_URL/subtitles?") { (key, value) -> "$key=${value.lowercase()}" }
    }

    suspend fun search(
        apiKey: String,
        query: OpenSubtitlesQuery,
        languages: List<String>,
        settings: OpenSubtitlesSettings,
    ): List<OpenSubtitlesSearchItem> {
        val response = httpRequestRaw(
            method = "GET",
            url = searchUrl(query, languages, settings),
            headers = headers(apiKey),
            body = "",
            maxResponseBodyBytes = 4 * 1024 * 1024,
        )
        if (response.status !in 200..299) {
            throw OpenSubtitlesException(response.status, response.body.take(200))
        }
        return json.decodeFromString<OpenSubtitlesSearchResponse>(response.body).data
    }

    /** Asks for the temporary download link of [fileId]; every call counts against the daily quota. */
    suspend fun downloadLink(apiKey: String, fileId: Long): OpenSubtitlesDownloadResponse {
        val body = buildJsonObject {
            put("file_id", fileId)
            put("sub_format", "srt")
        }.toString()
        val response = httpRequestRaw(
            method = "POST",
            url = "$BASE_URL/download",
            headers = headers(apiKey) + ("Content-Type" to "application/json"),
            body = body,
        )
        val parsed = runCatching { json.decodeFromString<OpenSubtitlesDownloadResponse>(response.body) }.getOrNull()
        if (response.status !in 200..299) {
            throw OpenSubtitlesException(response.status, parsed?.message ?: response.body.take(200))
        }
        return parsed ?: throw OpenSubtitlesException(response.status, "Empty download response")
    }

    /** Downloads the subtitle text from a link returned by [downloadLink]. */
    suspend fun downloadFile(link: String): String {
        val response = httpRequestRaw(
            method = "GET",
            url = link,
            headers = mapOf(
                "User-Agent" to headers("").getValue("User-Agent"),
                "Accept" to "*/*",
            ),
            body = "",
            maxResponseBodyBytes = 8 * 1024 * 1024,
        )
        if (response.status !in 200..299) {
            throw OpenSubtitlesException(response.status, response.body.take(200))
        }
        return response.body
    }

    suspend fun verifyApiKey(apiKey: String): OpenSubtitlesKeyCheck = try {
        val response = httpRequestRaw(
            method = "GET",
            url = "$BASE_URL/infos/formats",
            headers = headers(apiKey),
            body = "",
        )
        when (response.status) {
            in 200..299 -> OpenSubtitlesKeyCheck.Valid
            401, 403 -> OpenSubtitlesKeyCheck.Invalid
            else -> {
                log.w { "API key check returned HTTP ${response.status}" }
                OpenSubtitlesKeyCheck.Unknown
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        log.w(error) { "API key check failed" }
        OpenSubtitlesKeyCheck.Unknown
    }
}

internal class OpenSubtitlesException(
    val status: Int,
    detail: String,
) : Exception("OpenSubtitles HTTP $status: $detail")
