package com.nuvio.app.features.downloads.hls

import com.nuvio.app.features.player.languageMatchesPreference
import com.nuvio.app.features.player.normalizeLanguageCode
import kotlinx.serialization.Serializable

/** Localized words used by the track labels; kept apart so the label rules stay testable. */
data class HlsLabelStrings(
    val track: (index: Int) -> String,
    val forced: String,
    val audioDescription: String,
    val hearingImpaired: String,
    val singleQuality: String,
    val bitrate: (mbps: String) -> String,
)

data class HlsVideoOption(
    val id: String,
    val playlistUrl: String,
    val variant: HlsVariant?,
    val label: String,
)

data class HlsTrackOption(
    val id: String,
    val rendition: HlsRendition,
    val label: String,
    /** Audio carried inside the video segments (no own playlist). */
    val isMuxed: Boolean,
)

/**
 * The choices of the HLS download dialog in a form that can be applied to the playlist of another
 * episode: the season downloads ask once and pick the same quality and languages for every episode.
 */
@Serializable
data class HlsDownloadPreferences(
    val height: Int? = null,
    val bandwidth: Long? = null,
    /** Languages (or names, without a language) of the chosen audio tracks. */
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguages: List<String> = emptyList(),
    val forcedSubtitleLanguages: List<String> = emptyList(),
)

