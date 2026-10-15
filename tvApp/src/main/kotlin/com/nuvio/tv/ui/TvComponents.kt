package com.nuvio.tv.ui

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
import com.nuvio.app.core.ui.nuvio

/** Shared layout metrics of the TV screens (dp on a 960x540dp TV canvas). */
object TvDimens {
    val screenPadding = 48.dp
    val sidebarCollapsedWidth = 76.dp
    val rowSpacing = 14.dp
    val posterWidth = 124.dp
    val landscapeWidth = 248.dp
    val cardRadius = 10.dp
}

val TvCardShape = RoundedCornerShape(TvDimens.cardRadius)
val TvPillShape = RoundedCornerShape(50)

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
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
        color = MaterialTheme.nuvio.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = TvDimens.screenPadding, bottom = 10.dp),
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
            contentPadding = PaddingValues(horizontal = TvDimens.screenPadding, vertical = 10.dp),
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
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
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
        focused -> Color.Black
        primary -> colors.onAccent
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
            .background(if (enabled) background else background.copy(alpha = 0.06f)),
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
