package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable

/**
 * Battery restrictions that can stop the background downloads, and the system pages to lift them.
 * Only on phones and tablets: TVs have no battery saving and iOS has no such settings.
 */
internal class DownloadsPowerState(
    /** The app is under battery optimization: Android may stop its background work. */
    val restricted: Boolean,
    /** Asks Android to exempt the app from battery optimization (system dialog or list). */
    val openBatteryOptimization: () -> Unit,
    /** App details page of the phone, where manufacturers add their own background settings. */
    val openAppSettings: () -> Unit,
)

internal expect val downloadsPowerSettingsSupported: Boolean

/** Current state, checked again every time the user comes back to the app; null when not supported. */
@Composable
internal expect fun rememberDownloadsPowerState(): DownloadsPowerState?
