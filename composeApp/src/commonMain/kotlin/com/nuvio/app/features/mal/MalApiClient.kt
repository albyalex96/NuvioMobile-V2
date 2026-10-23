package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter

private const val MAL_MAX_RESPONSE_BODY_BYTES = 8 * 1024 * 1024

internal enum class MalHttpMethod {
    GET,
    PUT,
    PATCH,
    DELETE,
}

internal class MalApiException(
    val status: Int?,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun interface MalHttpEngine {
    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): RawHttpResponse
}

/**
 * Authenticated calls to the MAL v2 API. A 401 triggers one token refresh and a single retry;
 * a second 401 means the authorization is gone and [onUnauthorized] disconnects the account.
 */
internal class MalApiClient(
    private val engine: MalHttpEngine,
    private val accessToken: suspend () -> String?,
    private val refreshAccessToken: suspend (rejectedToken: String) -> String?,
    private val onUnauthorized: () -> Unit,
) {
    private val log = Logger.withTag("MalApi")

    /** Like [execute], but a 404 (the anime is not on the list) counts as success. */
    suspend fun executeAllowingMissing(
        method: MalHttpMethod,
        path: String,
    ): RawHttpResponse = execute(method, path, allowNotFound = true)

    suspend fun execute(
        method: MalHttpMethod,
        path: String,
        query: Map<String, String> = emptyMap(),
        formBody: String = "",
        allowNotFound: Boolean = false,
    ): RawHttpResponse {
        val token = accessToken() ?: throw MalApiException(status = null, message = "MAL account not connected")
        val url = malApiUrl(path, query)
        val first = send(method, url, token, formBody)
        val response = if (first.status == 401) {
            log.i { "MAL returned 401 for ${method.name} $path, refreshing the access token" }
            val refreshed = refreshAccessToken(token)
            if (refreshed == null) {
                onUnauthorized()
                throw MalApiException(status = 401, message = "MAL authorization rejected")
            }
            send(method, url, refreshed, formBody).also { retried ->
                if (retried.status == 401) {
                    onUnauthorized()
                    throw MalApiException(status = 401, message = "MAL authorization rejected")
                }
            }
        } else {
            first
        }
        log.d { "MAL HTTP ${method.name} $path status=${response.status} bodyChars=${response.body.length}" }
        if (response.status !in 200..299 && !(allowNotFound && response.status == 404)) {
            throw MalApiException(status = response.status, message = "MAL HTTP ${response.status} for ${method.name} $path")
        }
        return response
    }

    private suspend fun send(
        method: MalHttpMethod,
        url: String,
        token: String,
        formBody: String,
    ): RawHttpResponse = engine.execute(
        method = method.name,
        url = url,
        headers = buildMap {
            put("Authorization", "Bearer $token")
            put("Accept", "application/json")
            if (formBody.isNotEmpty()) put("Content-Type", "application/x-www-form-urlencoded")
        },
        body = formBody,
    )
}

internal fun malApiUrl(path: String, query: Map<String, String>): String = buildString {
    append(MAL_API_BASE_URL)
    append(if (path.startsWith('/')) path else "/$path")
    if (query.isNotEmpty()) {
        append('?')
        append(
            query.entries.joinToString("&") { (key, value) ->
                "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
            },
        )
    }
}

internal val malHttpEngine = MalHttpEngine { method, url, headers, body ->
    httpRequestRaw(
        method = method,
        url = url,
        headers = headers,
        body = body,
        maxResponseBodyBytes = MAL_MAX_RESPONSE_BODY_BYTES,
    )
}

internal object MalApi {
    val client: MalApiClient by lazy {
        MalApiClient(
            engine = malHttpEngine,
            accessToken = MalAuthRepository::validAccessToken,
            refreshAccessToken = MalAuthRepository::refreshAfterRejection,
            onUnauthorized = MalAuthRepository::onUnauthorizedResponse,
        )
    }
}
