package com.nuvio.app.features.streams.parser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_parser_cached
import nuvio.composeapp.generated.resources.stream_parser_language_arabic
import nuvio.composeapp.generated.resources.stream_parser_language_chinese
import nuvio.composeapp.generated.resources.stream_parser_language_czech
import nuvio.composeapp.generated.resources.stream_parser_language_danish
import nuvio.composeapp.generated.resources.stream_parser_language_dutch
import nuvio.composeapp.generated.resources.stream_parser_language_english
import nuvio.composeapp.generated.resources.stream_parser_language_finnish
import nuvio.composeapp.generated.resources.stream_parser_language_french
import nuvio.composeapp.generated.resources.stream_parser_language_german
import nuvio.composeapp.generated.resources.stream_parser_language_greek
import nuvio.composeapp.generated.resources.stream_parser_language_hindi
import nuvio.composeapp.generated.resources.stream_parser_language_hungarian
import nuvio.composeapp.generated.resources.stream_parser_language_italian
import nuvio.composeapp.generated.resources.stream_parser_language_japanese
import nuvio.composeapp.generated.resources.stream_parser_language_korean
import nuvio.composeapp.generated.resources.stream_parser_language_latin_spanish
import nuvio.composeapp.generated.resources.stream_parser_language_multi
import nuvio.composeapp.generated.resources.stream_parser_language_norwegian
import nuvio.composeapp.generated.resources.stream_parser_language_polish
import nuvio.composeapp.generated.resources.stream_parser_language_portuguese
import nuvio.composeapp.generated.resources.stream_parser_language_romanian
import nuvio.composeapp.generated.resources.stream_parser_language_russian
import nuvio.composeapp.generated.resources.stream_parser_language_spanish
import nuvio.composeapp.generated.resources.stream_parser_language_swedish
import nuvio.composeapp.generated.resources.stream_parser_language_turkish
import nuvio.composeapp.generated.resources.stream_parser_language_ukrainian
import nuvio.composeapp.generated.resources.stream_parser_proxied
import nuvio.composeapp.generated.resources.stream_parser_torrent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val ChipHeight = 22.dp
private val ChipShape = RoundedCornerShape(6.dp)
private val QualityShape = RoundedCornerShape(7.dp)

// --- Quality badge (header) -----------------------------------------------------------

private data class QualityStyle(val gradient: List<Color>, val text: Color, val glint: Color)

private fun qualityStyle(resolution: StreamParserResolution): QualityStyle = when (resolution) {
    StreamParserResolution.UHD_8K -> QualityStyle(listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC)), Color(0xFF1B1030), Color.White.copy(alpha = 0.95f))
    StreamParserResolution.UHD_4K -> QualityStyle(listOf(Color(0xFFFBE38E), Color(0xFFD4A017), Color(0xFFB8860B)), Color(0xFF3A2A00), Color(0xFFFFFDE8))
    StreamParserResolution.QHD -> QualityStyle(listOf(Color(0xFFF6CBB0), Color(0xFFC98B6B)), Color(0xFF3A1E10), Color.White.copy(alpha = 0.9f))
    StreamParserResolution.FHD -> QualityStyle(listOf(Color(0xFF6CB4FF), Color(0xFF1E5FBF)), Color.White, Color.White.copy(alpha = 0.75f))
    StreamParserResolution.HD -> QualityStyle(listOf(Color(0xFFE3A878), Color(0xFF9C5A2E)), Color.White, Color.White.copy(alpha = 0.7f))
    StreamParserResolution.SD -> QualityStyle(listOf(Color(0xFFA7B0BA), Color(0xFF5F6873)), Color.White, Color.White.copy(alpha = 0.6f))
}

@Composable
internal fun StreamParserQualityBadge(resolution: StreamParserResolution, animationsEnabled: Boolean) {
    val style = qualityStyle(resolution)
    val shimmer = rememberShimmerProgress(animationsEnabled)
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(QualityShape)
            .background(Brush.linearGradient(style.gradient))
            .shimmerSweep(shimmer, style.glint, animationsEnabled)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = resolution.label,
            color = style.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}

// --- Technical chips (centre band, left) ----------------------------------------------

private data class ChipStyle(val color: Color, val icon: ImageVector)

/**
 * One distinct colour per chip label, so no two quality chips look alike. None of them is
 * grey: grey-silver is reserved for the season/episode chips ([StreamParserEpisodeChipColor]).
 */