/** Everything the HLS download dialog can offer for one stream. */
class HlsDownloadChoices(
    private val playlist: HlsMultivariantPlaylist?,
    private val directPlaylistUrl: String,
    private val strings: HlsLabelStrings,
    private val languageName: (String) -> String?,
    /** Preferred audio languages of the app settings, in order (see resolvePreferredAudioLanguageTargets). */
    private val preferredAudioLanguages: List<String> = emptyList(),
) {
    val videoOptions: List<HlsVideoOption> = buildVideoOptions()

    fun audioOptions(video: HlsVideoOption): List<HlsTrackOption> {
        val groupId = video.variant?.audioGroupId ?: return emptyList()
        val renditions = playlist?.renditions.orEmpty()
            .filter { it.type == HlsRenditionType.Audio && it.groupId == groupId }
            .distinctRenditions()
        val labels = labelHlsRenditions(renditions, strings, languageName)
        return renditions.mapIndexed { index, rendition ->
            HlsTrackOption(
                id = rendition.uri ?: "muxed:${rendition.groupId}:${rendition.name}:${rendition.language}",
                rendition = rendition,
                label = labels[index],
                isMuxed = rendition.uri == null,
            )
        }
    }

    fun subtitleOptions(video: HlsVideoOption): List<HlsTrackOption> {
        val variant = video.variant ?: return emptyList()
        val groupId = variant.subtitlesGroupId ?: return emptyList()
        // IMSC (stpp) subtitles live in MP4 segments: only WebVTT can be converted.
        val subtitleCodecs = variant.codecs.filter(::isHlsSubtitleCodec)
        if (subtitleCodecs.isNotEmpty() && subtitleCodecs.none { it.lowercase().startsWith("wvtt") }) return emptyList()
        val renditions = playlist?.renditions.orEmpty()
            .filter { it.type == HlsRenditionType.Subtitles && it.groupId == groupId && it.uri != null }
            .distinctRenditions()
        val labels = labelHlsRenditions(renditions, strings, languageName)
        return renditions.mapIndexed { index, rendition ->
            HlsTrackOption(id = rendition.uri!!, rendition = rendition, label = labels[index], isMuxed = false)
        }
    }

    /**
     * Audio selected when the dialog opens: the track in the preferred language of the app
     * settings, otherwise the DEFAULT rendition, otherwise the first one.
     */
    fun defaultAudioIds(options: List<HlsTrackOption>): Set<String> {
        val preferred = preferredAudioLanguages.firstNotNullOfOrNull { language ->
            options.filter { languageMatchesPreference(it.rendition.language ?: it.rendition.name, language) }
                .let { matches -> matches.firstOrNull { it.rendition.isDefault } ?: matches.firstOrNull() }
        }
        return setOfNotNull((preferred ?: options.firstOrNull { it.rendition.isDefault } ?: options.firstOrNull())?.id)
    }

    /** Codecs of [video] that MP4 cannot store: shown as a hint that the file may become MKV. */
    fun mkvCodecs(video: HlsVideoOption): List<String> =
        hlsMp4IncompatibleCodecs(video.variant?.codecs.orEmpty()).map(::hlsCodecDisplayName).distinct()

    fun buildSpec(
        video: HlsVideoOption,
        audio: List<HlsTrackOption>,
        subtitles: List<HlsTrackOption>,
        durationSeconds: Double?,
    ): HlsDownloadSpec {
        val muxed = audio.firstOrNull { it.isMuxed }
        val hasAudioOptions = audioOptions(video).isNotEmpty()
        val separate = audio.filterNot { it.isMuxed }
        val defaultAudio = separate.firstOrNull { it.rendition.isDefault } ?: separate.firstOrNull()
        return HlsDownloadSpec(
            videoPlaylistUrl = video.playlistUrl,
            // Without a rendition list the audio can only be the one inside the video.
            includeMuxedAudio = !hasAudioOptions || muxed != null,
            muxedAudioLanguage = muxed?.rendition?.language,
            muxedAudioName = muxed?.rendition?.name,
            audio = separate.map { option ->
                option.toTrack(isDefault = muxed == null && option == defaultAudio)
            },
            subtitles = subtitles.map { it.toTrack(isDefault = it.rendition.forced) },
            bandwidth = video.variant?.let { it.averageBandwidth ?: it.bandwidth },
            durationSeconds = durationSeconds,
            container = predictHlsContainer(video.variant?.codecs.orEmpty()),
        )
    }

    fun preferencesFor(
        video: HlsVideoOption,
        audio: List<HlsTrackOption>,
        subtitles: List<HlsTrackOption>,
    ): HlsDownloadPreferences = HlsDownloadPreferences(
        height = video.variant?.height,
        bandwidth = video.variant?.let { it.averageBandwidth ?: it.bandwidth },
        audioLanguages = audio.mapNotNull { it.rendition.languageKey }.distinct(),
        subtitleLanguages = subtitles.filterNot { it.rendition.forced }.mapNotNull { it.rendition.languageKey }.distinct(),
        forcedSubtitleLanguages = subtitles.filter { it.rendition.forced }.mapNotNull { it.rendition.languageKey }.distinct(),
    )

    /**
     * Download of this playlist that follows [preferences]: the same height (the closest bitrate
     * among equal heights, otherwise the best one below), one track per preferred audio language
     * (the default audio when none matches) and the subtitles in the same languages.
     */
    fun specFor(preferences: HlsDownloadPreferences, durationSeconds: Double?): HlsDownloadSpec {
        val video = videoFor(preferences)
        val audioOptions = audioOptions(video)
        val matchedAudio = preferences.audioLanguages.mapNotNull { language ->
            audioOptions.firstOrNull { languageMatchesPreference(it.rendition.languageKey, language) }
        }.distinct()
        val audio = matchedAudio.ifEmpty {
            val defaults = defaultAudioIds(audioOptions)
            audioOptions.filter { it.id in defaults }
        }
        val subtitles = subtitleOptions(video).filter { option ->
            val languages = if (option.rendition.forced) preferences.forcedSubtitleLanguages else preferences.subtitleLanguages
            languages.any { languageMatchesPreference(option.rendition.languageKey, it) }
        }.distinctBy { it.rendition.languageKey?.lowercase() to it.rendition.forced }
        return buildSpec(video, audio, subtitles, durationSeconds)
    }

    private fun videoFor(preferences: HlsDownloadPreferences): HlsVideoOption {
        val target = preferences.height ?: return videoOptions.closestBandwidth(preferences.bandwidth)
        val sameHeight = videoOptions.filter { it.variant?.height == target }
        if (sameHeight.isNotEmpty()) return sameHeight.closestBandwidth(preferences.bandwidth)
        // Options are sorted from the highest: the first one below the target, else the lowest above.
        return videoOptions.firstOrNull { (it.variant?.height ?: 0) in 1 until target }
            ?: videoOptions.last()
    }

    private fun List<HlsVideoOption>.closestBandwidth(bandwidth: Long?): HlsVideoOption {
        if (bandwidth == null) return first()
        return minBy { option ->
            val optionBandwidth = option.variant?.let { it.averageBandwidth ?: it.bandwidth } ?: return@minBy Long.MAX_VALUE
            kotlin.math.abs(optionBandwidth - bandwidth)
        }
    }

    private fun HlsTrackOption.toTrack(isDefault: Boolean) = HlsDownloadTrack(
        playlistUrl = rendition.uri!!,
        language = rendition.language,
        name = rendition.name,
        label = label,
        isDefault = isDefault,
        forced = rendition.forced,
    )

    private fun buildVideoOptions(): List<HlsVideoOption> {
        val variants = playlist?.variants.orEmpty()
            .filter { variant -> variant.codecs.isEmpty() || variant.codecs.any(::isHlsVideoCodec) || variant.height != null }
            // The same video playlist can be listed once per audio group (AAC, AC-3…): keep each pair.
            .distinctBy { it.uri to it.audioGroupId }
            .sortedWith(
                compareByDescending<HlsVariant> { it.height ?: 0 }
                    .thenByDescending { it.averageBandwidth ?: it.bandwidth ?: 0L },
            )
        if (variants.isEmpty()) {
            return listOf(HlsVideoOption(directPlaylistUrl, directPlaylistUrl, null, strings.singleQuality))
        }
        val labels = labelHlsVariants(variants, strings)
        return variants.mapIndexed { index, variant ->
            HlsVideoOption("${variant.uri}#${variant.audioGroupId.orEmpty()}", variant.uri, variant, labels[index])
        }
    }
}

private val HlsRendition.languageKey: String?
    get() = language?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }

