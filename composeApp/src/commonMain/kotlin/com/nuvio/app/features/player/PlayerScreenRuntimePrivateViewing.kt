package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nuvio.app.core.ui.NuvioToastController
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.private_viewing_item_notice
import org.jetbrains.compose.resources.getString

/** Tells the user once, when the player opens, that this movie/episode is watched privately. */
@Composable
internal fun PlayerScreenRuntime.BindPrivateViewingNotice() {
    LaunchedEffect(privateViewingKey) {
        if (privateViewingKey == null || playerSettingsUiState.privateViewingEnabled) return@LaunchedEffect
        NuvioToastController.show(getString(Res.string.private_viewing_item_notice))
    }
}
