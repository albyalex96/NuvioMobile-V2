package com.nuvio.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.livetv.CloseWhileRecordingDialog
import com.nuvio.app.features.livetv.DesktopLiveTvRecorder
import com.nuvio.app.features.livetv.LiveTvRecordingPlatform
import com.nuvio.app.features.livetv.LiveTvRecordingsRepository
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import com.nuvio.app.features.player.PlayerWindowFullscreen
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioSheetBackdrop
import com.nuvio.app.core.ui.NuvioSheetBackdropHost
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.logging.AppLogger
import com.nuvio.app.core.network.dns.DnsOverHttpsRepository
import com.nuvio.app.desktop.DesktopStorageBootstrap
import com.nuvio.app.desktop.compat.DesktopPreferencesRegistry
import com.nuvio.app.features.plugins.configureDesktopQuickJsLibrary
import org.jetbrains.compose.resources.painterResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark

private val log = Logger.withTag("DesktopApp")

fun main(args: Array<String>) {
    // Before any socket: on Windows the IPv6 stack makes JmDNS (Chromecast discovery) fail with
    // "setInterface6: Invalid argument". Also set in the packaged app's JVM options.
    System.setProperty("java.net.preferIPv4Stack", "true")
    // Same order as NuvioApplication/MainActivity on Android: storage first, then logger and DNS.
    DesktopStorageBootstrap.initialize()
    AppLogger.install(debuggable = System.getProperty("nuvio.debug") == "true")
    DnsOverHttpsRepository.ensureLoaded()
    runCatching { configureDesktopQuickJsLibrary() }
        .onFailure { log.e(it) { "QuickJS native library not available: plugins disabled" } }
    log.i { "Starting Nuvio desktop (${System.getProperty("os.name")}, Java ${System.getProperty("java.version")})" }

    args.firstOrNull { it.startsWith("nuvio://") || it.startsWith("stremio://") }?.let { url ->
        log.i { "Opening launch link" }
        handleAppUrl(url)
    }

    // Opened by a Windows scheduled task for a Live TV recording: start minimised.
    val launchedForRecording = LiveTvRecordingPlatform.LAUNCH_FLAG in args
    if (launchedForRecording) {
        // Another Nuvio is already open and records it: this one is not needed.
        if (!LiveTvRecordingPlatform.recorderLock()) {
            log.i { "Opened by a scheduled recording, but Nuvio is already running: exiting" }
            DesktopPreferencesRegistry.flush()
            return
        }
        log.i { "Opened by a scheduled recording" }
    }
    // Timers (and Windows tasks) for the scheduled recordings; a passed start records at once.
    runCatching { LiveTvRecordingsRepository.rearmAll() }
        .onFailure { log.e(it) { "Scheduled recordings not re-armed" } }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { DesktopLiveTvRecorder.stopAll() }
            runCatching { DesktopPreferencesRegistry.flush() }
        },
    )

    com.nuvio.app.desktop.RightClickAsLongPress.install()

    application {
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
            isMinimized = launchedForRecording,
        )
        var confirmCloseWhileRecording by remember { mutableStateOf(false) }
        val closeApp = {
            DesktopLiveTvRecorder.stopAll()
            DesktopPreferencesRegistry.flush()
            exitApplication()
        }
        Window(
            onCloseRequest = {
                // Closing Nuvio stops the recordings: asked first.
                if (DesktopLiveTvRecorder.active.value > 0) confirmCloseWhileRecording = true else closeApp()
            },
            state = windowState,
            title = "Nuvio",
            icon = painterResource(Res.drawable.app_logo_wordmark),
        ) {
            // Fullscreen for the player (F, double click): the window owns it.
            LaunchedEffect(windowState) {
                PlayerWindowFullscreen.handler = { fullscreen ->
                    windowState.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
                }
                snapshotFlow { windowState.placement }.collect { placement ->
                    PlayerWindowFullscreen.reportState(placement == WindowPlacement.Fullscreen)
                }
            }
            // Right click is a long press (RightClickAsLongPress): a short timeout opens menus quickly.
            val base = LocalViewConfiguration.current
            val viewConfiguration = remember(base) { DesktopViewConfiguration(base) }
            // Same as the phone: the sheets refract the app behind them when liquid glass is on.
            val sheetBackdropSource = rememberNuvioBackdrop()
            val sheetBackdrop = remember(sheetBackdropSource) { NuvioSheetBackdrop(sheetBackdropSource) }
            CompositionLocalProvider(LocalViewConfiguration provides viewConfiguration) {
                NuvioSheetBackdropHost(sheetBackdrop = sheetBackdrop) {
                    App()
                }
                if (confirmCloseWhileRecording) {
                    CloseWhileRecordingDialog(
                        onMinimize = {
                            confirmCloseWhileRecording = false
                            windowState.isMinimized = true
                        },
                        onClose = closeApp,
                        onDismiss = { confirmCloseWhileRecording = false },
                    )
                }
            }
        }
    }
}

/** Desktop timings: [longPressTimeoutMillis] matches [com.nuvio.app.desktop.RightClickAsLongPress]. */
private class DesktopViewConfiguration(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long = com.nuvio.app.desktop.RightClickAsLongPress.LONG_PRESS_TIMEOUT_MS
}
