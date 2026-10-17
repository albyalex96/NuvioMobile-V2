package com.nuvio.app.features.cast

import androidx.compose.runtime.Composable
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot

/**
 * Drop-in replacement of `PlatformPlayerSurface` while a cast session is active: it loads
 * [request] on the receiver and exposes it through the same controller/snapshot callbacks,
 * so the player runtime (progress, scrobbling, next episode, party…) keeps working unchanged.
 * It draws nothing: the player shows [CastingBackdrop] behind the controls.
 */
@Composable
internal expect fun CastPlayerSurface(
    request: CastMediaRequest,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    onInitialPositionHandled: (key: String, handled: Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
)
