package com.nuvio.app.features.cloudstream

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.features.settings.IntegrationLogo
import com.nuvio.app.features.settings.integrationLogoPainter
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cloudstream_badge_label
import org.jetbrains.compose.resources.stringResource

/** Purple "CloudStream" badge with the original CloudStream logo. */
@Composable
internal fun CloudStreamBadge(modifier: Modifier = Modifier) {
    NuvioInfoBadge(
        text = stringResource(Res.string.cloudstream_badge_label),
        modifier = modifier,
        iconPainter = integrationLogoPainter(IntegrationLogo.Cloudstream),
        containerColor = CloudStreamBadgeContainer,
        contentColor = CloudStreamBadgeContent,
    )
}

private val CloudStreamBadgeContainer = Color(0xFF2D0A4E)
private val CloudStreamBadgeContent = Color(0xFFD8B4FE)
