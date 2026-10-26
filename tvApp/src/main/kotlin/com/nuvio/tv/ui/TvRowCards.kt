package com.nuvio.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioCardDepth

/** Focus ring width of NuvioTV cards (`spacing.xxs`): the card keeps its size on focus. */
val TvRowCardFocusBorder = 2.dp

private const val EXPAND_ANIMATION_MS = 260

/** Bottom-left clearlogo of the landscape cards (NuvioTV: 62% x 34% of the card). */
private val LandscapeLogoGradient = Brush.verticalGradient(
    0.45f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.72f),
)

/**
 * Focus surface of the home row cards: like NuvioTV the card does not grow, it only gets the
 * focus ring, and the profile's card depth effect (edge light / sheen) is drawn on top.
 */
@Composable
fun Modifier.tvRowCardSurface(
    shape: Shape,
    depthSurface: NuvioCardDepthSurface,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocusChange: (Boolean) -> Unit,
): Modifier = this
    .tvFocusable(
        shape = shape,
        onClick = onClick,
        onLongClick = onLongClick,
        focusedScale = 1f,
        borderWidth = TvRowCardFocusBorder,
        onFocusChange = onFocusChange,
    )
    .nuvioCardDepth(shape = shape, surface = depthSurface)

/** Title under a row card (NuvioTV `showLabels`: titleMedium, 8dp below the artwork). */
@Composable
fun TvRowCardLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        color = MaterialTheme.nuvio.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
    )
}

/**
 * Portrait (or square) card of a home catalog row. With [expanded] it widens to 16:9 and shows
 * the [expandedArtwork] backdrop with the clearlogo (NuvioTV "expand focused poster").
 */
@Composable
fun TvRowPosterCard(
    imageUrl: String?,
    title: String,
    width: Dp,
    height: Dp,
    shape: Shape,
    showLabel: Boolean,
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedWidth: Dp = width,
    expanded: Boolean = false,
    expandedArtwork: TvLandscapeArtwork? = null,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    if (expandedArtwork == null) {
        // Card that never expands (expansion off, square art, no backdrop): no width animation
        // and no crossfade to keep per card.
        Column(modifier = modifier.width(width)) {
            TvRowPosterSurface(height, shape, watched, onClick, onLongClick, onFocused) {
                PosterArtwork(imageUrl = imageUrl, title = title)
            }
            if (showLabel) TvRowCardLabel(title)
        }
        return
    }
    val showExpanded = expanded
    val animatedWidth by animateDpAsState(
        targetValue = if (showExpanded) expandedWidth else width,
        animationSpec = tween(EXPAND_ANIMATION_MS),
        label = "tvRowCardWidth",
    )
    Column(modifier = modifier.width(animatedWidth)) {
        TvRowPosterSurface(height, shape, watched, onClick, onLongClick, onFocused) {
            Crossfade(targetState = showExpanded, animationSpec = tween(EXPAND_ANIMATION_MS), label = "tvRowCardArtwork") { wide ->
                if (wide) {
                    LandscapeArtworkContent(artwork = expandedArtwork, title = title, cardHeight = height)
                } else {
                    PosterArtwork(imageUrl = imageUrl, title = title)
                }
            }
        }
        if (showLabel && !showExpanded) TvRowCardLabel(title)
    }
}

@Composable
private fun TvRowPosterSurface(
    height: Dp,
    shape: Shape,
    watched: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocused: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .tvRowCardSurface(
                shape = shape,
                depthSurface = NuvioCardDepthSurface.Posters,
                onClick = onClick,
                onLongClick = onLongClick,
                onFocusChange = { focused -> if (focused) onFocused() },
            )
            .background(MaterialTheme.nuvio.colors.surfaceCard),
    ) {
        content()
        if (watched) TvRowWatchedMarker(modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun BoxScope.PosterArtwork(imageUrl: String?, title: String) {
    TvRemoteImage(url = imageUrl, modifier = Modifier.fillMaxSize())
    if (imageUrl.isNullOrBlank()) RowCardFallbackTitle(title)
}

/** 16:9 card of a home catalog row ("landscape posters" or a landscape catalog). */
@Composable
fun TvRowLandscapeCard(
    artwork: TvLandscapeArtwork,
    title: String,
    width: Dp,
    height: Dp,
    shape: Shape,
    showLabel: Boolean,
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .tvRowCardSurface(
                    shape = shape,
                    depthSurface = NuvioCardDepthSurface.Posters,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onFocusChange = { focused -> if (focused) onFocused() },
                )
                .background(MaterialTheme.nuvio.colors.surfaceCard),
        ) {
            LandscapeArtworkContent(artwork = artwork, title = title, cardHeight = height)
            if (watched) TvRowWatchedMarker(modifier = Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) TvRowCardLabel(title)
    }
}

@Composable
private fun BoxScope.LandscapeArtworkContent(artwork: TvLandscapeArtwork, title: String, cardHeight: Dp) {
    val imageUrl = artwork.imageUrl
    if (artwork.fitImage && !imageUrl.isNullOrBlank()) {
        // Artwork not made for 16:9: blurred fill behind the whole, uncropped image.
        TvRemoteImage(url = imageUrl, modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.45f))
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        TvRemoteImage(url = imageUrl, modifier = Modifier.fillMaxSize())
    }
    when {
        !artwork.logoUrl.isNullOrBlank() -> {
            Box(modifier = Modifier.fillMaxSize().background(LandscapeLogoGradient))
            AsyncImage(
                model = artwork.logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.62f)
                    .height(cardHeight * 0.34f)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }
        // Without a logo NuvioTV writes the title on the artwork (not on a custom landscape poster).
        !artwork.fitImage && !imageUrl.isNullOrBlank() && !artwork.customLandscape -> {
            Box(modifier = Modifier.fillMaxSize().background(LandscapeLogoGradient))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.62f)
                    .padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            )
        }
        imageUrl.isNullOrBlank() -> RowCardFallbackTitle(title)
    }
}

@Composable
private fun BoxScope.RowCardFallbackTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.nuvio.colors.textSecondary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).padding(10.dp),
    )
}

/** Watched marker of NuvioTV cards: small accent circle with a check, top-end. */
@Composable
fun TvRowWatchedMarker(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 8.dp, end = 8.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.nuvio.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.nuvio.colors.onAccent,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Thin progress bar of the "continue watching" cards (NuvioTV: 3dp, secondary color). */
@Composable
fun TvRowProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(1.5.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(shape)
                .background(MaterialTheme.nuvio.colors.accent),
        )
    }
}