/** Drops repeated renditions: same playlist, or same language, name and channels. */
private fun List<HlsRendition>.distinctRenditions(): List<HlsRendition> =
    distinctBy { it.uri ?: "muxed:${it.name}:${it.language}" }
        .distinctBy { Triple(it.language?.lowercase(), it.name?.lowercase(), it.channels) }

/**
 * Labels as "<Language> [<original value>]", e.g. "Italiano [ita]". Channels, the rendition name
 * and finally a number are added only when they are needed to tell two tracks apart; "forced" and
 * accessibility traits are always shown.
 */
fun labelHlsRenditions(
    renditions: List<HlsRendition>,
    strings: HlsLabelStrings,
    languageName: (String) -> String?,
): List<String> {
    val primaries = renditions.mapIndexed { index, rendition ->
        val language = rendition.language?.let(languageName)?.capitalizeFirst()
        language ?: rendition.name?.capitalizeFirst() ?: strings.track(index + 1)
    }
    val fixed = renditions.map { rendition ->
        buildList {
            val characteristics = rendition.characteristics.orEmpty().lowercase()
            if ("describes-video" in characteristics) add(strings.audioDescription)
            if ("describes-music-and-sound" in characteristics || "transcribes-spoken-dialog" in characteristics) {
                add(strings.hearingImpaired)
            }
        }
    }
    val details = renditions.map { mutableListOf<String>() }

    fun duplicates(): Set<Int> {
        val keys = renditions.indices.map { index -> listOf(primaries[index]) + fixed[index] + details[index] + renditions[index].forced.toString() }
        return keys.indices.filter { index -> keys.count { it == keys[index] } > 1 }.toSet()
    }

    duplicates().forEach { index -> renditions[index].channels?.let(::formatHlsChannels)?.let(details[index]::add) }
    duplicates().forEach { index ->
        val rendition = renditions[index]
        val name = rendition.name
        if (name != null && !rendition.nameIsJustLanguage(primaries[index])) details[index] += name
    }
    duplicates().forEach { index -> details[index] += "#${index + 1}" }

    return renditions.mapIndexed { index, rendition ->
        buildString {
            append(primaries[index])
            (fixed[index] + details[index]).forEach { append(" · ").append(it) }
            if (rendition.forced) append(" (").append(strings.forced).append(')')
            val raw = rendition.language ?: rendition.name
            if (raw != null) append(" [").append(raw).append(']')
        }
    }
}

/** "1080p · H.264 · 6.2 Mbps", adding the audio codecs only when two versions would look the same. */
fun labelHlsVariants(variants: List<HlsVariant>, strings: HlsLabelStrings): List<String> {
    val base = variants.map { variant ->
        buildList {
            variant.height?.let { add("${it}p") }
            variant.codecs.firstOrNull(::isHlsVideoCodec)?.let { add(hlsCodecDisplayName(it)) }
            if (variant.videoRange.equals("PQ", true) || variant.videoRange.equals("HLG", true)) add("HDR")
            variant.frameRate?.takeIf { it > 31.0 }?.let { add("${it.toInt()} fps") }
        }
    }
    val bitrates = variants.map { variant -> (variant.averageBandwidth ?: variant.bandwidth)?.let { strings.bitrate(formatMbps(it)) } }
    val withAudio = variants.indices.map { index ->
        val sameVideo = variants.indices.count { base[it] == base[index] && bitrates[it] == bitrates[index] } > 1 ||
            variants.count { it.uri == variants[index].uri } > 1
        if (!sameVideo) emptyList() else variants[index].codecs
            .filterNot { isHlsVideoCodec(it) || isHlsSubtitleCodec(it) }
            .map(::hlsCodecDisplayName)
            .distinct()
    }
    return variants.indices.map { index ->
        val parts = base[index] + withAudio[index] + listOfNotNull(bitrates[index])
        parts.joinToString(" · ").ifBlank { strings.track(index + 1) }
    }
}

/** HLS CHANNELS ("2", "6", "16/JOC") as a speaker layout. */
fun formatHlsChannels(value: String): String {
    val count = value.substringBefore('/').trim().toIntOrNull() ?: return value
    if (value.contains("JOC", ignoreCase = true)) return "Atmos"
    return when (count) {
        1 -> "1.0"
        2 -> "2.0"
        3 -> "2.1"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$count ch"
    }
}

private fun formatMbps(bitsPerSecond: Long): String {
    val tenths = ((bitsPerSecond + 50_000L) / 100_000L).coerceAtLeast(1L)
    return if (tenths % 10L == 0L || tenths >= 100L) "${(tenths + 5L) / 10L}" else "${tenths / 10L}.${tenths % 10L}"
}

private fun HlsRendition.nameIsJustLanguage(languageLabel: String): Boolean {
    val name = name ?: return true
    if (name.equals(languageLabel, ignoreCase = true)) return true
    val fromName = normalizeLanguageCode(name)
    val fromLanguage = normalizeLanguageCode(language)
    return fromName != null && fromName == fromLanguage && name.length <= 20 && !name.any(Char::isDigit) &&
        name.trim().split(' ').size <= 2
}

private fun String.capitalizeFirst(): String = trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
