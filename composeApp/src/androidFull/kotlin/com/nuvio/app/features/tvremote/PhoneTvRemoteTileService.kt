package com.nuvio.app.features.tvremote

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import co.touchlab.kermit.Logger

/**
 * Quick Settings tile (Android full): opens the TV remote controls in the notification even when
 * the app is closed. Android forbids an app in background from starting them by itself, but a
 * tap on the tile is a user action, so the foreground service can start. A second tap closes them.
 */
class PhoneTvRemoteTileService : TileService() {
    private val log = Logger.withTag("PhoneTvRemoteTile")

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val client = clientOrNull()
        val state = client?.state?.value
        when {
            PhoneTvRemoteService.isRunning -> {
                log.i { "Tile: closing the TV remote controls" }
                PhoneTvRemoteService.stopFromTile(this)
            }
            client == null || state?.pairedTv == null || !state.backgroundControls -> {
                // Nothing to control from the notification: the app shows the pairing / settings.
                log.i { "Tile: no paired TV or background controls off, opening the app" }
                openApp()
            }
            else -> {
                log.i { "Tile: opening the TV remote controls" }
                if (!PhoneTvRemoteService.startFromTile(this)) openApp()
            }
        }
        refreshTile()
    }

    private fun clientOrNull(): PhoneTvRemoteClient? {
        // The app can be closed: the client is normally created by MainActivity.
        TvRemoteBootstrap.initialize(applicationContext)
        return PhoneTvRemote.activeClient
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val state = clientOrNull()?.state?.value
        val tv = state?.pairedTv
        tile.state = when {
            PhoneTvRemoteService.isRunning -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = tv?.name
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure { error -> log.w(error) { "Tile: unable to open the app" } }
    }

    companion object {
        /** Asks the system to refresh the tile (the controls were opened or closed elsewhere). */
        internal fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, PhoneTvRemoteTileService::class.java))
            }
        }
    }
}
