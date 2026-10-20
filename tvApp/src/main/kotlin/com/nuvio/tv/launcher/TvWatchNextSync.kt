package com.nuvio.tv.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.tv.settings.rememberTvLayoutSettings
import kotlinx.coroutines.delay

/** Waits for the row to settle: progress sync and "next up" resolution update it in bursts. */
private const val SYNC_DEBOUNCE_MS = 3_000L

/**
 * Keeps the launcher rows (app channel and "Play next") in line with the home "Continue watching" row.
 * [ready] is false while the home is still loading, so a temporarily empty row does not
 * wipe the launcher entries.
 */
@Composable
fun TvWatchNextSync(items: List<ContinueWatchingItem>, ready: Boolean) {
    val context = LocalContext.current
    val settings = rememberTvLayoutSettings()
    LaunchedEffect(ready, items, settings.launcherContinueWatching, settings.launcherOpenAction) {
        if (!settings.launcherContinueWatching) {
            TvLauncherPublisher.clear(context)
            return@LaunchedEffect
        }
        if (!ready) return@LaunchedEffect
        delay(SYNC_DEBOUNCE_MS)
        TvLauncherPublisher.sync(context, items, settings.launcherOpenAction)
    }
}
