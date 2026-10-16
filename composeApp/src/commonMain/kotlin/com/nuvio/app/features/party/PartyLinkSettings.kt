package com.nuvio.app.features.party

import androidx.compose.runtime.Composable

/** Host of the watch party invite links (https://nuvio.tv/party/<code>). */
internal const val PARTY_INVITE_LINK_HOST = "nuvio.tv"

/**
 * Opens the system page where the user lets this app open the invite links. Invite links are
 * verified only for the official app, so this fork has to be approved by the user once.
 * Returns null when the links already open here, or the platform has no such page
 * (iOS, TV, Android older than 12, where links show the app chooser anyway).
 */
@Composable
internal expect fun rememberPartyLinkSettingsAction(): (() -> Unit)?
