package com.nuvio.app.features.downloads

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvAppRuntime

private val log = Logger.withTag("Downloads")

internal actual val downloadsPowerSettingsSupported: Boolean
    get() = !TvAppRuntime.isTvApp

@Composable
internal actual fun rememberDownloadsPowerState(): DownloadsPowerState? {
    if (!downloadsPowerSettingsSupported) return null
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(context.isBatteryRestricted()) }
    // Checked again when the user comes back from the system settings.
    LifecycleResumeEffect(Unit) {
        restricted = context.isBatteryRestricted()
        onPauseOrDispose { }
    }
    return remember(restricted) {
        DownloadsPowerState(
            restricted = restricted,
            openBatteryOptimization = { context.openBatteryOptimization() },
            openAppSettings = { context.openFirst(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.packageUri())) },
        )
    }
}

private fun Context.isBatteryRestricted(): Boolean = runCatching {
    val power = getSystemService(PowerManager::class.java) ?: return false
    !power.isIgnoringBatteryOptimizations(packageName)
}.onFailure { log.w(it) { "Could not read the battery optimization state" } }
    .getOrDefault(false)

// The app is distributed outside Google Play: asking directly for the exemption is allowed.
@SuppressLint("BatteryLife")
private fun Context.openBatteryOptimization() {
    log.i { "Opening the battery optimization settings (restricted=${isBatteryRestricted()})" }
    openFirst(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()),
    )
}

private fun Context.packageUri(): Uri = Uri.parse("package:$packageName")

/** Starts the first intent the phone can handle (pages differ between manufacturers). */
private fun Context.openFirst(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log.i { "Opened ${intent.action}" }
            return
        } catch (error: Exception) {
            log.w { "Could not open ${intent.action}: ${error::class.simpleName}" }
        }
    }
    log.e { "No system page available for the battery settings" }
}
