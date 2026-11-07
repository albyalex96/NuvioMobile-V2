package com.nuvio.app.features.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import co.touchlab.kermit.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private val audioEnhancementLog = Logger.withTag(AUDIO_ENHANCEMENT_LOG_TAG)

/** Settings shared between the player controller (UI thread) and the audio playback thread. */
internal class AudioEnhancementHolder {
    @Volatile
    var settings: AudioEnhancementSettings = AudioEnhancementSettings()
}

/**
 * ExoPlayer PCM processor for the dialogue boost and the dynamic range compression.
 *
 * - 5.1/7.1 (channel order FL FR FC LFE ...): the centre channel, where films put the dialogue,
 *   is raised and the other channels slightly lowered. The channel count is kept, so it works
 *   both with multichannel output and with the system downmix.
 * - Mono/stereo: the centre-panned part (mid = (L+R)/2) is band-passed on the voice range
 *   (~200 Hz - 4 kHz) and added back to both channels, while the original is slightly lowered.
 * - Compression: feed-forward compressor on the peak of all channels, followed by a soft limiter.
 *
 * Only 16-bit and float PCM are handled; anything else (e.g. passthrough) bypasses the processor.
 */
@OptIn(UnstableApi::class)
internal class DialogueEnhancementAudioProcessor(
    private val holder: AudioEnhancementHolder,
) : BaseAudioProcessor() {
    private var channelCount = 0
    private var sampleRate = 0
    private var isFloat = false

    private val highPass = arrayOf(Biquad(), Biquad())
    private val lowPass = arrayOf(Biquad(), Biquad())
    private var envelope = 0f
    private var lastSettings: AudioEnhancementSettings? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported || inputAudioFormat.channelCount <= 0) {
            audioEnhancementLog.d { "ExoPlayer audio enhancement bypassed for encoding=${inputAudioFormat.encoding}" }
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        audioEnhancementLog.i {
            "ExoPlayer audio enhancement configured: ${channelCount}ch ${sampleRate}Hz float=$isFloat"
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        configureFilters()
        envelope = 0f
    }

    override fun onReset() {
        channelCount = 0
        sampleRate = 0
        envelope = 0f
    }

    private fun configureFilters() {
        if (sampleRate <= 0) return
        highPass.forEach { it.setHighPass(sampleRate, 200.0) }
        lowPass.forEach { it.setLowPass(sampleRate, 4000.0) }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val settings = holder.settings
        if (settings != lastSettings) {
            audioEnhancementLog.d {
                "ExoPlayer audio enhancement: dialogue=${settings.dialogueBoostPercent}% " +
                    "compression=${settings.compressionPercent}% amplification=+${settings.amplificationDb}dB " +
                    "(${channelCount}ch)"
            }
            if (highPass[0].isUnset) configureFilters()
            lastSettings = settings
        }
        if (!settings.isActive) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (highPass[0].isUnset) configureFilters()

        val bytesPerSample = if (isFloat) 4 else 2
        val frameSize = bytesPerSample * channelCount
        val frameCount = remaining / frameSize
        val frame = FloatArray(channelCount)

        val dialogue = normalizeAudioEnhancementPercent(settings.dialogueBoostPercent) / 100f
        val voiceGain = 10f.pow(settings.dialogueGainDb / 20f) - 1f
        val surround = channelCount >= 6
        val centreGain = 1f + 3f * dialogue
        val otherGain = 1f - 0.6f * dialogue
        // Stereo: the voice band of the centre is boosted, the rest (sides = wide music/effects,
        // out-of-band centre = bass/cymbals) is attenuated, so the contrast is clearly audible.
        val sideGain = 1f - 0.75f * dialogue
        val restGain = 1f - 0.6f * dialogue

        val compress = settings.compressionPercent > 0
        val threshold = settings.compressionThresholdDb
        val ratio = settings.compressionRatio
        val makeup = 10f.pow(settings.compressionMakeupDb / 20f)
        val amplification = settings.amplificationGain
        val attack = coefficient(10.0)
        val release = coefficient(200.0)

        inputBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            for (c in 0 until channelCount) {
                frame[c] = if (isFloat) inputBuffer.float else inputBuffer.short / 32768f
            }
            if (dialogue > 0f) {
                if (surround) {
                    for (c in 0 until channelCount) {
                        frame[c] *= when (c) {
                            2 -> centreGain
                            3 -> otherGain * 0.6f // LFE: music/effects only
                            else -> otherGain
                        }
                    }
                } else if (channelCount >= 2) {
                    val mid = (frame[0] + frame[1]) * 0.5f
                    val side = (frame[0] - frame[1]) * 0.5f
                    val voice = lowPass[1].process(lowPass[0].process(highPass[1].process(highPass[0].process(mid))))
                    val newMid = voice * (1f + voiceGain) + (mid - voice) * restGain
                    val newSide = side * sideGain
                    frame[0] = newMid + newSide
                    frame[1] = newMid - newSide
                } else {
                    val voice = lowPass[1].process(lowPass[0].process(highPass[1].process(highPass[0].process(frame[0]))))
                    frame[0] = voice * (1f + voiceGain) + (frame[0] - voice) * restGain
                }
            }
            var gain = 1f
            if (compress) {
                var peak = 0f
                for (c in 0 until channelCount) peak = maxOf(peak, abs(frame[c]))
                envelope = if (peak > envelope) attack * envelope + (1f - attack) * peak
                else release * envelope + (1f - release) * peak
                val levelDb = if (envelope > 1e-6f) 20f * log10(envelope) else -120f
                if (levelDb > threshold) {
                    val reducedDb = (levelDb - threshold) * (1f - 1f / ratio)
                    gain = 10f.pow(-reducedDb / 20f)
                }
                gain *= makeup
            }
            gain *= amplification
            for (c in 0 until channelCount) {
                val sample = softLimit(frame[c] * gain)
                if (isFloat) {
                    output.putFloat(sample)
                } else {
                    output.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }
        // Drop a trailing partial frame (should not happen with well-formed PCM).
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun coefficient(timeMs: Double): Float =
        if (sampleRate <= 0) 0f else exp(-1.0 / (timeMs / 1000.0 * sampleRate)).toFloat()

    /** Transparent below 0.9, smooth saturation above so boosted audio never hard-clips. */
    private fun softLimit(x: Float): Float {
        val knee = 0.9f
        val a = abs(x)
        if (a <= knee) return x
        val over = (a - knee) / (1f - knee)
        val limited = knee + (1f - knee) * tanh(over)
        return if (x < 0f) -limited else limited
    }
}

/** RBJ biquad (Butterworth Q), direct form I. */
private class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f
    var isUnset = true
        private set

    fun setHighPass(sampleRate: Int, frequency: Double) = set(sampleRate, frequency, highPass = true)

    fun setLowPass(sampleRate: Int, frequency: Double) = set(sampleRate, frequency, highPass = false)

    private fun set(sampleRate: Int, frequency: Double, highPass: Boolean) {
        val f = frequency.coerceAtMost(sampleRate * 0.45)
        val w0 = 2.0 * PI * f / sampleRate
        val alpha = sin(w0) / (2.0 * (1.0 / sqrt(2.0)))
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha
        if (highPass) {
            b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
            b1 = (-(1.0 + cosW0) / a0).toFloat()
        } else {
            b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
        }
        b2 = b0
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        isUnset = false
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}
