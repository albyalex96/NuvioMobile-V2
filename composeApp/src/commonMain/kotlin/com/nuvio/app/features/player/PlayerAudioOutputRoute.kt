package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

/**
 * Current media audio output, updated when headphones/Bluetooth/HDMI devices come and go.
 * Null when the platform cannot tell (the delay then simply is not saved per device).
 */
@Composable
internal expect fun rememberAudioOutputRoute(): AudioOutputRoute?
