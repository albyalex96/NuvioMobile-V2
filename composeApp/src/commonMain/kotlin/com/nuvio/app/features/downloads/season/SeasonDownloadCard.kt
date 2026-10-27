package com.nuvio.app.features.downloads.season

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioStatusModal
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** A season download in the downloads screen: progress, episodes left and its actions. */
@Composable
fun SeasonDownloadCard(
    batch: SeasonDownloadBatch,
    modifier: Modifier = Modifier,
) {
    var confirmCancel by remember(batch.id) { mutableStateOf(false) }
    val seasonLabel = if (batch.seasonNumber == 0) {
        stringResource(Res.string.episodes_specials)
    } else {
        stringResource(Res.string.episodes_season, batch.seasonNumber)
    }
    val total = batch.countedEpisodes.coerceAtLeast(1)
    val fallbackCount = batch.episodes.count { it.fallbackSource != null && it.state != SeasonEpisodeState.Skipped }
    val statusParts = buildList {
        add(stringResource(Res.string.season_download_progress, batch.completedCount, batch.countedEpisodes))
        if (batch.paused) add(stringResource(Res.string.season_download_status_paused))
        if (batch.activeCount > 0) add(stringResource(Res.string.season_download_status_active, batch.activeCount))
        if (batch.pendingCount > 0) add(stringResource(Res.string.season_download_status_queued, batch.pendingCount))
        if (batch.needsManualCount > 0) add(stringResource(Res.string.season_download_status_manual, batch.needsManualCount))
        if (batch.failedCount > 0) add(stringResource(Res.string.season_download_status_failed, batch.failedCount))
        if (fallbackCount > 0) add(stringResource(Res.string.season_download_status_fallback, fallbackCount))
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${batch.title} · $seasonLabel",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!batch.isFinished || batch.paused) {
                    if (batch.paused) {
                        IconButton(onClick = { SeasonDownloadRepository.resumeBatch(batch.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(Res.string.season_download_resume))
                        }
                    } else {
                        IconButton(onClick = { SeasonDownloadRepository.pauseBatch(batch.id) }) {
                            Icon(Icons.Rounded.Pause, contentDescription = stringResource(Res.string.season_download_pause))
                        }
                    }
                }
                if (batch.failedCount + batch.needsManualCount > 0) {
                    IconButton(onClick = { SeasonDownloadRepository.retryBatch(batch.id) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(Res.string.season_download_retry))
                    }
                }
                if (batch.isFinished) {
                    IconButton(onClick = { SeasonDownloadRepository.dismissBatch(batch.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.season_download_dismiss))
                    }
                } else {
                    IconButton(onClick = { confirmCancel = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(Res.string.season_download_cancel))
                    }
                }
            }
            LinearProgressIndicator(
                progress = { batch.completedCount.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
            )
            if (batch.needsManualCount > 0) {
                Text(
                    text = stringResource(Res.string.season_download_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
        }
    }

    if (confirmCancel) {
        NuvioStatusModal(
            title = stringResource(Res.string.season_download_cancel_title),
            message = stringResource(Res.string.season_download_cancel_message),
            isVisible = true,
            destructive = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                confirmCancel = false
                SeasonDownloadRepository.cancelBatch(batch.id)
            },
            onDismiss = { confirmCancel = false },
        )
    }
}
