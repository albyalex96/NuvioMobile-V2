package com.nuvio.app.core.deeplink

import com.nuvio.app.core.tracking.ensureTrackingProvidersRegistered
import com.nuvio.app.features.party.PARTY_CODE_LENGTH
import com.nuvio.app.features.party.normalizePartyCode
import com.nuvio.app.features.player.infusePlaybackCallbacks
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface AppDeepLink {
    data class Meta(
        val type: String,
        val id: String,
    ) : AppDeepLink

    data class AddonInstall(
        val manifestUrl: String,
    ) : AppDeepLink

    data object Downloads : AppDeepLink

    data class Party(
        val code: String,
    ) : AppDeepLink

    /** A search started from outside the app (Android TV voice search / Google Assistant). */
    data class Search(
        val query: String,
    ) : AppDeepLink

    /** A "continue watching" entry opened from outside the app (Android TV launcher). */
    data class ContinueWatching(
        val item: ContinueWatchingItem,
        val action: ContinueWatchingLaunchAction,
    ) : AppDeepLink
}

/** What opening a [buildContinueWatchingDeepLinkUrl] link does. */
enum class ContinueWatchingLaunchAction(val urlValue: String) {
    /** Resumes like a tap on the in-app card. */
    Play("play"),
    /** Opens the stream list of the episode/movie without autoplay. */
    ChooseSource("sources"),
    /** Shows the card options menu (resume, choose source, details…). */
    Menu("menu"),
    Details("details"),
    ;

    companion object {
        fun fromUrlValue(value: String?): ContinueWatchingLaunchAction =
            entries.firstOrNull { it.urlValue.equals(value?.trim(), ignoreCase = true) } ?: Play
    }
}

internal object AppDeepLinkRepository {
    private val _pendingDeepLink = MutableStateFlow<AppDeepLink?>(null)
    val pendingDeepLink: StateFlow<AppDeepLink?> = _pendingDeepLink.asStateFlow()

    fun handleUrl(url: String) {
        parseAppDeepLink(url)?.let { deepLink ->
            _pendingDeepLink.value = deepLink
        }
    }

    fun markConsumed(deepLink: AppDeepLink) {
        if (_pendingDeepLink.value == deepLink) {
            _pendingDeepLink.value = null
        }
    }
}

fun handleAppUrl(url: String) {
    val normalizedUrl = url.trim()
    if (normalizedUrl.isBlank()) return
    if (infusePlaybackCallbacks.handleUrl(normalizedUrl)) return

    ensureTrackingProvidersRegistered()
    TrackingProviderRegistry.handleAuthCallback(normalizedUrl)
    AppDeepLinkRepository.handleUrl(normalizedUrl)
}

fun buildMetaDeepLinkUrl(
    type: String,
    id: String,
): String = buildString {
    append("nuvio://meta?type=")
    append(type.trim().encodeURLParameter())
    append("&id=")
    append(id.trim().encodeURLParameter())
}

fun buildDownloadsDeepLinkUrl(): String = "nuvio://downloads"

fun buildSearchDeepLinkUrl(query: String): String =
    "nuvio://search?q=${query.trim().encodeURLParameter()}"

/**
 * `nuvio://continue?...` link of a continue-watching entry. It carries everything needed to
 * start playback, because the app may be cold started from the link (Android TV launcher).
 */
fun buildContinueWatchingDeepLinkUrl(
    item: ContinueWatchingItem,
    action: ContinueWatchingLaunchAction,
): String {
    val parameters = listOf(
        "type" to item.parentMetaType,
        "id" to item.parentMetaId,
        "video" to item.videoId,
        "s" to item.seasonNumber?.toString(),
        "e" to item.episodeNumber?.toString(),
        "title" to item.title,
        "ep" to item.episodeTitle,
        "poster" to item.poster,
        "bg" to item.background,
        "logo" to item.logo,
        "thumb" to item.episodeThumbnail,
        "pos" to item.resumePositionMs.takeIf { it > 0L }?.toString(),
        "frac" to item.resumeProgressFraction?.toString(),
        "dur" to item.durationMs.takeIf { it > 0L }?.toString(),
        "progress" to item.progressFraction.takeIf { it > 0f }?.toString(),
        "next" to if (item.isNextUp) "1" else null,
        "action" to action.urlValue,
    )
    return parameters
        .mapNotNull { (key, value) -> value?.takeIf(String::isNotBlank)?.let { "$key=${it.encodeURLParameter()}" } }
        .joinToString(separator = "&", prefix = "nuvio://continue?")
}

