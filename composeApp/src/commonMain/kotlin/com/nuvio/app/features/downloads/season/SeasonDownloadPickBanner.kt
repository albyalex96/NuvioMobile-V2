package com.nuvio.app.features.downloads.season

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Shown on the streams screen while the user picks the stream a season download is based on. */
@Composable
fun SeasonDownloadPickBanner(
    request: SeasonDownloadRequest,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasonLabel = if (request.seasonNumber == 0) {
        stringResource(Res.string.episodes_specials)
    } else {
        stringResource(Res.string.episodes_season, request.seasonNumber)
    }
    Surface(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.season_download_pick_title, seasonLabel, request.episodes.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.season_download_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.season_download_pick_cancel),
                )
            }
        }
    }
}
