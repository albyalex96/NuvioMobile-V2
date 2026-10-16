package com.nuvio.app.features.player

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

const val AUDIO_DELAY_MIN_MS = -60_000
const val AUDIO_DELAY_MAX_MS = 60_000
const val AUDIO_DELAY_STEP_MS = 25

internal const val AUDIO_DELAY_LOG_TAG = "AudioDelay"

/**
 * Normalizes a `behaviorHints.audioDelayMs` value: whole milliseconds, NaN/Infinity dropped,
 * clamped to the player range (±60 s).
 */
fun Number?.toStreamAudioDelayMs(): Int? =
    this?.toDouble()
        ?.takeIf { it.isFinite() }
        ?.roundToInt()
        ?.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)

/** Reads `audioDelayMs` from a stream `behaviorHints` object. Only JSON numbers are accepted. */
internal fun JsonObject.streamAudioDelayMs(): Int? {
    val primitive = this["audioDelayMs"] as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return primitive.doubleOrNull.toStreamAudioDelayMs()
}

/**
 * Audio delay split into its sources, so the stream hint and the user's tweak on that stream
 * never end up in the per-device delay. The player always gets [totalMs].
 */
data class AudioDelayParts(
    /** Per-device delay (stored per audio output when "remember per device" is on). */
    val deviceMs: Int = 0,
    /** `behaviorHints.audioDelayMs` of the current stream. */
    val streamMs: Int = 0,
    /** User adjustment made while a stream delay is active; never persisted, dropped on stream change. */
    val sessionAdjustMs: Int = 0,
) {
    val totalMs: Int
        get() = (deviceMs.toLong() + streamMs + sessionAdjustMs)
            .coerceIn(AUDIO_DELAY_MIN_MS.toLong(), AUDIO_DELAY_MAX_MS.toLong())
            .toInt()

    /** Value to save for the current audio output, or null while a stream delay is active. */
    val persistableDeviceMs: Int?
        get() = deviceMs.takeIf { streamMs == 0 }

    /** The user picked [totalMs] in the Audio panel. */
    fun withManualTotal(totalMs: Int): AudioDelayParts {
        val clampedTotalMs = totalMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)
        return if (streamMs == 0) {
            copy(deviceMs = clampedTotalMs, sessionAdjustMs = 0)
        } else {
            copy(sessionAdjustMs = clampedTotalMs - deviceMs - streamMs)
        }
    }

    fun withStream(streamMs: Int): AudioDelayParts =
        copy(streamMs = streamMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS), sessionAdjustMs = 0)

    fun withDevice(deviceMs: Int): AudioDelayParts =
        copy(deviceMs = deviceMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS))
}

/** Active media audio output (speaker, headphones, Bluetooth, HDMI…). */
data class AudioOutputRoute(
    /** Stable identifier used to store the per-device delay. */
    val key: String,
    /** Human readable name shown in the Audio panel. */
    val label: String,
    val isBluetooth: Boolean = false,
)

/** "+1.250s", "-0.300s", "0.000s". */
internal fun formatAudioDelay(delayMs: Int): String {
    val sign = when {
        delayMs > 0 -> "+"
        delayMs < 0 -> "-"
        else -> ""
    }
    val absoluteMs = abs(delayMs)
    val seconds = absoluteMs / 1000
    val millis = absoluteMs % 1000
    return "$sign$seconds.${millis.toString().padStart(3, '0')}s"
}
