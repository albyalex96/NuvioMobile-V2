package com.nuvio.app.features.updater

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.gradientMask
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.updates_changelog_changes
import nuvio.composeapp.generated.resources.updates_changelog_full
import nuvio.composeapp.generated.resources.updates_changelog_installed_label
import nuvio.composeapp.generated.resources.updates_changelog_open_change
import nuvio.composeapp.generated.resources.updates_changelog_open_release
import nuvio.composeapp.generated.resources.updates_changelog_section_features
import nuvio.composeapp.generated.resources.updates_changelog_section_fixes
import nuvio.composeapp.generated.resources.updates_changelog_section_improvements
import nuvio.composeapp.generated.resources.updates_changelog_section_other
import nuvio.composeapp.generated.resources.updates_changelog_section_performance
import nuvio.composeapp.generated.resources.updates_installed_changelog_failed
import nuvio.composeapp.generated.resources.updates_installed_changelog_not_found
import nuvio.composeapp.generated.resources.updates_no_release_notes
import nuvio.composeapp.generated.resources.updates_release_notes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Changelog of an available update, opened from the info button of the update banner. */
@Composable
internal fun ReleaseNotesDialog(
    source: ReleaseNotesSource,
    onDismiss: () -> Unit,
    label: String = stringResource(Res.string.updates_release_notes),
) {
    val parsed = remember(source) {
        ReleaseNotesParser.parse(source.notes, ReleaseNotesParser.repositoryUrl(source.releaseUrl))
    }
    val uriHandler = LocalUriHandler.current
    DialogSurface(onDismissRequest = onDismiss) {
        ReleaseNotesHero(
            label = label,
            tag = source.tag,
            title = source.title,
            changeCount = parsed.entries.size,
            onDismiss = onDismiss,
        )
        ReleaseNotesBody(parsed = parsed, onOpen = uriHandler::openUri)
        if (parsed.compareUrl != null || source.releaseUrl != null) {
            DialogButtons {
                parsed.compareUrl?.let { url ->
                    DialogButton(
                        text = stringResource(Res.string.updates_changelog_full),
                        onClick = { uriHandler.openUri(url) },
                        icon = Icons.Rounded.History,
                    )
                }
                source.releaseUrl?.let { url ->
                    DialogButton(
                        text = stringResource(Res.string.updates_changelog_open_release),
                        onClick = { uriHandler.openUri(url) },
                        style = DialogButtonStyle.Primary,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    )
                }
            }
        }
    }
}

private sealed interface InstalledNotesState {
    data object Loading : InstalledNotesState
    data class Loaded(val source: ReleaseNotesSource) : InstalledNotesState
    data class Failed(val notFound: Boolean) : InstalledNotesState
}

