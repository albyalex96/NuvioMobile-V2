package com.nuvio.app.features.player

import androidx.compose.material.icons.rounded.Schedule
import nuvio.composeapp.generated.resources.live_tv_player_guide
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.nuvio.app.core.ui.glass.ambientLiquidGlass
import com.nuvio.app.core.ui.glass.isAmbientLiquidGlassActive
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerToolbar(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPartyClick: (() -> Unit)? = null,
    partyActive: Boolean = false,
    onCastClick: (() -> Unit)? = null,
    castActive: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val capsule = playerToolbarGlassActive()
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        leadingContent?.invoke()
        val enterPictureInPicture = rememberEnterPictureInPicture()
        Row(
            modifier = if (capsule) Modifier.playerToolbarGlass(PlayerGlassCapsule).padding(horizontal = 4.dp) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // Order and visibility come from the "Player buttons" setting; unavailable ones are skipped.
        val toolbarLayout = PlayerSettingsRepository.uiState.collectAsStateWithLifecycle().value.toolbarLayout
        toolbarLayout.topBarButtons.forEach { button ->
            when (button) {
                PlayerToolbarButton.PICTURE_IN_PICTURE -> if (enterPictureInPicture != null) {
                    PlayerAction(
                        description = stringResource(Res.string.compose_player_picture_in_picture),
                        icon = Icons.Rounded.PictureInPictureAlt,
                        onClick = enterPictureInPicture,
                    )
                }
                PlayerToolbarButton.CAST -> if (onCastClick != null) {
                    PlayerAction(
                        description = stringResource(Res.string.cast_action),
                        icon = if (castActive) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                        onClick = onCastClick,
                        tint = if (castActive) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                PlayerToolbarButton.WATCH_PARTY -> if (onPartyClick != null) {
                    PlayerAction(
                        description = stringResource(Res.string.party_title),
                        icon = Icons.Rounded.Groups,
                        onClick = onPartyClick,
                        tint = if (partyActive) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                else -> Unit
            }
        }
        PlayerAction(
            description = stringResource(
                if (isLocked) Res.string.compose_player_unlock_controls else Res.string.compose_player_lock_controls,
            ),
            icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
            onClick = onLockToggle,
        )
        }
        if (capsule) Spacer(Modifier.width(8.dp))
        NuvioBackButton(
            tvFocusable = true,
            onClick = onBack,
            modifier = if (capsule) Modifier.playerToolbarGlass(CircleShape) else Modifier,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            buttonSize = 48.dp,
            iconSize = 24.dp,
            contentDescription = stringResource(Res.string.compose_player_close),
        )
    }
}

@Composable
internal fun PlayerControlActions(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    showRemainingTime: Boolean,
    onRuntimeClick: () -> Unit,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSourcesClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)? = null,
    onLiveChannelsClick: (() -> Unit)? = null,
    onLiveGuideClick: (() -> Unit)? = null,
    onNextEpisodeClick: (() -> Unit)?,
    onSwitchEngineClick: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    onResizeModeClick: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onOpenInExternalPlayer: (() -> Unit)?,
    onStreamInfoClick: () -> Unit,
    onSubmitIntroClick: (() -> Unit)?,
    onInteraction: () -> Unit,
) {
    val availableActions = buildMap<PlayerToolbarButton, PlayerControlAction> {
        onNextEpisodeClick?.let {
            put(PlayerToolbarButton.NEXT_EPISODE, PlayerControlAction(
                stringResource(Res.string.player_next_episode), it,
                icon = Icons.Rounded.SkipNext, iconSize = 40.dp,
            ))
        }
        put(PlayerToolbarButton.SUBTITLES, PlayerControlAction(
            stringResource(Res.string.compose_player_subtitles), onSubtitleClick,
            painter = appIconPainter(AppIconResource.PlayerSubtitles),
        ))
        put(PlayerToolbarButton.AUDIO, PlayerControlAction(
            stringResource(Res.string.compose_player_audio), onAudioClick,
            painter = appIconPainter(AppIconResource.PlayerAudioFilled),
        ))
        onSourcesClick?.let {
            put(PlayerToolbarButton.SOURCES, PlayerControlAction(
                stringResource(Res.string.compose_player_sources), it,
                painter = appIconPainter(AppIconResource.PlayerSource),
            ))
        }
        onEpisodesClick?.let {
            put(PlayerToolbarButton.EPISODES, PlayerControlAction(
                stringResource(Res.string.compose_player_episodes), it,
                painter = appIconPainter(AppIconResource.PlayerEpisodes),
            ))
        }
        onChaptersClick?.let {
            put(PlayerToolbarButton.CHAPTERS, PlayerControlAction(
                stringResource(Res.string.player_chapters), it,
                icon = Icons.AutoMirrored.Rounded.ListAlt,
            ))
        }
        onLiveChannelsClick?.let {
            put(PlayerToolbarButton.LIVE_CHANNELS, PlayerControlAction(
                stringResource(Res.string.compose_player_channels), it,
                icon = Icons.Rounded.Tv,
            ))
        }
        onLiveGuideClick?.let {
            put(PlayerToolbarButton.LIVE_GUIDE, PlayerControlAction(
                stringResource(Res.string.live_tv_player_guide), it,
                icon = Icons.Rounded.Schedule,
            ))
        }
        onSwitchEngineClick?.let {
            put(PlayerToolbarButton.SWITCH_ENGINE, PlayerControlAction(
                stringResource(Res.string.cd_switch_player_engine), it,
                icon = Icons.Rounded.SwapHoriz,
            ))
        }
        put(PlayerToolbarButton.SPEED, PlayerControlAction(
            "${stringResource(Res.string.compose_player_speed)} ${formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed)}",
            onSpeedClick, icon = Icons.Rounded.Speed,
        ))
        put(PlayerToolbarButton.RESIZE, PlayerControlAction(
            stringResource(resizeMode.labelRes), onResizeModeClick,
            painter = appIconPainter(AppIconResource.PlayerAspectRatio),
        ))
        onOpenInExternalPlayer?.let {
            put(PlayerToolbarButton.EXTERNAL_PLAYER, PlayerControlAction(
                stringResource(Res.string.streams_open_external_player), it,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            ))
        }
        put(PlayerToolbarButton.STREAM_INFO, PlayerControlAction(
            stringResource(Res.string.cd_stream_info), onStreamInfoClick,
            icon = Icons.Rounded.Info,
        ))
        onVideoSettingsClick?.let {
            put(PlayerToolbarButton.VIDEO_SETTINGS, PlayerControlAction(
                stringResource(Res.string.player_action_video_settings), it,
                icon = Icons.Rounded.Build,
            ))
        }
        onSubmitIntroClick?.let {
            put(PlayerToolbarButton.SUBMIT_INTRO, PlayerControlAction(
                stringResource(Res.string.submit_intro_action), it,
                icon = Icons.Rounded.Flag,
            ))
        }
    }
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    val toolbarLayout = playerSettings.toolbarLayout
    // Shown buttons first, then the ones behind the arrow; buttons that don't apply to this video are skipped.
    val primaryActions = toolbarLayout.visibleButtons.mapNotNull(availableActions::get)
    val secondaryActions = toolbarLayout.hiddenButtons.mapNotNull(availableActions::get)
    val actions = primaryActions + secondaryActions
    val hasOverflow = secondaryActions.isNotEmpty()
    var expanded by remember(hasOverflow) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val capsule = playerToolbarGlassActive()
    // Without the capsule the first icon is pulled to the timeline edge; inside it, it keeps its padding.
    val startOffset = when {
        capsule -> 0.dp
        primaryActions.firstOrNull() != null && primaryActions.first() === availableActions[PlayerToolbarButton.NEXT_EPISODE] -> (-13).dp
        else -> (-12).dp
    }
    LaunchedEffect(expanded, scrollState.maxValue) {
        if (expanded) scrollState.animateScrollTo(scrollState.maxValue) else scrollState.scrollTo(0)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerTimelineContentInset),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = !capsule)
                    .offset(x = startOffset)
                    .then(if (capsule) Modifier.playerToolbarGlass(PlayerGlassCapsule).padding(horizontal = 4.dp) else Modifier)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (if (expanded) actions else primaryActions).forEach { action ->
                    PlayerAction(
                        description = action.description,
                        onClick = {
                            onInteraction()
                            action.onClick()
                        },
                        icon = action.icon,
                        painter = action.painter,
                        iconSize = action.iconSize,
                    )
                }
                if (hasOverflow) {
                    PlayerAction(
                        description = stringResource(
                            if (expanded) Res.string.compose_player_fewer_actions else Res.string.compose_player_more_actions,
                        ),
                        onClick = {
                            expanded = !expanded
                            onInteraction()
                        },
                        icon = if (expanded) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    )
                }
            }
            Box(
                modifier = Modifier.height(48.dp).widthIn(min = 48.dp).clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(
                        if (showRemainingTime) Res.string.compose_player_show_elapsed_time else Res.string.compose_player_show_remaining_time,
                    ),
                    onClick = {
                        onRuntimeClick()
                        onInteraction()
                    },
                ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = formatPlaybackRuntime(displayedPositionMs, playbackSnapshot.durationMs, showRemainingTime),
                    style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = (metrics.timeSize.value + 2).sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlayerAction(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        if (painter != null) {
            Icon(painter, description, tint = tint, modifier = Modifier.size(iconSize))
        } else if (icon != null) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

private data class PlayerControlAction(
    val description: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
    val iconSize: Dp = 24.dp,
)

internal val PlayerGlassCapsule = RoundedCornerShape(percent = 50)

/** True when the toolbar actions get their glass capsule: liquid glass on and control backings on. */
@Composable
private fun playerToolbarGlassActive(): Boolean =
    LocalPlayerControlGlassEnabled.current && isAmbientLiquidGlassActive()

@Composable
private fun Modifier.playerToolbarGlass(shape: Shape): Modifier =
    clip(shape).ambientLiquidGlass(
        shape = shape,
        tint = Color.White.copy(alpha = 0.06f),
        fallback = Color.Transparent,
        blurRadius = 12.dp,
        refractionHeight = 8.dp,
        refractionAmount = 12.dp,
        backdropOpacity = 0.35f,
    ).border(0.75.dp, Color.White.copy(alpha = 0.22f), shape)
