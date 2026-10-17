package com.nuvio.app.features.cast

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import co.touchlab.kermit.Logger
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Cast with the Default Media Receiver. Everything here runs on the main thread
 * (MediaRouter and the Cast framework require it). Without Google Play services, and in the
 * TV app, the state stays [CastSessionState.Unavailable] and no cast UI is shown.
 */
internal actual object CastPlatform {
    private val log = Logger.withTag("CastSession")

    private val _state = MutableStateFlow<CastSessionState>(CastSessionState.Unavailable)
    actual val state: StateFlow<CastSessionState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    actual val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private var castContext: CastContext? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null
    private var initializing = false
    private var discoveryClients = 0

    fun initialize(context: Context) {
        if (castContext != null || initializing || TvAppRuntime.isTvApp) return
        val appContext = context.applicationContext
        val availability = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        }.getOrDefault(ConnectionResult.SERVICE_MISSING)
        if (availability != ConnectionResult.SUCCESS) {
            log.i { "Google Play services unavailable (code $availability): cast disabled" }
            return
        }
        initializing = true
        runCatching {
            CastContext.getSharedInstance(appContext, ContextCompat.getMainExecutor(appContext))
                .addOnSuccessListener { ready ->
                    initializing = false
                    onCastContextReady(appContext, ready)
                }
                .addOnFailureListener { error ->
                    initializing = false
                    log.w(error) { "Cast framework unavailable: cast disabled" }
                }
        }.onFailure { error ->
            initializing = false
            log.w(error) { "Cast framework initialization failed: cast disabled" }
        }
    }

    private fun onCastContextReady(appContext: Context, context: CastContext) {
        castContext = context
        mediaRouter = MediaRouter.getInstance(appContext)
        routeSelector = context.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID),
            )
            .build()
        context.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        publishSession(context.sessionManager.currentCastSession)
        if (discoveryClients > 0) registerDiscovery()
        log.i { "Cast ready (state: ${_state.value::class.simpleName})" }
    }

    /** The connected session, if any; used by the cast player surface. */
    internal fun currentSession(): CastSession? =
        castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }

    actual fun startDiscovery() {
        discoveryClients += 1
        if (discoveryClients == 1) registerDiscovery()
    }

    actual fun stopDiscovery() {
        discoveryClients = (discoveryClients - 1).coerceAtLeast(0)
        if (discoveryClients > 0) return
        mediaRouter?.removeCallback(routerCallback)
        _devices.value = emptyList()
    }

    actual fun selectDevice(deviceId: String) {
        val router = mediaRouter ?: return
        val route = router.routes.firstOrNull { it.id == deviceId }
        if (route == null) {
            log.w { "Selected device is no longer available" }
            refreshDevices()
            return
        }
        log.i { "Connecting to ${route.name}" }
        router.selectRoute(route)
    }

    actual fun disconnect() {
        val context = castContext ?: return
        log.i { "Ending cast session" }
        context.sessionManager.endCurrentSession(true)
    }

    actual fun canServeLocalMedia(): Boolean = LanMediaProxy.lanAddress() != null

    private fun registerDiscovery() {
        val router = mediaRouter ?: return
        val selector = routeSelector ?: return
        router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refreshDevices()
    }

    private fun refreshDevices() {
        val router = mediaRouter ?: return
        val selector = routeSelector ?: return
        _devices.value = router.routes
            .filter { route ->
                !route.isDefault && !route.isBluetooth && route.isEnabled && route.matchesSelector(selector)
            }
            .map { route ->
                CastDevice(
                    id = route.id,
                    name = route.name,
                    description = route.description,
                    isConnecting = route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                )
            }
    }

    private fun publishSession(session: CastSession?) {
        _state.value = when {
            session == null -> CastSessionState.Idle
            session.isConnected -> session.connectedState()
            session.isConnecting -> CastSessionState.Connecting(session.castDevice?.friendlyName)
            else -> CastSessionState.Idle
        }
    }

    private fun CastSession.connectedState() = CastSessionState.Connected(
        deviceName = castDevice?.friendlyName,
        sessionId = sessionId ?: "cast",
    )

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _state.value = CastSessionState.Connecting(session.castDevice?.friendlyName)
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            log.i { "Session started on ${session.castDevice?.friendlyName}" }
            _state.value = session.connectedState()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            log.w { "Session start failed: ${CastStatusCodes.getStatusCodeString(error)}" }
            _state.value = CastSessionState.Idle
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            log.i { "Session ended (${CastStatusCodes.getStatusCodeString(error)})" }
            _state.value = CastSessionState.Idle
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _state.value = CastSessionState.Connecting(session.castDevice?.friendlyName)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            log.i { "Session resumed on ${session.castDevice?.friendlyName}" }
            _state.value = session.connectedState()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            log.w { "Session resume failed: ${CastStatusCodes.getStatusCodeString(error)}" }
            _state.value = CastSessionState.Idle
        }

        // A suspended session usually comes back on its own: keep the current state so a short
        // network drop does not move playback back to the phone.
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            log.w { "Session suspended (reason $reason)" }
        }
    }
}
