package com.nuvio.app.features.streams.parser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.i18n.localizedByteUnit
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamBadgeImage
import com.nuvio.app.features.streams.StreamItem
import kotlin.math.round
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_parser_direct
import nuvio.composeapp.generated.resources.stream_parser_episode
import nuvio.composeapp.generated.resources.stream_parser_season
import org.jetbrains.compose.resources.stringResource

private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
private const val BYTES_PER_MIB = 1024.0 * 1024.0

/**
 * Stream Parser card: header (quality badge + series/movie title, episode title beside it),
 * centre band (technical chips and season/episode chips on the left, animated status badges
 * on the right) and a footer (size and audio languages on the left, source on the right).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamParserCard(
    stream: StreamItem,
    info: StreamParserInfo,
    context: StreamParserContext,
    animationsEnabled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    currentLabel: String? = null,
) {
    val tokens = MaterialTheme.nuvio
    val cardShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(cardShape)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
            )
            .then(
                if (isCurrent) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.52f), cardShape)
                } else {
                    Modifier
                },
            )
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        // Header: quality badge and title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreamParserQualityBadge(info.resolution, animationsEnabled)
            Spacer(Modifier.width(10.dp))
            Text(
                text = buildAnnotatedString {
                    append(context.title?.takeIf { it.isNotBlank() } ?: stream.cleanStreamLabel())
                    // Episode title sits next to the series name, lighter and smaller.
                    context.episodeTitle?.let { episodeTitle ->
                        append("  ")
                        withStyle(
                            SpanStyle(
                                color = tokens.colors.textMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(episodeTitle)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                color = tokens.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isCurrent && !currentLabel.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                CurrentPill(currentLabel)
            }
        }

        // Centre: chips on the left, status badges on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val imageBadges = stream.badges.filter { it.imageURL.isNotBlank() }
                if (info.tags.isNotEmpty() || imageBadges.isNotEmpty() || !info.isTorrent) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        info.tags.forEach { tag -> StreamParserTechChip(tag) }
                        if (info.tags.isEmpty() && !info.isTorrent) {
                            StreamParserOutlinedChip(
                                label = stringResource(Res.string.stream_parser_direct),
                                icon = Icons.Rounded.Link,
                                color = StreamParserDirectChipColor,
                            )
                        }
                        imageBadges.forEach { badge -> StreamBadgeImage(badge) }
                    }
                }

                if (context.isEpisode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        StreamParserOutlinedChip(
                            label = stringResource(Res.string.stream_parser_season, context.seasonNumber.twoDigits()),
                            icon = Icons.Rounded.VideoLibrary,
                            color = StreamParserEpisodeChipColor,
                        )
                        StreamParserOutlinedChip(
                            label = stringResource(Res.string.stream_parser_episode, context.episodeNumber.twoDigits()),
                            icon = Icons.Rounded.PlayCircle,
                            color = StreamParserEpisodeChipColor,
                        )
                    }
                }
            }

            if (info.isCached || info.isTorrent || info.isProxied) {
                Spacer(Modifier.width(10.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (info.isCached) StreamParserCachedBadge(animationsEnabled)
                    if (info.isTorrent) StreamParserTorrentBadge(animationsEnabled)
                    if (info.isProxied) StreamParserProxiedBadge(animationsEnabled)
                }
            }
        }

        // Footer: size and languages, source on the right.
        StreamParserFooter(stream, info, context)
    }
}

@Composable
private fun StreamParserFooter(stream: StreamItem, info: StreamParserInfo, context: StreamParserContext) {
    val muted = MaterialTheme.nuvio.colors.textMuted
    val estimatedBytes = remember(stream, info, context.isEpisode) {
        if (info.sizeBytes != null) null else StreamParserSizeEstimator.estimateBytes(info, context.isEpisode, stream.seedKey())
    }
    val size = formatSize(info.sizeBytes ?: estimatedBytes ?: 0L, estimated = info.sizeBytes == null)
    val languages = info.languages.map { it.localizedName() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Storage,
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    if (size.estimated) append("~")
                    append(size.value)
                    append(' ')
                    append(size.unit)
                }
                if (languages.isNotEmpty()) {
                    append(FOOTER_SEPARATOR)
                    append(languages.joinToString(", "))
                }
            },
            modifier = Modifier.weight(1f),
            color = muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        info.sourceLabel?.let { source ->
            Spacer(Modifier.width(12.dp))
            Text(
                text = source,
                modifier = Modifier.widthIn(max = 150.dp),
                color = muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CurrentPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Plain spacing between size and languages: no visible separator. */
private const val FOOTER_SEPARATOR = "    "

private data class SizeText(val value: String, val unit: String, val estimated: Boolean)

/** Real sizes keep up to two decimals below 10 GB; estimates always use at most one. */
private fun formatSize(bytes: Long, estimated: Boolean): SizeText {
    val gib = bytes / BYTES_PER_GIB
    return if (gib >= 1.0) {
        val decimals = if (!estimated && gib < 10.0) 2 else 1
        SizeText(gib.formatDecimals(decimals), localizedByteUnit("GB"), estimated)
    } else {
        val mib = bytes / BYTES_PER_MIB
        SizeText(round(mib).toLong().toString(), localizedByteUnit("MB"), estimated)
    }
}

private fun Double.formatDecimals(decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(this * factor).toLong()
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

private fun Int?.twoDigits(): String = (this ?: 0).toString().padStart(2, '0')

/** Stable key for the size estimate: identical across refreshes of the same stream. */
private fun StreamItem.seedKey(): String =
    p2pInfoHash ?: directPlaybackUrl ?: listOfNotNull(name, description, addonId).joinToString("|")

/** Stream name without leading emoji/symbols, used when no content title is available. */
private fun StreamItem.cleanStreamLabel(): String =
    streamLabel.dropWhile { !it.isLetterOrDigit() }.trim().ifEmpty { streamLabel }
