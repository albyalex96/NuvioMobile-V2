package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.skip.NextEpisodeInfo
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_episode_title_format
import nuvio.composeapp.generated.resources.player_next_episode_thumbnail
import nuvio.composeapp.generated.resources.player_still_watching_continue
import nuvio.composeapp.generated.resources.player_still_watching_live_tv_message
import nuvio.composeapp.generated.resources.player_still_watching_message
import nuvio.composeapp.generated.resources.player_still_watching_next_label
import nuvio.composeapp.generated.resources.player_still_watching_stop
import nuvio.composeapp.generated.resources.player_still_watching_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Are you still watching?" card: countdown ring, the episode that would play next and the two
 * choices. On TV "Keep watching" takes the focus (primary [DialogButton]); left reaches "Exit".
 */
@Composable
internal fun StillWatchingDialog(
    countdownProgress: Float,
    remainingSeconds: Int,
    isLiveTv: Boolean,
    consecutiveEpisodes: Int,
    liveTvMinutes: Int,
    nextEpisode: NextEpisodeInfo?,
    blurNextEpisodeThumbnail: Boolean,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    DialogSurface(onDismissRequest = onExit) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StillWatchingCountdownRing(progress = countdownProgress, remainingSeconds = remainingSeconds)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
            ) {
                Text(
                    text = stringResource(Res.string.player_still_watching_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
                Text(
                    text = if (isLiveTv) {
                        stringResource(Res.string.player_still_watching_live_tv_message, liveTvMinutes)
                    } else {
                        pluralStringResource(
                            Res.plurals.player_still_watching_message,
                            consecutiveEpisodes,
                            consecutiveEpisodes,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
            }
        }
        if (!isLiveTv && nextEpisode != null) {
            StillWatchingNextEpisode(episode = nextEpisode, blurThumbnail = blurNextEpisodeThumbnail)
        }
        DialogButtons {
            StillWatchingExitButton(elapsed = 1f - countdownProgress, onClick = onExit)
            DialogButton(
                text = stringResource(Res.string.player_still_watching_continue),
                onClick = onContinue,
                style = DialogButtonStyle.Primary,
                icon = Icons.Rounded.PlayArrow,
            )
        }
    }
}

/** Red exit button that fills from left to right as the countdown runs out. */
@Composable
private fun StillWatchingExitButton(elapsed: Float, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val danger = tokens.colors.danger
    Button(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = NuvioTokens.Space.s48)
            .clip(tokens.shapes.button)
            .background(danger.copy(alpha = STILL_WATCHING_EXIT_TRACK_ALPHA))
            .drawBehind {
                drawRect(color = danger, size = size.copy(width = size.width * elapsed.coerceIn(0f, 1f)))
            },
        shape = tokens.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = tokens.colors.onAccent,
        ),
        contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(tokens.icons.sm),
        )
        Spacer(modifier = Modifier.width(NuvioTokens.Space.s8))
        Text(
            text = stringResource(Res.string.player_still_watching_stop),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val STILL_WATCHING_EXIT_TRACK_ALPHA = 0.35f

@Composable
private fun StillWatchingCountdownRing(progress: Float, remainingSeconds: Int) {
    val tokens = MaterialTheme.nuvio
    Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = tokens.colors.accent,
            trackColor = tokens.colors.textPrimary.copy(alpha = tokens.opacity.selected),
            strokeWidth = 4.dp,
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = remainingSeconds.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary,
        )
    }
}

@Composable
private fun StillWatchingNextEpisode(episode: NextEpisodeInfo, blurThumbnail: Boolean) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.subtle))
            .padding(NuvioTokens.Space.s10),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!episode.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = episode.thumbnail,
                contentDescription = stringResource(Res.string.player_next_episode_thumbnail),
                modifier = Modifier
                    .size(width = 104.dp, height = 58.dp)
                    .clip(tokens.shapes.compactCard)
                    // Same spoiler protection as the next-episode card.
                    .then(if (blurThumbnail) Modifier.blur(18.dp) else Modifier),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
        ) {
            Text(
                text = stringResource(Res.string.player_still_watching_next_label),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
            )
            Text(
                text = formatEpisodeCodeWithSelectedFormat(episode.season, episode.episode)
                    ?.let { code -> if (episode.title.isBlank()) code else "$code • ${episode.title}" }
                    ?: stringResource(
                        Res.string.compose_player_episode_title_format,
                        episode.season,
                        episode.episode,
                        episode.title,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
