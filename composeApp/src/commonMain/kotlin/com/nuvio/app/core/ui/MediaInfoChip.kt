package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour and icon of an info chip (episode code, remaining time, release state...).
 * Colours are fixed (not the theme accent) so the chips stay readable on any artwork.
 */
enum class MediaInfoChipStyle(
    val topColor: Color,
    val bottomColor: Color,
    val icon: ImageVector,
) {
    EpisodeCode(Color(0xFF8B5CF6), Color(0xFF5B21B6), Icons.Rounded.Tv),
    TimeRemaining(Color(0xFF14B8A6), Color(0xFF0F766E), Icons.Rounded.HourglassBottom),
    WatchedPercent(Color(0xFF14B8A6), Color(0xFF0F766E), Icons.Rounded.Timelapse),
    UpNext(Color(0xFFF43F5E), Color(0xFF9F1239), Icons.Rounded.SkipNext),
    NewEpisode(Color(0xFF3B82F6), Color(0xFF1D4ED8), Icons.Rounded.NewReleases),
    NewSeason(Color(0xFFF59E0B), Color(0xFFB45309), Icons.Rounded.AutoAwesome),
    AirDate(Color(0xFF64748B), Color(0xFF334155), Icons.Rounded.Event),
}

@Composable
fun MediaInfoChip(
    text: String,
    style: MediaInfoChipStyle,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp,
) {
    val shape = RoundedCornerShape(radius)
    val iconSize = with(LocalDensity.current) { textSize.toDp() } * 1.15f
    Row(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(style.topColor, style.bottomColor)))
            .border(width = 0.75.dp, color = Color.White.copy(alpha = 0.22f), shape = shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(horizontalPadding * 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Episode code chip ("S01E02", "1x02"...), shared by Continue Watching and the episode lists. */
@Composable
fun EpisodeCodeChip(
    text: String,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp,
) {
    MediaInfoChip(
        text = text,
        style = MediaInfoChipStyle.EpisodeCode,
        textSize = textSize,
        modifier = modifier,
        radius = radius,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
    )
}
