package com.nuvio.app.features.player

internal const val AUDIO_ENHANCEMENT_LOG_TAG = "AudioEnhancement"
internal const val AUDIO_ENHANCEMENT_MIN_PERCENT = 0
internal const val AUDIO_ENHANCEMENT_MAX_PERCENT = 100
internal const val AUDIO_ENHANCEMENT_STEP_PERCENT = 10

/** Dialogue boost / compression are implemented for ExoPlayer, libmpv and desktop mpv only. */
internal val audioEnhancementSupported: Boolean
    get() = !com.nuvio.app.isIos && !com.nuvio.app.isWeb

internal fun normalizeAudioEnhancementPercent(percent: Int): Int =
    percent.coerceIn(AUDIO_ENHANCEMENT_MIN_PERCENT, AUDIO_ENHANCEMENT_MAX_PERCENT)

/**
 * Audio enhancement requested by the user.
 *
 * - [dialogueBoostPercent]: 0 = off. Raises the dialogue (centre channel on 5.1/7.1, the
 *   centre-panned voice band on stereo) over music and effects.
 * - [compressionPercent]: 0 = off. Dynamic range compression ("night mode"): quiet parts louder,
 *   loud peaks tamed. Independent from the dialogue boost.
 */
data class AudioEnhancementSettings(
    val dialogueBoostPercent: Int = 0,
    val compressionPercent: Int = 0,
) {
    val isActive: Boolean get() = dialogueBoostPercent > 0 || compressionPercent > 0

    /** Gain (linear) applied to the extracted dialogue: up to +12 dB at 100%. */
    val dialogueGainDb: Float get() = 12f * normalizeAudioEnhancementPercent(dialogueBoostPercent) / 100f

    /** Compressor ratio: 1:1 (off) up to 8:1 at 100%. */
    val compressionRatio: Float get() = 1f + 7f * normalizeAudioEnhancementPercent(compressionPercent) / 100f

    /** Compressor threshold in dBFS: from -12 dB (light) to -30 dB (strong). */
    val compressionThresholdDb: Float get() = -12f - 18f * normalizeAudioEnhancementPercent(compressionPercent) / 100f

    /** Make-up gain in dB so the compressed signal is not quieter overall. */
    val compressionMakeupDb: Float
        get() = (-compressionThresholdDb) * (1f - 1f / compressionRatio) * 0.5f
}

/**
 * mpv `af` chain for the given settings (libmpv on Android, mpv on desktop), or an empty string
 * when nothing is active.
 *
 * - Dialogue: `pan` lifts the centre channel when the source has one (downmixed to stereo
 *   afterwards), then `dialoguenhance` extracts the centre-panned voice from the stereo image.
 * - Compression: `acompressor` with ratio/threshold/make-up from the slider, then `alimiter` to
 *   keep the boosted signal from clipping.
 */
internal fun AudioEnhancementSettings.toMpvAudioFilter(sourceChannels: Int?): String {
    if (!isActive) return ""
    val filters = mutableListOf<String>()
    if (dialogueBoostPercent > 0) {
        val strength = normalizeAudioEnhancementPercent(dialogueBoostPercent) / 100.0
        if (sourceChannels != null && sourceChannels >= 6) {
            // 5.1/7.1 order: FL FR FC LFE BL BR (SL SR). Centre gain up to 4x, others slightly reduced.
            val centre = fmt(1.0 + 3.0 * strength)
            val side = fmt(1.0 - 0.6 * strength)
            val rear = fmt((1.0 - 0.6 * strength) * 0.7)
            val lfe = fmt((1.0 - 0.7 * strength) * 0.5)
            val left = buildString {
                append("FL=$side*FL+$centre*FC+$lfe*LFE+$rear*BL")
                if (sourceChannels >= 8) append("+$rear*SL")
            }
            val right = buildString {
                append("FR=$side*FR+$centre*FC+$lfe*LFE+$rear*BR")
                if (sourceChannels >= 8) append("+$rear*SR")
            }
            filters += "lavfi=[pan=stereo|$left|$right]"
        } else {
            val original = fmt(1.0 - 0.8 * strength)
            val enhance = fmt(1.0 + 2.0 * strength)
            filters += "lavfi=[dialoguenhance=original=$original:enhance=$enhance:voice=${fmt(2.0 + 6.0 * strength)}]"
        }
    }
    if (compressionPercent > 0) {
        val thresholdLinear = dbToLinear(compressionThresholdDb)
        val makeup = dbToLinear(compressionMakeupDb).coerceIn(1.0, 64.0)
        filters += "lavfi=[acompressor=threshold=${fmt(thresholdLinear)}:ratio=${fmt(compressionRatio.toDouble())}:" +
            "attack=10:release=200:makeup=${fmt(makeup)}]"
    }
    filters += "lavfi=[alimiter=limit=0.97:level=disabled]"
    return filters.joinToString(",")
}

private fun dbToLinear(db: Float): Double = kotlin.math.exp(db * kotlin.math.ln(10.0) / 20.0)

private fun fmt(value: Double): String {
    val scaled = kotlin.math.round(value * 10000.0) / 10000.0
    return scaled.toString()
}