private val chipColors: Map<String, Color> = mapOf(
    // Sources.
    "REMUX" to Color(0xFFE879F9),
    "BluRay" to Color(0xFF3B82F6),
    "WEB-DL" to Color(0xFF38BDF8),
    "WEBRip" to Color(0xFF2DD4BF),
    "HDTV" to Color(0xFFA5B4FC),
    "DVD" to Color(0xFFFDBA74),
    "CAM" to Color(0xFFEF4444),
    // Video.
    "Dolby Vision" to Color(0xFFC084FC),
    "HDR10+" to Color(0xFFFCD34D),
    "HDR10" to Color(0xFFF59E0B),
    "HDR" to Color(0xFFFACC15),
    "HLG" to Color(0xFFF97316),
    "IMAX" to Color(0xFF99F6E4),
    "3D" to Color(0xFFFDA4AF),
    "12bit" to Color(0xFFDB2777),
    "10bit" to Color(0xFFF472B6),
    // Codecs.
    "HEVC" to Color(0xFFA78BFA),
    "AVC" to Color(0xFF6366F1),
    "AV1" to Color(0xFFBEF264),
    "VP9" to Color(0xFF6EE7B7),
    "XviD" to Color(0xFFFCA5A5),
    "MPEG-2" to Color(0xFFFEF08A),
    // Audio.
    "Atmos" to Color(0xFF34D399),
    "TrueHD" to Color(0xFF22C55E),
    "DTS:X" to Color(0xFFFB7185),
    "DTS-HD MA" to Color(0xFFF87171),
    "DTS-HD" to Color(0xFFFECDD3),
    "DTS" to Color(0xFFE11D48),
    "DD+" to Color(0xFFFB923C),
    "DD" to Color(0xFFD9F99D),
    "AAC" to Color(0xFF93C5FD),
    "FLAC" to Color(0xFF86EFAC),
    "LPCM" to Color(0xFFC4B5FD),
    "Opus" to Color(0xFFF0ABFC),
    "MP3" to Color(0xFFFDE047),
    // Channels.
    "7.1" to Color(0xFFA3E635),
    "5.1" to Color(0xFFFDE68A),
    "2.0" to Color(0xFFE9D5FF),
)

/** Channel layouts other than 7.1 / 5.1 / 2.0 (6.1, 4.0, …). */
private val OtherChannelsColor = Color(0xFFD8B4FE)

/** Neutral silver used only by the season/episode chips. */
internal val StreamParserEpisodeChipColor = Color(0xFFCBD5E1)

/** Warm stone used only by the fallback "Direct" chip. */
internal val StreamParserDirectChipColor = Color(0xFFA8A29E)

private fun chipIcon(tag: StreamParserTag): ImageVector = when (tag.kind) {
    StreamParserTagKind.SOURCE -> when (tag.label) {
        "WEB-DL", "WEBRip" -> Icons.Rounded.Cloud
        "HDTV" -> Icons.Rounded.Tv
        else -> Icons.Rounded.Album
    }
    StreamParserTagKind.CAM -> Icons.Rounded.Videocam
    StreamParserTagKind.DOLBY_VISION -> Icons.Rounded.AutoAwesome
    StreamParserTagKind.HDR -> Icons.Rounded.HdrOn
    StreamParserTagKind.VISUAL -> Icons.Rounded.Theaters
    StreamParserTagKind.BIT_DEPTH -> Icons.Rounded.Gradient
    StreamParserTagKind.CODEC -> Icons.Rounded.Memory
    StreamParserTagKind.AUDIO_PREMIUM, StreamParserTagKind.AUDIO -> Icons.Rounded.GraphicEq
    StreamParserTagKind.CHANNELS -> Icons.Rounded.SurroundSound
}

private fun chipStyle(tag: StreamParserTag): ChipStyle =
    ChipStyle(color = chipColors[tag.label] ?: OtherChannelsColor, icon = chipIcon(tag))

@Composable
internal fun StreamParserTechChip(tag: StreamParserTag) {
    val style = chipStyle(tag)
    StreamParserOutlinedChip(label = tag.label, icon = style.icon, color = style.color)
}

