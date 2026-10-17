package com.nuvio.tv.ui

import com.nuvio.app.core.ui.primaryActionContentColor
import com.nuvio.app.core.ui.primaryActionSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleUiState
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape

/**
 * Shared layout metrics of the TV screens (dp on a 960x540dp TV canvas), taken from the modern
 * home of NuvioTV: portrait cards = 126x189 base poster * 0.84 * 1.08, landscape and
 * "continue watching" cards = 126 * 1.24 * 1.34 at 16:9, rows inset 52dp and 12dp apart.
 */
object TvDimens {
    val screenPadding = 48.dp
    /** Start/end inset of the home and details rows (NuvioTV `rowHorizontalPadding`). */
    val rowPadding = 52.dp
    /** Space kept free at the top of the pages for the floating sidebar pill. */
    val chromeTopInset = 80.dp
    val rowSpacing = 12.dp
    /** Vertical gap between the rows of a page. */
    val rowGap = 24.dp
    /** Space above and below the cards of a row, room for the focus scale. */
    val rowVerticalPadding = 10.dp
    val posterWidth = 114.dp
    /** Posters outside the home rows (details "more like this", collections). */
    val basePosterWidth = 126.dp
    val landscapeWidth = 210.dp
    val cardRadius = 12.dp
}

val TvCardShape = RoundedCornerShape(TvDimens.cardRadius)
const val TvPosterAspectRatio = 2f / 3f

/** Square tiles are as tall as the posters of the same row. */
val TvSquareTileSize: Dp get() = TvDimens.posterWidth / TvPosterAspectRatio
val TvPillShape = RoundedCornerShape(50)

/** Poster settings of the profile (Settings → Appearance → Posters), shared with the phone. */
@Composable
fun rememberTvPosterCardStyle(): PosterCardStyleUiState {
    PosterCardStyleRepository.ensureLoaded()
    val style by PosterCardStyleRepository.uiState.collectAsState()
    return style
}

/** "Landscape posters" setting, or a catalog whose addon declares landscape artwork. */
fun PosterCardStyleUiState.showsLandscape(items: List<MetaPreview>): Boolean =
    catalogLandscapeModeEnabled || items.firstOrNull()?.posterShape == PosterShape.Landscape

/** Artwork of a landscape card, picked like the phone's `HomePosterCard`. */
data class TvLandscapeArtwork(val imageUrl: String?, val logoUrl: String?)

fun PosterCardStyleUiState.landscapeArtwork(item: MetaPreview): TvLandscapeArtwork {
    val customLandscape = if (alwaysShowLandscapeClearlogo) null else item.landscapePoster?.takeIf { it.isNotBlank() }
    return TvLandscapeArtwork(
        imageUrl = customLandscape ?: item.banner ?: item.poster,
        // A custom landscape poster already carries the title; plain backdrops get the clearlogo.
        logoUrl = if (customLandscape == null) item.logo?.takeIf { it.isNotBlank() } else null,
    )
}

@Composable
fun TvRemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    Box(modifier = modifier.background(MaterialTheme.nuvio.colors.surfaceCard)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TvSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        color = MaterialTheme.nuvio.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // 14dp to the cards (NuvioTV `rowTitleBottom`), including the row's vertical padding.
        modifier = modifier.padding(start = TvDimens.rowPadding, bottom = 14.dp - TvDimens.rowVerticalPadding),
    )
}

/** A titled horizontal row of focusable items that restores the last focused item. */
@Composable
fun TvContentRow(
    title: String?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            TvSectionTitle(title)
        }
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = TvDimens.rowPadding, vertical = TvDimens.rowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.rowSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            content = content,
        )
    }
}

@Composable
fun TvProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.nuvio.colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(TvPillShape)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(accent),
        )
    }
}

@Composable
fun TvPosterCard(
    imageUrl: String?,
    title: String,
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TvDimens.posterWidth,
    aspectRatio: Float = TvPosterAspectRatio,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .tvFocusable(
                    shape = TvCardShape,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onFocusChange = { hasFocus ->
                        focused = hasFocus
                        if (hasFocus) onFocused()
                    },
                ),
        ) {
            TvRemoteImage(url = imageUrl, modifier = Modifier.fillMaxSize())
            if (imageUrl.isNullOrBlank()) {
                CardFallbackTitle(title)
            }
            if (watched) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) MaterialTheme.nuvio.colors.textPrimary else MaterialTheme.nuvio.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun TvLandscapeCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TvDimens.landscapeWidth,
    progress: Float? = null,
    badge: String? = null,
    watched: Boolean = false,
    dimmed: Boolean = false,
    logoUrl: String? = null,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .tvFocusable(
                    shape = TvCardShape,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    focusedScale = 1.05f,
                    onFocusChange = { hasFocus ->
                        focused = hasFocus
                        if (hasFocus) onFocused()
                    },
                ),
        ) {
            TvRemoteImage(url = imageUrl, modifier = Modifier.fillMaxSize())
            if (imageUrl.isNullOrBlank()) {
                CardFallbackTitle(title)
            }
            if (dimmed) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight(0.34f)
                        .padding(start = 10.dp, bottom = 10.dp),
                )
            }
            if (badge != null) {
                TvBadge(text = badge, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            if (watched) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (progress != null && progress > 0f) {
                TvProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (focused) MaterialTheme.nuvio.colors.textPrimary else MaterialTheme.nuvio.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nuvio.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.CardFallbackTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.nuvio.colors.textSecondary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).padding(10.dp),
    )
}

@Composable
fun TvBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.nuvio.colors.onAccent,
        maxLines = 1,
        modifier = modifier
            .clip(TvPillShape)
            .background(MaterialTheme.nuvio.colors.accent)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.nuvio.colors.accent,
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f)),
    )
}

/** Pill button used for the main actions of the TV screens. */
@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.nuvio.colors
    val background = when {
        focused -> Color.White
        primary -> colors.accent
        selected -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val foreground = when {
        primary -> primaryActionContentColor(enabled)
        focused -> Color.Black
        else -> colors.textPrimary
    }
    Box(
        modifier = modifier
            .widthIn(min = if (icon != null && label.isEmpty()) 48.dp else 0.dp)
            .height(48.dp)
            .tvFocusable(
                shape = TvPillShape,
                onClick = onClick,
                onLongClick = onLongClick,
                focusedScale = 1.05f,
                borderWidth = 0.dp,
                enabled = enabled,
                onFocusChange = { focused = it },
            )
            .then(
                if (primary) {
                    // Main actions share the phone's gradient pill; the glow follows the focus.
                    Modifier.primaryActionSurface(shape = TvPillShape, enabled = enabled, glow = focused)
                } else {
                    Modifier.background(if (enabled) background else background.copy(alpha = 0.06f))
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = if (label.isEmpty()) 12.dp else 20.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = label.ifEmpty { null }, tint = foreground, modifier = Modifier.size(22.dp))
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = foreground,
                    maxLines = 1,
                )
            }
        }
        if (progress != null && progress > 0f) {
            TvProgressBar(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 5.dp)
                    .height(3.dp),
            )
        }
    }
}

@Composable
fun TvMessage(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(TvDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.nuvio.colors.textPrimary,
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.nuvio.colors.textSecondary,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(8.dp))
            action()
        }
    }
}
