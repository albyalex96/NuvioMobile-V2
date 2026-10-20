package com.nuvio.app.features.discord

import co.touchlab.kermit.Logger
import com.nuvio.app.features.discord.DiscordIdentify.Companion.toIdentifyPayload
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal enum class DiscordGatewayState {
    Idle,
    Connecting,
    Ready,
    Reconnecting,
    AuthenticationFailed,
    Failed,
    Closed,
}

/**
 * Discord gateway connection (user-token client, like Kizzy): identify/resume, heartbeat,
 * reconnects and presence updates. The token is only sent in IDENTIFY/RESUME and never logged.
 */
internal class DiscordGatewayConnection(
    private val token: String,
) {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val client: HttpClient = createDiscordGatewayHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var websocket: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sequence: Int? = null
    private var sessionId: String? = null
    private var resumeGatewayUrl: String? = null
    private var pendingPresence: DiscordPresence? = null
    private var reconnectAttempts = 0
    private var heartbeatAcknowledged = true
    private var closed = false
    private var lastCloseInfo: String? = null

    private val _state = MutableStateFlow(DiscordGatewayState.Idle)
    val state: StateFlow<DiscordGatewayState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun connect() {
        scope.launch {
            if (closed || connectionJob?.isActive == true) return@launch
            connectionJob = scope.launch { runConnection() }
        }
    }

    fun updatePresence(presence: DiscordPresence) {
        scope.launch {
            if (closed) return@launch
            pendingPresence = presence
            if (_state.value == DiscordGatewayState.Ready) {
                sendPresence(presence)
            }
        }
    }

    fun close() {
        scope.launch {
            if (closed) return@launch
            closed = true
            pendingPresence = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            val session = websocket
            websocket = null
            if (session != null) {
                withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
                    runCatching {
                        session.close(CloseReason(CloseReason.Codes.NORMAL, "Presence cleared"))
                    }
                }
            }
            connectionJob?.cancel()
            connectionJob = null
            runCatching { client.close() }
            sessionId = null
            resumeGatewayUrl = null
            sequence = null
            _state.value = DiscordGatewayState.Closed
            log.i { "Connection to gateway closed" }
            scope.cancel()
        }
    }

    private suspend fun runConnection() {
        while (currentCoroutineContext().isActive && !closed) {
            _state.value = if (reconnectAttempts == 0) {
                DiscordGatewayState.Connecting
            } else {
                DiscordGatewayState.Reconnecting
            }
            var closeCode: Int? = null
            var closeMessage: String? = null
            val usingResumeUrl = resumeGatewayUrl != null
            try {
                val url = resumeGatewayUrl ?: GATEWAY_URL
                log.i { "Connecting to $url" }
                client.webSocket(urlString = url) {
                    websocket = this
                    heartbeatAcknowledged = true
                    log.d { "Gateway socket opened" }
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> handleMessage(frame.readText())
                            is Frame.Close -> log.w { "Close frame received" }
                            else -> Unit
                        }
                    }
                    val reason = withTimeoutOrNull(CLOSE_TIMEOUT_MS) { closeReason.await() }
                    closeCode = reason?.code?.toInt()
                    closeMessage = reason?.message
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = error.message ?: error::class.simpleName ?: "Gateway connection error"
                _lastError.value = lastCloseInfo?.let { "$message (after $it)" } ?: message
                log.e(error) { "Gateway connection error" }
                if (usingResumeUrl) {
                    log.w { "Resume gateway unreachable, falling back to $GATEWAY_URL" }
                    resumeGatewayUrl = null
                }
            }

            heartbeatJob?.cancel()
            heartbeatJob = null
            websocket = null
            if (closed) return

            val code = closeCode
            val fatal = code != null && FATAL_CLOSE_CODES.contains(code)
            if (code != null) {
                val info = "Discord closed the connection ($code${closeMessage?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()})"
                lastCloseInfo = info
                _lastError.value = info
            }
            log.w { "Gateway closed code=$code reason=$closeMessage canReconnect=${code != AUTH_FAILED_CODE && !fatal}" }
            if (code == AUTH_FAILED_CODE) {
                _state.value = DiscordGatewayState.AuthenticationFailed
                return
            }
            if (fatal) {
                _state.value = DiscordGatewayState.Failed
                return
            }
            if (code == 4007 || code == 4009) resetSession()

            reconnectAttempts += 1
            if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
                log.w { "Giving up after $MAX_RECONNECT_ATTEMPTS reconnect attempts" }
                _state.value = DiscordGatewayState.Failed
                return
            }
            _state.value = DiscordGatewayState.Reconnecting
            delay(if (code == 4000) RECONNECT_FAST_DELAY_MS else (reconnectAttempts * RECONNECT_STEP_DELAY_MS).coerceAtMost(RECONNECT_MAX_DELAY_MS))
        }
    }

    private suspend fun handleMessage(jsonString: String) {
        val root = try {
            json.parseToJsonElement(jsonString) as? JsonObject
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w { "Unable to decode payload: ${error.message.orEmpty()}" }
            null
        } ?: return

        val op = root["op"].intValue()
        val seq = root["s"].intValue()
        val event = root["t"].stringValue()
        val data = root["d"]
        log.v { "Received op=$op seq=$seq event=$event" }

        seq?.let { sequence = it }
        when (op) {
            DiscordOpCode.DISPATCH.value -> handleDispatch(event, data)
            DiscordOpCode.HEARTBEAT.value -> sendHeartbeat()
            DiscordOpCode.RECONNECT.value -> reconnectWebSocket("Reconnect requested")
            DiscordOpCode.INVALID_SESSION.value -> handleInvalidSession(data)
            DiscordOpCode.HELLO.value -> handleHello(data)
            DiscordOpCode.HEARTBEAT_ACK.value -> heartbeatAcknowledged = true
            else -> Unit
        }
    }

    private suspend fun handleDispatch(event: String?, data: JsonElement?) {
        when (event) {
            "READY" -> {
                val ready = data as? JsonObject
                sessionId = ready?.get("session_id").stringValue()
                resumeGatewayUrl = ready?.get("resume_gateway_url").stringValue()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "${it.trimEnd('/')}/?v=10&encoding=json" }
                log.i { "Session ready" }
                onSessionReady()
            }
            "RESUMED" -> {
                log.i { "Session resumed" }
                onSessionReady()
            }
            "SESSIONS_REPLACE" -> logSessions(data)
            else -> Unit
        }
    }

    private suspend fun onSessionReady() {
        reconnectAttempts = 0
        lastCloseInfo = null
        _lastError.value = null
        _state.value = DiscordGatewayState.Ready
        pendingPresence?.let { sendPresence(it) }
    }

    private suspend fun handleInvalidSession(data: JsonElement?) {
        val resumable = (data as? JsonPrimitive)?.booleanOrNull == true
        log.w { "Invalid session, resumable=$resumable" }
        delay(INVALID_SESSION_DELAY_MS)
        if (resumable && !sessionId.isNullOrBlank() && sequence != null) {
            sendResume()
        } else {
            resetSession()
            sendIdentify()
        }
    }

    private suspend fun handleHello(data: JsonElement?) {
        if (sequence != null && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        val interval = (data as? JsonObject)?.get("heartbeat_interval").longValue() ?: DEFAULT_HEARTBEAT_INTERVAL_MS
        log.d { "Heartbeat interval $interval ms" }
        startHeartbeatJob(interval)
    }

    private fun resetSession() {
        sessionId = null
        sequence = null
        resumeGatewayUrl = null
    }

    private suspend fun sendHeartbeat() {
        heartbeatAcknowledged = false
        send(op = DiscordOpCode.HEARTBEAT, d = sequence)
    }

    private suspend fun reconnectWebSocket(reason: String) {
        log.i { reason }
        websocket?.close(CloseReason(4000, reason))
    }

    private suspend fun sendIdentify() {
        log.d { "Sending IDENTIFY" }
        send(op = DiscordOpCode.IDENTIFY, d = token.toIdentifyPayload())
    }

    private suspend fun sendResume() {
        log.d { "Sending RESUME" }
        send(
            op = DiscordOpCode.RESUME,
            d = DiscordResume(
                seq = sequence ?: 0,
                sessionId = sessionId,
                token = token,
            ),
        )
    }

    private suspend fun sendPresence(presence: DiscordPresence) {
        log.d { "Sending PRESENCE_UPDATE ${json.encodeToString(presence)}" }
        send(op = DiscordOpCode.PRESENCE_UPDATE, d = presence)
    }

    private fun logSessions(data: JsonElement?) {
        val sessions = data as? JsonArray ?: return
        sessions.forEach { element ->
            val session = element as? JsonObject ?: return@forEach
            val id = session["session_id"].stringValue()
            val label = when (id) {
                sessionId -> "this"
                "all" -> "all"
                else -> "other"
            }
            val activities = (session["activities"] as? JsonArray).orEmpty().mapNotNull { activity ->
                (activity as? JsonObject)?.let {
                    "type=${it["type"].intValue()} name=${it["name"].stringValue()}"
                }
            }
            log.v { "Session $label status=${session["status"].stringValue()} activities=$activities" }
            if (label == "this" && activities.isEmpty() && !pendingPresence?.activities.isNullOrEmpty()) {
                log.w { "Discord dropped the activity from this session" }
            }
        }
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay((interval * HEARTBEAT_JITTER).toLong())
            while (isActive) {
                if (!heartbeatAcknowledged) {
                    reconnectWebSocket("Heartbeat was not acknowledged")
                    return@launch
                }
                sendHeartbeat()
                delay(interval)
            }
        }
    }

    private suspend inline fun <reified T> send(op: DiscordOpCode, d: T?) {
        val session = websocket ?: return
        val payload = json.encodeToString(
            DiscordOutgoingPayload(
                op = op,
                d = d,
            ),
        )
        try {
            session.send(Frame.Text(payload))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w { "Unable to send $op: ${error.message.orEmpty()}" }
        }
    }

    private fun JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonElement?.intValue(): Int? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

    private fun JsonElement?.longValue(): Long? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull

    private companion object {
        val log = Logger.withTag(DISCORD_RPC_LOG_TAG)
        const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        const val AUTH_FAILED_CODE = 4004
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_FAST_DELAY_MS = 200L
        const val RECONNECT_STEP_DELAY_MS = 2_000L
        const val RECONNECT_MAX_DELAY_MS = 15_000L
        const val INVALID_SESSION_DELAY_MS = 1_500L
        const val CLOSE_TIMEOUT_MS = 3_000L
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 41_250L
        const val HEARTBEAT_JITTER = 0.5
        val FATAL_CLOSE_CODES = setOf(4010, 4011, 4012, 4013, 4014)
    }
}
