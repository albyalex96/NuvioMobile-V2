package com.nuvio.app.features.livetv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User choices about which Live TV channels are shown and in which order.
 *
 * Channels are identified by [liveTvChannelPreferenceKey] and categories by
 * [liveTvCategoryKey], both stable across playlist refreshes. [channelOrder] is a prefix of the
 * arranged list: listed channels come first in that order, every other channel follows sorted by
 * name (the default order of the Live TV screen). Only the prefix touched by a drag is stored, so
 * moving a few channels to the top keeps the synced blob small even with huge playlists.
 */
@Serializable
data class LiveTvChannelPreferences(
    val hiddenChannelKeys: Set<String> = emptySet(),
    val hiddenCategoryKeys: Set<String> = emptySet(),
    val channelOrder: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = hiddenChannelKeys.isEmpty() && hiddenCategoryKeys.isEmpty() && channelOrder.isEmpty()

    fun isChannelHidden(channel: LiveTvChannel): Boolean =
        liveTvChannelPreferenceKey(channel) in hiddenChannelKeys

    fun isCategoryHidden(channel: LiveTvChannel): Boolean =
        liveTvCategoryKey(channel.group) in hiddenCategoryKeys

    fun isVisible(channel: LiveTvChannel): Boolean =
        !isChannelHidden(channel) && !isCategoryHidden(channel)

    fun signature(): String = listOf(
        hiddenChannelKeys.sorted().joinToString(","),
        hiddenCategoryKeys.sorted().joinToString(","),
        channelOrder.joinToString(","),
    ).joinToString("|").let { "${it.length}:${it.hashCode()}" }
}

/**
 * Stable key of a channel. M3U ids end with the channel's position in the playlist
 * (`live:<hash>:<index>`), which shifts when the playlist changes: the position is dropped,
 * the stream URL hash alone is unique because channels are deduplicated by URL.
 */
fun liveTvChannelPreferenceKey(channel: LiveTvChannel): String {
    val id = channel.id
    if (!id.startsWith("live:")) return id
    return id.substringBeforeLast(':').takeIf { it.count { char -> char == ':' } >= 1 } ?: id
}

/** Category key: trimmed, lower-cased group name; uncategorized channels share the empty key. */
fun liveTvCategoryKey(group: String?): String = group?.trim()?.lowercase().orEmpty()

/** Every channel in the user's order (hidden ones included). Default: sorted by name. */
fun arrangeLiveTvChannels(
    channels: List<LiveTvChannel>,
    preferences: LiveTvChannelPreferences,
): List<LiveTvChannel> {
    val byName = channels.sortedBy { it.name.lowercase() }
    if (preferences.channelOrder.isEmpty()) return byName
    val byKey = byName.groupBy(::liveTvChannelPreferenceKey)
    val placed = HashSet<String>()
    val ordered = ArrayList<LiveTvChannel>(byName.size)
    preferences.channelOrder.forEach { key ->
        if (placed.add(key)) byKey[key]?.let(ordered::addAll)
    }
    byName.forEach { channel -> if (liveTvChannelPreferenceKey(channel) !in placed) ordered += channel }
    return ordered
}

/**
 * Moves [fromKey] to the position of [toKey] inside the [arranged] list and returns the order
 * prefix to store. Works on the full list, so it is correct while the UI shows a filtered view.
 */
internal fun moveLiveTvChannelOrder(
    arranged: List<LiveTvChannel>,
    currentOrder: List<String>,
    fromKey: String,
    toKey: String,
): List<String> {
    val keys = arranged.map(::liveTvChannelPreferenceKey).toMutableList()
    val fromIndex = keys.indexOf(fromKey)
    val toIndex = keys.indexOf(toKey)
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return currentOrder
    keys.add(toIndex, keys.removeAt(fromIndex))
    val presentOrderLength = currentOrder.count { it in keys }
    val prefixLength = maxOf(presentOrderLength, maxOf(fromIndex, toIndex) + 1)
    val prefix = keys.take(prefixLength)
    // Keys of channels missing right now (playlist offline) keep their place at the end.
    val missing = currentOrder.filterNot { it in keys }
    return prefix + missing
}

private val preferencesJson = Json { ignoreUnknownKeys = true }

internal fun encodeLiveTvChannelPreferences(preferences: LiveTvChannelPreferences): String =
    preferencesJson.encodeToString(LiveTvChannelPreferences.serializer(), preferences)

internal fun decodeLiveTvChannelPreferences(raw: String?): LiveTvChannelPreferences? {
    if (raw.isNullOrBlank()) return null
    return runCatching { preferencesJson.decodeFromString(LiveTvChannelPreferences.serializer(), raw) }.getOrNull()
}
