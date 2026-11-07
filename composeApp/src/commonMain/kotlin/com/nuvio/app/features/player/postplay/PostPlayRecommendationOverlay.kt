package com.nuvio.app.features.player.postplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.tv.tvFocusOnAppear
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_post_play_because
import nuvio.composeapp.generated.resources.player_post_play_counter
import nuvio.composeapp.generated.resources.player_post_play_details
import nuvio.composeapp.generated.resources.player_post_play_next
import nuvio.composeapp.generated.resources.player_post_play_play
import nuvio.composeapp.generated.resources.player_post_play_previous
import nuvio.composeapp.generated.resources.player_post_play_recommended
import nuvio.composeapp.generated.resources.player_post_play_return
import nuvio.composeapp.generated.resources.player_post_play_stop_trailer
import nuvio.composeapp.generated.resources.player_post_play_trailer
import nuvio.composeapp.generated.resources.player_post_play_trailer_countdown
import org.jetbrains.compose.resources.stringResource

private val PanelBackground = Color(0xFF0B0B0F)
private val AccentColor = Color.White
private val SecondaryText = Color.White.copy(alpha = 0.72f)

/**
 * Full screen post-play panel: backdrop (or the trailer), title, metadata, description, actions and
 * browsing between the recommendations. Back returns to the player.
 */