internal fun parseAppDeepLink(url: String): AppDeepLink? {
    val parsedUrl = runCatching { Url(url) }.getOrNull() ?: return null
    val scheme = parsedUrl.protocol.name.lowercase()
    if (scheme == "stremio") {
        return if (looksLikeAddonHost(parsedUrl.host.lowercase())) {
            customSchemeToHttpsUrl(url, scheme)?.let(AppDeepLink::AddonInstall)
        } else {
            null
        }
    }
    val host = parsedUrl.host.lowercase()
    val pathSegments = parsedUrl.pathSegments.map(String::trim).filter(String::isNotBlank)
    if (scheme == "https") {
        return if (host == "nuvio.tv" && pathSegments.firstOrNull() == "party") partyDeepLink(pathSegments.getOrNull(1)) else null
    }
    if (scheme != "nuvio") return null

    return when (host) {
        "meta" -> {
            parseMetaFromParameters(parsedUrl)
                ?: parseMetaFromPath(pathSegments)
        }

        "detail", "details", "open", "watch" -> parseMetaFromPath(pathSegments)

        "movie", "movies", "series", "show", "shows", "tv" -> {
            val type = normalizeDeepLinkMediaType(host)
            val id = pathSegments.firstOrNull()?.let(::normalizeDeepLinkId).orEmpty()
            if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
        }

        "imdb", "tmdb" -> parseProviderMetaDeepLink(host, pathSegments, parsedUrl)

        "downloads" -> AppDeepLink.Downloads

        "party" -> partyDeepLink(pathSegments.firstOrNull() ?: firstParameter(parsedUrl, "code"))

        "continue" -> parseContinueWatchingDeepLink(parsedUrl)

        "search" -> (firstParameter(parsedUrl, "q", "query") ?: pathSegments.firstOrNull())
            ?.let(::cleanExternalSearchQuery)
            ?.takeIf(String::isNotBlank)
            ?.let(AppDeepLink::Search)

        "auth" -> null

        else -> {
            if (looksLikeAddonHost(host)) {
                customSchemeToHttpsUrl(url, scheme)?.let(AppDeepLink::AddonInstall)
            } else {
                null
            }
        }
    }
}

/**
 * Assistant queries may still carry the app name ("criminal minds su nuvio"): it is not part
 * of the title, so it is dropped.
 */
internal fun cleanExternalSearchQuery(query: String): String =
    query.trim()
        .replace(externalSearchAppSuffix, "")
        .trim()

private val externalSearchAppSuffix =
    Regex("""\s+(?:su|on|in|con|with)\s+nuvio(?:\s+enhanced)?(?:\s+tv)?\s*$""", RegexOption.IGNORE_CASE)

private fun partyDeepLink(code: String?): AppDeepLink.Party? =
    code?.let(::normalizePartyCode)?.takeIf { it.length == PARTY_CODE_LENGTH }?.let(AppDeepLink::Party)

