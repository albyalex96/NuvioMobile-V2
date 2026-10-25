package com.nuvio.app.features.player.skip

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpPostJsonWithHeaders
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException

internal object SkipIntroApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val ANIMESKIP_BASE = "https://api.anime-skip.com/"

    // --- IntroDb ---

    suspend fun getIntroDbMovieSegments(imdbId: String): IntroDbSegmentsResponse? {
        val baseUrl = IntroDbConfig.URL.trimEnd('/')
        if (baseUrl.isBlank()) return null
        return try {
            val text = httpGetText(introDbMovieSegmentsUrl(baseUrl, imdbId))
            json.decodeFromString<IntroDbSegmentsResponse>(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getIntroDbSegments(
        imdbId: String,
        season: Int,
        episode: Int,
    ): IntroDbSegmentsResponse? {
        val baseUrl = IntroDbConfig.URL.trimEnd('/')
        if (baseUrl.isBlank()) return null
        val url = "$baseUrl/segments?imdb_id=$imdbId&season=$season&episode=$episode"
        return try {
            val text = httpGetText(url)
            json.decodeFromString<IntroDbSegmentsResponse>(text)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun submitIntro(
        apiKey: String,
        request: SubmitIntroRequest,
    ): Boolean {
        val baseUrl = IntroDbConfig.URL.trimEnd('/')
        if (baseUrl.isBlank() || apiKey.isBlank()) return false
        val url = "$baseUrl/submit"
        val body = json.encodeToString(SubmitIntroRequest.serializer(), request)
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        return try {
            val response = com.nuvio.app.features.addons.httpRequestRaw(
                method = "POST",
                url = url,
                headers = headers,
                body = body
            )
            response.status == 200 || response.status == 201
        } catch (_: Exception) {
            false
        }
    }

    suspend fun verifyIntroDbApiKey(apiKey: String): Boolean {
        val baseUrl = IntroDbConfig.URL.trimEnd('/')
        if (baseUrl.isBlank() || apiKey.isBlank()) return false
        val url = "$baseUrl/submit"
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        return try {
            val response = com.nuvio.app.features.addons.httpRequestRaw(
                method = "POST",
                url = url,
                headers = headers,
                body = "{}"
            )
            
            // 400 means Auth passed but payload was empty/invalid -> Key is Valid
            if (response.status == 400) return true
            
            // 200/201 would also mean valid (though unexpected with empty body)
            if (response.status == 200 || response.status == 201) return true
            
            // Explicitly handle auth failures
            if (response.status == 401 || response.status == 403) return false
            
            false
        } catch (_: Exception) {
            false
        }
    }

    // --- SkipDB ---

    suspend fun getSkipDbSegments(
        imdbId: String,
        season: Int?,
        episode: Int?,
        durationSec: Double?,
        adjust: SkipDbAdjustMode,
    ): SkipDbSegmentsResponse? {
        val url = skipDbSegmentsUrl(imdbId, season, episode, durationSec, adjust)
        return try {
            json.decodeFromString<SkipDbSegmentsResponse>(httpGetText(url))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Returns the HTTP status, or null when the request could not be sent. */
    suspend fun submitSkipDbSegment(apiKey: String, request: SkipDbSubmitRequest): SkipDbSubmitResult? {
        if (apiKey.isBlank()) return null
        return try {
            val response = com.nuvio.app.features.addons.httpRequestRaw(
                method = "POST",
                url = SKIPDB_BASE + "api/segments",
                headers = skipDbAuthHeaders(apiKey),
                body = json.encodeToString(SkipDbSubmitRequest.serializer(), request),
            )
            val status = runCatching { json.decodeFromString<SkipDbSubmitResponse>(response.body).status }.getOrNull()
            SkipDbSubmitResult(httpStatus = response.status, status = status)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /**
     * SkipDB has no key check endpoint: an empty submission is rejected with 400/422 once the key is
     * accepted, and with 401/403 when it is not.
     */
    suspend fun verifySkipDbApiKey(apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        return try {
            val response = com.nuvio.app.features.addons.httpRequestRaw(
                method = "POST",
                url = SKIPDB_BASE + "api/segments",
                headers = skipDbAuthHeaders(apiKey),
                body = "{}",
            )
            response.status in setOf(200, 201, 400, 409, 422)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun skipDbAuthHeaders(apiKey: String) = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
    )

    // --- AniSkip ---

    suspend fun getAniSkipTimes(
        malId: String,
        episode: Int,
        episodeLengthSec: Double? = null,
    ): AniSkipResponse? {
        val url = aniSkipTimesUrl(malId, episode, episodeLengthSec)
        return try {
            val text = httpGetText(url)
            json.decodeFromString<AniSkipResponse>(text)
        } catch (_: Exception) {
            null
        }
    }

    // --- Anime-Skip GraphQL ---

    suspend fun queryAnimeSkip(clientId: String, graphqlQuery: String): AnimeSkipGraphqlResponse? {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("query", kotlinx.serialization.json.JsonPrimitive(graphqlQuery))
            }
        )
        val headers = mapOf(
            "X-Client-ID" to clientId,
            "Content-Type" to "application/json",
        )
        return try {
            val text = httpPostJsonWithHeaders(ANIMESKIP_BASE + "graphql", body, headers)
            json.decodeFromString<AnimeSkipGraphqlResponse>(text)
        } catch (_: Exception) {
            null
        }
    }
}

/** `episodeLength=0` asks AniSkip for every submission, whatever the length of the release it was recorded on. */
internal fun aniSkipTimesUrl(malId: String, episode: Int, episodeLengthSec: Double?): String {
    val types = "types=op&types=ed&types=recap&types=mixed-op&types=mixed-ed"
    val length = episodeLengthSec?.takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L
    return "${ANISKIP_BASE}skip-times/$malId/$episode?$types&episodeLength=$length"
}

internal data class SkipDbSubmitResult(
    val httpStatus: Int,
    /** `approved`, `pending`, `rejected` or `already_approved`. */
    val status: String?,
) {
    /** Accepted (even if pending review) or already present with the same timing. */
    val isAccepted: Boolean
        get() = httpStatus in 200..201 && status != "rejected" ||
            httpStatus == 409 && status == "already_approved"
}

private const val SKIPDB_BASE = "https://api.skipdb.tv/"
private const val ANISKIP_BASE = "https://api.aniskip.com/v2/"

internal fun skipDbSegmentsUrl(
    imdbId: String,
    season: Int?,
    episode: Int?,
    durationSec: Double?,
    adjust: SkipDbAdjustMode,
): String = buildString {
    append(SKIPDB_BASE).append("api/segments?imdb_id=").append(imdbId)
    if (season != null && episode != null) append("&season=").append(season).append("&episode=").append(episode)
    durationSec?.takeIf { it.isFinite() && it > 0 }?.let { append("&duration=").append(it.toLong()) }
    if (adjust != SkipDbAdjustMode.CONSERVATIVE) append("&adjust=").append(adjust.apiValue)
}

internal fun introDbMovieSegmentsUrl(baseUrl: String, imdbId: String): String =
    "${baseUrl.trimEnd('/')}/segments?imdb_id=$imdbId&is_movie=true"