/** Tinted outlined chip shared by technical, default and episode chips. */
@Composable
internal fun StreamParserOutlinedChip(label: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .height(ChipHeight)
            .clip(ChipShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.42f), ChipShape)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            text = label,
            color = lerp(color, Color.White, 0.35f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Status badges (centre band, right) -----------------------------------------------

@Composable
private fun StatusBadge(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(ChipHeight)
            .clip(ChipShape)
            .background(background)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun StatusLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
internal fun StreamParserCachedBadge(animationsEnabled: Boolean) {
    val blink = rememberBlinkAlpha(animationsEnabled)
    val dark = Color(0xFF15803D)
    val bright = Color(0xFF22C55E)
    val glow = Color(0xFF86EFAC)
    StatusBadge(
        background = Color.Transparent,
        modifier = Modifier.drawBehind {
            // Read in the draw phase only: the blink never recomposes the card.
            val value = blink.value
            val radius = CornerRadius(6.dp.toPx())
            drawRoundRect(color = lerp(dark, bright, value), cornerRadius = radius)
            drawRoundRect(
                color = glow.copy(alpha = 0.25f + 0.45f * value),
                cornerRadius = radius,
                style = Stroke(width = 1.dp.toPx()),
            )
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = Color(0xFFDCFCE7),
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { alpha = 0.4f + 0.6f * blink.value },
        )
        StatusLabel(stringResource(Res.string.stream_parser_cached), Color(0xFFF0FDF4))
    }
}

@Composable
internal fun StreamParserTorrentBadge(animationsEnabled: Boolean) {
    val sway = rememberMagnetSway(animationsEnabled)
    val particles = rememberLoopProgress(animationsEnabled)
    val accent = Color(0xFFD8B4FE)
    StatusBadge(background = Color(0xFF6D28D9)) {
        Icon(
            imageVector = StreamParserIcons.Magnet,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { rotationZ = sway.value },
        )
        if (animationsEnabled) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(14.dp)
                    .attractedParticles(particles, accent, enabled = true),
            )
        }
        StatusLabel(stringResource(Res.string.stream_parser_torrent), Color(0xFFF3E8FF))
    }
}

@Composable
internal fun StreamParserProxiedBadge(animationsEnabled: Boolean) {
    val wave = rememberProxyWaveProgress(animationsEnabled)
    val accent = Color(0xFF7DD3FC)
    StatusBadge(background = Color(0xFF0E7490)) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .proxyWave(wave, accent, animationsEnabled),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp),
            )
        }
        StatusLabel(stringResource(Res.string.stream_parser_proxied), Color(0xFFECFEFF))
    }
}

// --- Languages --------------------------------------------------------------------------

private fun StreamParserLanguage.labelResource(): StringResource = when (this) {
    StreamParserLanguage.ITALIAN -> Res.string.stream_parser_language_italian
    StreamParserLanguage.ENGLISH -> Res.string.stream_parser_language_english
    StreamParserLanguage.FRENCH -> Res.string.stream_parser_language_french
    StreamParserLanguage.GERMAN -> Res.string.stream_parser_language_german
    StreamParserLanguage.SPANISH -> Res.string.stream_parser_language_spanish
    StreamParserLanguage.LATIN_SPANISH -> Res.string.stream_parser_language_latin_spanish
    StreamParserLanguage.PORTUGUESE -> Res.string.stream_parser_language_portuguese
    StreamParserLanguage.JAPANESE -> Res.string.stream_parser_language_japanese
    StreamParserLanguage.CHINESE -> Res.string.stream_parser_language_chinese
    StreamParserLanguage.KOREAN -> Res.string.stream_parser_language_korean
    StreamParserLanguage.RUSSIAN -> Res.string.stream_parser_language_russian
    StreamParserLanguage.UKRAINIAN -> Res.string.stream_parser_language_ukrainian
    StreamParserLanguage.POLISH -> Res.string.stream_parser_language_polish
    StreamParserLanguage.HUNGARIAN -> Res.string.stream_parser_language_hungarian
    StreamParserLanguage.CZECH -> Res.string.stream_parser_language_czech
    StreamParserLanguage.DUTCH -> Res.string.stream_parser_language_dutch
    StreamParserLanguage.DANISH -> Res.string.stream_parser_language_danish
    StreamParserLanguage.FINNISH -> Res.string.stream_parser_language_finnish
    StreamParserLanguage.NORWEGIAN -> Res.string.stream_parser_language_norwegian
    StreamParserLanguage.SWEDISH -> Res.string.stream_parser_language_swedish
    StreamParserLanguage.ROMANIAN -> Res.string.stream_parser_language_romanian
    StreamParserLanguage.GREEK -> Res.string.stream_parser_language_greek
    StreamParserLanguage.TURKISH -> Res.string.stream_parser_language_turkish
    StreamParserLanguage.ARABIC -> Res.string.stream_parser_language_arabic
    StreamParserLanguage.HINDI -> Res.string.stream_parser_language_hindi
    StreamParserLanguage.MULTI -> Res.string.stream_parser_language_multi
}

@Composable
internal fun StreamParserLanguage.localizedName(): String = stringResource(labelResource())