private fun parseContinueWatchingDeepLink(parsedUrl: Url): AppDeepLink.ContinueWatching? {
    fun parameter(key: String) = firstParameter(parsedUrl, key)
    val type = parameter("type") ?: return null
    val id = parameter("id") ?: return null
    val videoId = parameter("video") ?: id
    val poster = parameter("poster")
    val background = parameter("bg")
    val thumbnail = parameter("thumb")
    val item = ContinueWatchingItem(
        parentMetaId = id,
        parentMetaType = type,
        videoId = videoId,
        title = parameter("title").orEmpty(),
        subtitle = "",
        imageUrl = thumbnail ?: background ?: poster,
        logo = parameter("logo"),
        poster = poster,
        background = background,
        seasonNumber = parameter("s")?.toIntOrNull(),
        episodeNumber = parameter("e")?.toIntOrNull(),
        episodeTitle = parameter("ep"),
        episodeThumbnail = thumbnail,
        isNextUp = parameter("next") == "1",
        resumePositionMs = parameter("pos")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        resumeProgressFraction = parameter("frac")?.toFloatOrNull()?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f),
        durationMs = parameter("dur")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        progressFraction = parameter("progress")?.toFloatOrNull()?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: 0f,
    )
    return AppDeepLink.ContinueWatching(
        item = item,
        action = ContinueWatchingLaunchAction.fromUrlValue(parameter("action")),
    )
}

private fun parseMetaFromParameters(parsedUrl: Url): AppDeepLink.Meta? {
    val type = firstParameter(parsedUrl, "type", "mediaType", "media_type")
        ?.let(::normalizeDeepLinkMediaType)
        .orEmpty()
    val id = firstParameter(parsedUrl, "id", "imdb", "imdbId", "imdb_id")
        ?.let(::normalizeDeepLinkId)
        ?: firstParameter(parsedUrl, "tmdb", "tmdbId", "tmdb_id")
            ?.let { "tmdb:${it.removePrefix("tmdb:").trim()}" }
        ?: ""
    return if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
}

private fun parseMetaFromPath(pathSegments: List<String>): AppDeepLink.Meta? {
    if (pathSegments.size < 2) return null
    val type = normalizeDeepLinkMediaType(pathSegments[0])
    val id = normalizeDeepLinkId(pathSegments[1])
    return if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
}

private fun parseProviderMetaDeepLink(
    provider: String,
    pathSegments: List<String>,
    parsedUrl: Url,
): AppDeepLink.Meta? {
    val first = pathSegments.firstOrNull().orEmpty()
    val second = pathSegments.getOrNull(1).orEmpty()
    val firstAsType = normalizeDeepLinkMediaType(first)
    val queryType = firstParameter(parsedUrl, "type", "mediaType", "media_type")
        ?.let(::normalizeDeepLinkMediaType)
        .orEmpty()
    val type = firstAsType.ifBlank { queryType }
    val rawId = if (firstAsType.isNotBlank()) second else first
    val id = when (provider) {
        "tmdb" -> rawId.removePrefix("tmdb:").trim().takeIf(String::isNotBlank)?.let { "tmdb:$it" }
        else -> normalizeDeepLinkId(rawId).takeIf(String::isNotBlank)
    }.orEmpty()
    return if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
}

private fun firstParameter(parsedUrl: Url, vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        parsedUrl.parameters[key]?.trim()?.takeIf(String::isNotBlank)
    }

private fun normalizeDeepLinkMediaType(value: String): String =
    when (value.trim().lowercase()) {
        "movie", "movies", "film", "films" -> "movie"
        "series", "show", "shows", "tv", "tvshow", "tvshows" -> "series"
        else -> ""
    }

private fun normalizeDeepLinkId(value: String): String =
    value.trim()
        .removePrefix("imdb:")
        .takeIf(String::isNotBlank)
        .orEmpty()

private fun looksLikeAddonHost(host: String): Boolean =
    host.contains('.') ||
        host.equals("localhost", ignoreCase = true) ||
        host.any(Char::isDigit)

private fun customSchemeToHttpsUrl(url: String, scheme: String): String? {
    val prefix = "$scheme://"
    val rest = url.trim()
        .takeIf { it.startsWith(prefix, ignoreCase = true) }
        ?.substring(prefix.length)
        ?.takeIf { it.isNotBlank() && !it.startsWith("/") }
        ?: return null
    return "https://$rest"
}