@Composable
internal fun PostPlayRecommendationOverlay(
    state: PostPlayRecommendationUiState,
    trailerPlaybackSupported: Boolean,
    horizontalSafePadding: Dp,
    onPlay: (PostPlayRecommendation) -> Unit,
    onOpenDetails: (PostPlayRecommendation) -> Unit,
    onPlayTrailer: () -> Unit,
    onStopTrailer: () -> Unit,
    onTrailerEnded: () -> Unit,
    onTrailerError: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReturnToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recommendation = state.recommendation
    AnimatedVisibility(
        visible = state.isVisible && recommendation != null,
        enter = fadeIn(tween(320)),
        exit = fadeOut(tween(260)),
        modifier = modifier,
    ) {
        if (recommendation == null) return@AnimatedVisibility
        PlatformBackHandler(enabled = state.isVisible) {
            if (state.isTrailerPlaying) onStopTrailer() else onReturnToPlayer()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PanelBackground)
                // The panel takes every tap: nothing reaches the player below.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            AnimatedContent(
                targetState = recommendation.id,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "postPlayBackdrop",
            ) { _ ->
                AsyncImage(
                    model = recommendation.background ?: recommendation.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val trailer = recommendation.trailer
            if (state.isTrailerPlaying && trailer != null) {
                PostPlayTrailer(
                    key = recommendation.id,
                    videoUrl = trailer.videoUrl,
                    audioUrl = trailer.audioUrl,
                    onEnded = onTrailerEnded,
                    onError = onTrailerError,
                )
            }
            val scrimAlpha by animateFloatAsState(if (state.isTrailerPlaying) 0.55f else 1f, tween(400))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.92f),
                            0.55f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalSafePadding + 24.dp, vertical = 20.dp),
            ) {
                val compact = maxHeight < 420.dp
                if (state.count > 1) {
                    RecommendationPager(
                        state = state,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .widthIn(max = 620.dp)
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                ) {
                    Text(
                        text = if (state.sourceTitle.isBlank()) {
                            stringResource(Res.string.player_post_play_recommended)
                        } else {
                            stringResource(Res.string.player_post_play_because, state.sourceTitle)
                        },
                        color = SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RecommendationTitle(recommendation = recommendation, compact = compact)
                    recommendation.metadataLine().takeIf { it.isNotBlank() }?.let { line ->
                        Text(text = line, color = SecondaryText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!state.isTrailerPlaying) {
                        recommendation.description?.takeIf { it.isNotBlank() }?.let { description ->
                            Text(
                                text = description,
                                color = Color.White.copy(alpha = 0.86f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = if (compact) 2 else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        PostPlayButton(
                            label = stringResource(Res.string.player_post_play_play),
                            icon = Icons.Filled.PlayArrow,
                            primary = true,
                            modifier = Modifier.tvFocusOnAppear(),
                            onClick = { onPlay(recommendation) },
                        )
                        if (trailerPlaybackSupported && (trailer != null || state.isLoadingTrailer)) {
                            TrailerButton(
                                state = state,
                                available = trailer != null,
                                onPlayTrailer = onPlayTrailer,
                                onStopTrailer = onStopTrailer,
                            )
                        }
                        PostPlayButton(
                            label = stringResource(Res.string.player_post_play_details),
                            icon = Icons.Rounded.Info,
                            onClick = { onOpenDetails(recommendation) },
                        )
                        PostPlayButton(
                            label = stringResource(Res.string.player_post_play_return),
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            onClick = onReturnToPlayer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostPlayTrailer(
    key: String,
    videoUrl: String,
    audioUrl: String?,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    var ready by remember(key) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(500))
    HeroTrailerPlayerSurface(
        sourceUrl = videoUrl,
        sourceAudioUrl = audioUrl,
        playWhenReady = true,
        muted = false,
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        onReady = { ready = true },
        onEnded = onEnded,
        onError = onError,
    )
}

@Composable
private fun RecommendationTitle(recommendation: PostPlayRecommendation, compact: Boolean) {
    var logoFailed by remember(recommendation.logo) { mutableStateOf(false) }
    val logo = recommendation.logo?.takeIf { it.isNotBlank() && !logoFailed }
    if (logo != null) {
        AsyncImage(
            model = logo,
            contentDescription = recommendation.title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier
                .widthIn(max = if (compact) 240.dp else 340.dp)
                .heightIn(max = if (compact) 64.dp else 100.dp),
        )
    } else {
        Text(
            text = recommendation.title,
            color = Color.White,
            fontSize = if (compact) 26.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (compact) 30.sp else 40.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrailerButton(
    state: PostPlayRecommendationUiState,
    available: Boolean,
    onPlayTrailer: () -> Unit,
    onStopTrailer: () -> Unit,
) {
    val countdown = state.countdownSeconds
    when {
        state.isTrailerPlaying -> PostPlayButton(
            label = stringResource(Res.string.player_post_play_stop_trailer),
            icon = Icons.Rounded.Stop,
            onClick = onStopTrailer,
        )
        !available -> PostPlayButton(
            label = stringResource(Res.string.player_post_play_trailer),
            icon = null,
            loading = true,
            enabled = false,
            onClick = {},
        )
        else -> PostPlayButton(
            label = if (countdown != null) {
                stringResource(Res.string.player_post_play_trailer_countdown, countdown)
            } else {
                stringResource(Res.string.player_post_play_trailer)
            },
            icon = Icons.Rounded.Movie,
            onClick = onPlayTrailer,
        )
    }
}

@Composable
private fun RecommendationPager(
    state: PostPlayRecommendationUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PagerArrow(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            label = stringResource(Res.string.player_post_play_previous),
            enabled = state.canShowPrevious,
            onClick = onPrevious,
        )
        Text(
            text = stringResource(Res.string.player_post_play_counter, state.index + 1, state.count),
            color = SecondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        PagerArrow(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            label = stringResource(Res.string.player_post_play_next),
            enabled = state.canShowNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun PagerArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (focused) 0.32f else 0.14f))
            .border(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.2f), CircleShape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun PostPlayButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(24.dp)
    val background = when {
        primary -> AccentColor
        focused -> Color.White.copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val content = if (primary) Color.Black else Color.White
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused && primary -> Color.White.copy(alpha = 0.6f)
                    focused -> Color.White
                    primary -> Color.Transparent
                    else -> Color.White.copy(alpha = 0.18f)
                },
                shape = shape,
            )
            .graphicsLayer { if (focused && primary) { scaleX = 1.05f; scaleY = 1.05f } }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = content.copy(alpha = 0.7f),
                strokeWidth = 2.dp,
            )
            icon != null -> Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            color = content.copy(alpha = if (enabled) 1f else 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun PostPlayRecommendation.metadataLine(): String = buildList {
    releaseInfo?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay)?.takeIf { it.isNotBlank() }?.let(::add)
    runtime?.takeIf { it.isNotBlank() }?.let(::add)
    ageRating?.takeIf { it.isNotBlank() }?.let(::add)
    genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", ")?.let(::add)
    imdbRating?.trim()?.takeIf { it.isNotBlank() }?.let { add("IMDb $it") }
}.joinToString(" • ")
