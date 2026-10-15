package com.nuvio.app.features.streams.parser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf

/**
 * What the stream list is about: the same for every card, so it is published once by the
 * hosting screen instead of being parsed from each stream.
 */
@Immutable
data class StreamParserContext(
    /** Movie title or series name. */
    val title: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
) {
    val isEpisode: Boolean
        get() = seasonNumber != null && episodeNumber != null
}

/**
 * Stack of contexts published by the visible screens (streams screen, player panels).
 * The top entry wins; a missing title is inherited from the entries below, so a nested
 * panel only needs to publish the episode it shows.
 */
internal object StreamParserContextStore {
    private class Entry(val context: StreamParserContext)

    private val entries = mutableStateListOf<Entry>()

    val current: StreamParserContext
        get() {
            val top = entries.lastOrNull()?.context ?: return StreamParserContext()
            val title = top.title?.takeIf { it.isNotBlank() }
                ?: entries.asReversed().firstNotNullOfOrNull { entry -> entry.context.title?.takeIf { it.isNotBlank() } }
            return top.copy(title = title)
        }

    fun push(context: StreamParserContext): Any = Entry(context).also { entries += it }

    fun remove(token: Any) {
        (token as? Entry)?.let(entries::remove)
    }
}

/** Publishes the content shown by the current stream list for as long as the caller is composed. */
@Composable
fun StreamParserContextEffect(
    title: String?,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
) {
    DisposableEffect(title, seasonNumber, episodeNumber, episodeTitle) {
        val token = StreamParserContextStore.push(
            StreamParserContext(
                title = title,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle?.takeIf { it.isNotBlank() },
            ),
        )
        onDispose { StreamParserContextStore.remove(token) }
    }
}
