package com.nuvio.app.core.mediaproxy

import java.net.URI

private val HLS_URI_ATTRIBUTE = Regex("URI=\"([^\"]*)\"")
private val DASH_BASE_URL = Regex("<BaseURL([^>]*)>([^<]*)</BaseURL>")
private val DASH_URL_ATTRIBUTE = Regex("\\b(media|initialization|sourceURL)=\"(https?://[^\"]*)\"")
private val DASH_MPD_OPEN_TAG = Regex("<MPD\\b[^>]*>")

/**
 * Resolves [reference] against [baseUrl] (RFC 3986). Returns null when either URL cannot be
 * parsed, so the caller keeps the original text.
 */
internal fun resolveUrl(baseUrl: String, reference: String): String? = runCatching {
    URI(baseUrl).resolve(URI(reference.trim())).toString()
}.getOrNull()

private fun String.isHttpUrl(): Boolean = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/**
 * Routes every URI of an HLS playlist (variants, segments, keys, init sections, renditions)
 * through [proxify]. Relative URIs are resolved against [baseUrl], the final URL of the
 * playlist after redirects; non-HTTP URIs (data:, skd:) are left untouched.
 */
internal fun rewriteHlsPlaylist(text: String, baseUrl: String, proxify: (String) -> String): String {
    fun route(reference: String): String {
        val absolute = resolveUrl(baseUrl, reference) ?: return reference
        return if (absolute.isHttpUrl()) proxify(absolute) else reference
    }
    return text.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> line
            trimmed.startsWith("#") -> HLS_URI_ATTRIBUTE.replace(line) { match ->
                "URI=\"${route(match.groupValues[1])}\""
            }
            else -> route(trimmed)
        }
    }
}

/**
 * Routes the absolute URLs of a DASH manifest through [proxify]. Relative URLs keep working
 * because the receiver resolves them against the proxied manifest URL; when the manifest was
 * redirected ([baseUrl] differs from [requestedUrl]) and declares no BaseURL, one pointing to
 * the final location is added so they still resolve to the right server.
 */
internal fun rewriteDashManifest(
    text: String,
    requestedUrl: String,
    baseUrl: String,
    proxify: (String) -> String,
): String {
    var rewritten = DASH_BASE_URL.replace(text) { match ->
        val value = match.groupValues[2].trim()
        if (value.isHttpUrl()) "<BaseURL${match.groupValues[1]}>${proxify(value)}</BaseURL>" else match.value
    }
    rewritten = DASH_URL_ATTRIBUTE.replace(rewritten) { match ->
        "${match.groupValues[1]}=\"${proxify(match.groupValues[2])}\""
    }
    if (baseUrl != requestedUrl && !rewritten.contains("<BaseURL")) {
        val baseDirectory = resolveUrl(baseUrl, ".") ?: return rewritten
        rewritten = DASH_MPD_OPEN_TAG.replaceFirst(rewritten) { match ->
            "${match.value}<BaseURL>${proxify(baseDirectory)}</BaseURL>"
        }
    }
    return rewritten
}

private fun Regex.replaceFirst(input: String, transform: (MatchResult) -> String): String {
    val match = find(input) ?: return input
    return input.replaceRange(match.range, transform(match))
}
