package com.nuvio.app.features.streams.parser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem

/** `addonId` of the single group that holds every stream when sorting by quality. */
private const val QUALITY_SORTED_GROUP_ID = "stream-parser:quality-sorted"

/**
 * "Sort by video quality": streams from every addon and plugin in one list, highest
 * resolution first (8K, 4K, 1440p, 1080p, 720p, SD). The sort is stable, so streams with the
 * same resolution keep their original order. Resolution comes from [GenericStreamParser].
 */
internal object StreamQualitySorting {
    /** Drops the streams whose detected resolution is in [hidden]. */
    fun filterStreams(
        streams: List<StreamItem>,
        knownTitle: String?,
        hidden: Set<StreamParserResolution>,
    ): List<StreamItem> {
        if (hidden.isEmpty()) return streams
        return streams.filter { GenericStreamParser.parse(it, knownTitle).resolution !in hidden }
    }

    /**
     * Applies the quality filter to every group. Groups left without streams are dropped,
     * unless they are still loading or carry an error to show.
     */
    fun filterGroups(
        groups: List<AddonStreamGroup>,
        knownTitle: String?,
        hidden: Set<StreamParserResolution>,
    ): List<AddonStreamGroup> {
        if (hidden.isEmpty()) return groups
        return groups.mapNotNull { group ->
            val streams = filterStreams(group.streams, knownTitle, hidden)
            when {
                streams.size == group.streams.size -> group
                streams.isEmpty() && !group.isLoading && group.error == null -> null
                else -> group.copy(streams = streams)
            }
        }
    }

    fun sortStreams(streams: List<StreamItem>, knownTitle: String?): List<StreamItem> =
        streams.sortedBy { GenericStreamParser.parse(it, knownTitle).resolution.ordinal }

    /** Merges [groups] into a single headerless group sorted by quality. */
    fun mergeGroups(groups: List<AddonStreamGroup>, knownTitle: String?): List<AddonStreamGroup> {
        if (groups.isEmpty()) return groups
        return listOf(
            AddonStreamGroup(
                addonName = "",
                addonId = QUALITY_SORTED_GROUP_ID,
                streams = sortStreams(groups.flatMap { it.streams }, knownTitle),
                isLoading = groups.any { it.isLoading },
            ),
        )
    }
}

/** True for the merged group produced by quality sorting: no addon header, no source sub-headers. */
internal val AddonStreamGroup.isQualitySortedGroup: Boolean
    get() = addonId == QUALITY_SORTED_GROUP_ID

@Composable
private fun rememberStreamParserSettings(): StreamParserSettingsUiState {
    val settings by remember {
        StreamParserSettingsRepository.ensureLoaded()
        StreamParserSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    return settings
}

/**
 * Stream groups to render: hidden qualities removed, then merged and sorted by quality when
 * the toggle is on.
 */
@Composable
internal fun rememberQualitySortedGroups(groups: List<AddonStreamGroup>): List<AddonStreamGroup> {
    val settings = rememberStreamParserSettings()
    val enabled = settings.sortByQuality
    val hidden = settings.hiddenQualities
    val title = StreamParserContextStore.current.title
    return remember(groups, enabled, hidden, title) {
        val visible = StreamQualitySorting.filterGroups(groups, title, hidden)
        if (enabled) StreamQualitySorting.mergeGroups(visible, title) else visible
    }
}

/** Flat stream list to render: hidden qualities removed, then sorted by quality when the toggle is on. */
@Composable
internal fun rememberQualitySortedStreams(streams: List<StreamItem>): List<StreamItem> {
    val settings = rememberStreamParserSettings()
    val enabled = settings.sortByQuality
    val hidden = settings.hiddenQualities
    val title = StreamParserContextStore.current.title
    return remember(streams, enabled, hidden, title) {
        val visible = StreamQualitySorting.filterStreams(streams, title, hidden)
        if (enabled) StreamQualitySorting.sortStreams(visible, title) else visible
    }
}
