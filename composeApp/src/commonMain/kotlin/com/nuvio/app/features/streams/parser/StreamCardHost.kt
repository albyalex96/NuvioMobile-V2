package com.nuvio.app.features.streams.parser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.streams.LocalStreamHighlight
import com.nuvio.app.features.streams.StreamBadgePlacement
import com.nuvio.app.features.streams.StreamCard
import com.nuvio.app.features.streams.StreamItem

/**
 * Drop-in replacement for [StreamCard]: renders the Stream Parser card when the feature is
 * enabled in Settings → Streams, the classic card otherwise. Same parameters as [StreamCard]
 * so existing call sites only change the function name.
 */
@Composable
internal fun StreamCardHost(
    stream: StreamItem,
    enabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    showFileSizeBadges: Boolean,
    showAddonLogo: Boolean,
    badgePlacement: StreamBadgePlacement,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    currentLabel: String? = null,
    outlined: Boolean = false,
) {
    val settings by remember {
        StreamParserSettingsRepository.ensureLoaded()
        StreamParserSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    if (!settings.enabled) {
        StreamCard(
            stream = stream,
            enabled = enabled,
            appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
            showFileSizeBadges = showFileSizeBadges,
            showAddonLogo = showAddonLogo,
            badgePlacement = badgePlacement,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            isCurrent = isCurrent,
            currentLabel = currentLabel,
            outlined = outlined,
        )
        return
    }

    val context = StreamParserContextStore.current
    val info = remember(stream, context.title) { GenericStreamParser.parse(stream, context.title) }
    // Same highlight the classic card applies (e.g. the watch party's source).
    val highlightLabel = if (isCurrent) null else LocalStreamHighlight.current(stream)
    StreamParserCard(
        stream = stream,
        info = info,
        context = context,
        animationsEnabled = settings.animationsEnabled,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        isCurrent = isCurrent || outlined || highlightLabel != null,
        currentLabel = if (isCurrent) currentLabel else highlightLabel,
    )
}