/** Changelog of the installed version, see [InstalledReleaseNotes]. */
@Composable
internal fun InstalledReleaseNotesDialog(onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<InstalledNotesState>(InstalledNotesState.Loading) }
    LaunchedEffect(attempt) {
        state = InstalledNotesState.Loading
        state = InstalledReleaseNotes.load().fold(
            onSuccess = { InstalledNotesState.Loaded(it) },
            onFailure = { InstalledNotesState.Failed(notFound = it is NoChannelReleaseException) },
        )
    }
    val label = stringResource(Res.string.updates_changelog_installed_label)
    when (val current = state) {
        is InstalledNotesState.Loaded -> ReleaseNotesDialog(
            source = current.source,
            onDismiss = onDismiss,
            label = label,
        )
        else -> DialogSurface(onDismissRequest = onDismiss) {
            val version = InstalledReleaseNotes.installedVersion
            ReleaseNotesHero(
                label = label,
                tag = "v${VersionUtils.normalize(version)}",
                title = null,
                changeCount = null,
                onDismiss = onDismiss,
            )
            if (current is InstalledNotesState.Failed) {
                Text(
                    text = if (current.notFound) {
                        stringResource(Res.string.updates_installed_changelog_not_found, version)
                    } else {
                        stringResource(Res.string.updates_installed_changelog_failed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
                DialogButtons {
                    DialogButton(
                        text = stringResource(Res.string.action_close),
                        onClick = onDismiss,
                    )
                    if (!current.notFound) {
                        DialogButton(
                            text = stringResource(Res.string.action_retry),
                            onClick = { attempt += 1 },
                            style = DialogButtonStyle.Primary,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = NuvioTokens.Space.s24),
                    contentAlignment = Alignment.Center,
                ) {
                    NuvioLoadingIndicator(size = NuvioTokens.Space.s32)
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesHero(
    label: String,
    tag: String,
    title: String?,
    changeCount: Int?,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    val accentBrush = palette.accentBrush()
    val glow = palette.accentGradient.lastOrNull() ?: tokens.colors.accent
    val heroShape = RoundedCornerShape(NuvioTokens.Radius.xl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(
                Brush.linearGradient(
                    listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0.08f), Color.Transparent),
                ),
            )
            .border(BorderStroke(tokens.borders.thin, glow.copy(alpha = 0.22f)), heroShape),
    ) {
        Icon(
            imageVector = Icons.Rounded.NewReleases,
            contentDescription = null,
            tint = glow.copy(alpha = 0.16f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = NuvioTokens.Space.s8)
                .size(NuvioTokens.Space.s96)
                .graphicsLayer {
                    rotationZ = -14f
                    translationY = 18.dp.toPx()
                },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NuvioTokens.Space.s18,
                    end = NuvioTokens.Space.s48,
                    top = NuvioTokens.Space.s16,
                    bottom = NuvioTokens.Space.s18,
                ),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = tag,
                modifier = Modifier.gradientMask(accentBrush),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                title?.takeIf { it.isNotBlank() && it != tag },
                changeCount?.takeIf { it > 0 }?.let {
                    pluralStringResource(Res.plurals.updates_changelog_changes, it, it)
                },
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(separator = "  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(NuvioTokens.Space.s4),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.action_close),
                tint = tokens.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun ColumnScope.ReleaseNotesBody(
    parsed: ParsedReleaseNotes,
    onOpen: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sections = parsed.sections
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s18),
    ) {
        if (sections.isEmpty() && parsed.text.isBlank()) {
            Text(
                text = stringResource(Res.string.updates_no_release_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
            )
        }
        var index = 0
        sections.forEach { (kind, entries) ->
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                ReleaseNotesSectionHeader(kind = kind, count = entries.size)
                entries.forEach { entry ->
                    ReleaseNoteRow(entry = entry, appearIndex = index++, onOpen = onOpen)
                }
            }
        }
        if (parsed.text.isNotBlank()) {
            Text(
                text = parsed.text,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ReleaseNotesSectionHeader(kind: ReleaseNoteKind, count: Int) {
    val tokens = MaterialTheme.nuvio
    val color = kind.color()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Box(
            modifier = Modifier
                .size(NuvioTokens.Space.s24)
                .clip(RoundedCornerShape(NuvioTokens.Radius.md))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = kind.icon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(NuvioTokens.Space.s16),
            )
        }
        Text(
            text = stringResource(kind.label()),
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            modifier = Modifier
                .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.hover))
                .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s1),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textSecondary,
        )
    }
}

@Composable
private fun ReleaseNoteRow(
    entry: ReleaseNoteEntry,
    appearIndex: Int,
    onOpen: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val color = entry.kind.color()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(appearIndex.coerceAtMost(12) * 28L)
        reveal.animateTo(1f, tween(durationMillis = NuvioTokens.Motion.normalMillis))
    }
    val openLabel = entry.reference?.let { stringResource(Res.string.updates_changelog_open_change, it) }
    val shape = RoundedCornerShape(NuvioTokens.Radius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 10.dp.toPx()
            }
            .clip(shape)
            .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.subtle))
            .then(
                if (entry.url != null) {
                    Modifier
                        .clickable { onOpen(entry.url) }
                        .semantics { openLabel?.let { contentDescription = it } }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(NuvioTokens.Space.s3)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = NuvioTokens.Space.s12,
                    end = NuvioTokens.Space.s8,
                    top = NuvioTokens.Space.s10,
                    bottom = NuvioTokens.Space.s10,
                ),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            if (entry.scope != null || entry.reference != null || entry.author != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    entry.scope?.let { scope ->
                        Text(
                            text = scope,
                            modifier = Modifier
                                .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                                .background(color.copy(alpha = 0.16f))
                                .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s1),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    entry.reference?.let { reference ->
                        Text(
                            text = reference.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                    entry.author?.let { author ->
                        Text(
                            text = "@$author",
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (entry.url != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = tokens.colors.textMuted,
                modifier = Modifier
                    .padding(end = NuvioTokens.Space.s12)
                    .size(NuvioTokens.Space.s16),
            )
        }
    }
}

@Composable
private fun ReleaseNoteKind.color(): Color {
    val colors = MaterialTheme.nuvio.colors
    return when (this) {
        ReleaseNoteKind.Feature -> colors.accent
        ReleaseNoteKind.Fix -> colors.warning
        ReleaseNoteKind.Performance -> colors.success
        ReleaseNoteKind.Improvement -> colors.info
        ReleaseNoteKind.Other -> colors.textMuted
    }
}

private fun ReleaseNoteKind.icon(): ImageVector = when (this) {
    ReleaseNoteKind.Feature -> Icons.Rounded.AutoAwesome
    ReleaseNoteKind.Fix -> Icons.Rounded.Build
    ReleaseNoteKind.Performance -> Icons.Rounded.Speed
    ReleaseNoteKind.Improvement -> Icons.Rounded.Tune
    ReleaseNoteKind.Other -> Icons.Rounded.MoreHoriz
}

private fun ReleaseNoteKind.label(): StringResource = when (this) {
    ReleaseNoteKind.Feature -> Res.string.updates_changelog_section_features
    ReleaseNoteKind.Fix -> Res.string.updates_changelog_section_fixes
    ReleaseNoteKind.Performance -> Res.string.updates_changelog_section_performance
    ReleaseNoteKind.Improvement -> Res.string.updates_changelog_section_improvements
    ReleaseNoteKind.Other -> Res.string.updates_changelog_section_other
}
