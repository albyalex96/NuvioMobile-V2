package com.nuvio.app.features.party

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
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

private val log = Logger.withTag("PartyLinks")

@Composable
internal actual fun rememberPartyLinkSettingsAction(): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || TvAppRuntime.isTvApp) return null
    val context = LocalContext.current
    var linksEnabled by remember { mutableStateOf(context.partyLinksOpenHere()) }
    // Checked again when the user comes back from the system settings.
    LifecycleResumeEffect(Unit) {
        linksEnabled = context.partyLinksOpenHere()
        onPauseOrDispose { }
    }
    if (linksEnabled) return null
    return { context.openLinkSettings() }
}

private fun Context.partyLinksOpenHere(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return runCatching {
        val manager = getSystemService(DomainVerificationManager::class.java) ?: return false
        val state = manager.getDomainVerificationUserState(packageName)
            ?.hostToStateMap
            ?.get(PARTY_INVITE_LINK_HOST)
        state == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
            state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }.onFailure { log.w(it) { "Could not read the link approval state" } }
        .getOrDefault(false)
}

private fun Context.openLinkSettings() {
    val packageUri = Uri.parse("package:$packageName")
    val openByDefault = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    for (intent in listOf(openByDefault, appDetails)) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log.i { "Opened ${intent.action}" }
            return
        } catch (error: ActivityNotFoundException) {
            log.w { "No activity for ${intent.action}" }
        }
    }
}
