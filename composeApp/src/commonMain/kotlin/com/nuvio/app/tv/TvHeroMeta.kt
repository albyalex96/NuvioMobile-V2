package com.nuvio.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.i18n.localizedMediaStatusLabel
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.components.DetailHeroMetaBadge
import com.nuvio.app.features.details.components.ImdbRatingSourceLabel
import com.nuvio.app.features.details.components.RatingsRow
import com.nuvio.app.features.details.formatRuntimeForDisplay
import com.nuvio.app.features.home.MetaPreview
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.media_movie
import nuvio.composeapp.generated.resources.media_series
import org.jetbrains.compose.resources.stringResource

/** MDBList ratings shown inline on the hero (NuvioTV `MDBListRatingsRow(maxItems = 3)`). */
private const val HERO_RATINGS_LIMIT = 3

/** Metadata lines of the TV home hero, the fields of NuvioTV `HeroPreview`. */
@Immutable
data class TvHeroMeta(
    val type: String? = null,
    /** Episode code and title of a continue watching item, shown instead of the type. */
    val episodeLabel: String? = null,
    val genre: String? = null,
    val runtime: String? = null,
    val releaseInfo: String? = null,
    /** Continue watching state ("12 MIN LEFT", "NEXT UP"...), first on the second line. */
    val highlight: String? = null,
    val imdbRating: String? = null,
    val ageRating: String? = null,
    val status: String? = null,
    val language: String? = null,
    val ratings: List<MetaExternalRating> = emptyList(),
)

fun MetaPreview.toTvHeroMeta(): TvHeroMeta = TvHeroMeta(
    type = type,
    genre = genres.firstOrNull()?.takeIf { it.isNotBlank() },
    runtime = runtime,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    ageRating = ageRating,
    status = status,
    language = language,
    ratings = externalRatings,
)

/**
 * The two metadata lines of NuvioTV `HeroTitleContent`: "Type • Genre | runtime | year [IMDb]",
 * then the continue watching state, the age rating / status badge, IMDb, language and MDBList
 * ratings. IMDb moves to the second line for series, or when a badge or the state is there.
 */
@Composable
fun TvHeroMetaRows(
    meta: TvHeroMeta,
    showImdbRating: Boolean,
    showMdbListRatings: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.nuvio.colors
    val type = meta.type?.trim()?.lowercase()
    val isSeries = type == "series" || type == "tv"
    val typeLabel = meta.episodeLabel?.takeIf { it.isNotBlank() } ?: when (type) {
        null, "" -> null
        "movie" -> stringResource(Res.string.media_movie)
        "series" -> stringResource(Res.string.media_series)
        else -> meta.type.trim().replaceFirstChar(Char::uppercase)
    }
    val leading = listOfNotNull(typeLabel, meta.genre?.takeIf { it.isNotBlank() }).joinToString(" • ")
    val runtime = formatRuntimeForDisplay(meta.runtime)?.takeIf { it.isNotBlank() }
    val release = meta.releaseInfo?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay)
    val highlight = meta.highlight?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    val ageRating = meta.ageRating?.trim()?.takeIf { it.isNotBlank() }
    val status = meta.status?.trim()?.takeIf { it.isNotBlank() }
        ?.let { localizedMediaStatusLabel(it, isSeries).uppercase() }
    val language = meta.language?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    val ratings = if (showMdbListRatings) meta.ratings else emptyList()
    // NuvioTV: with MDBList ratings on screen the standalone IMDb rating is not shown.
    val imdb = meta.imdbRating?.trim()
        ?.takeIf { showImdbRating && ratings.isEmpty() }
        ?.takeIf { raw -> raw.toDoubleOrNull()?.let { it > 0.0 } == true }
    val hasBadge = ageRating != null || status != null
    val primaryImdb = imdb?.takeIf { !isSeries && !hasBadge && highlight == null }
    val secondaryImdb = imdb?.takeIf { primaryImdb == null }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hasTrailing = runtime != null || release != null || primaryImdb != null
        if (leading.isNotBlank() || hasTrailing) {
            MetaLine {
                if (leading.isNotBlank()) {
                    MetaText(
                        text = leading,
                        color = colors.textSecondary,
                        modifier = if (hasTrailing) Modifier.weight(1f, fill = false) else Modifier,
                    )
                }
                listOfNotNull(runtime, release).forEachIndexed { index, text ->
                    if (index > 0 || leading.isNotBlank()) MetaDot()
                    MetaText(text = text, color = colors.textSecondary)
                }
                primaryImdb?.let {
                    if (leading.isNotBlank() && runtime == null && release == null) MetaDot()
                    HeroImdbRating(rating = it)
                }
            }
        }

        if (highlight != null || hasBadge || secondaryImdb != null || language != null || ratings.isNotEmpty()) {
            MetaLine {
                var first = true
                fun separator(): Boolean = (!first).also { first = false }
                highlight?.let {
                    separator()
                    MetaText(text = it, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                if (hasBadge) {
                    if (separator()) MetaDot()
                    DetailHeroMetaBadge(
                        text = ageRating ?: status.orEmpty(),
                        secondaryText = status.takeIf { ageRating != null },
                        contentColor = colors.textPrimary,
                    )
                }
                secondaryImdb?.let {
                    if (separator()) MetaDot()
                    HeroImdbRating(rating = it)
                }
                language?.let {
                    if (separator()) MetaDot()
                    MetaText(text = it, color = colors.textMuted)
                }
                if (ratings.isNotEmpty()) {
                    if (separator()) MetaDot()
                    RatingsRow(ratings = ratings, maxItems = HERO_RATINGS_LIMIT)
                }
            }
        }
    }
}

@Composable
private fun MetaLine(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun HeroImdbRating(rating: String) {
    val style = MaterialTheme.typography.labelMedium
    Row(verticalAlignment = Alignment.CenterVertically) {
        ImdbRatingSourceLabel(storeTextStyle = style)
        Spacer(modifier = Modifier.width(4.dp))
        MetaText(text = rating, color = MaterialTheme.nuvio.colors.textSecondary)
    }
}

@Composable
private fun MetaText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** NuvioTV `HeroMetaDivider`: a small dot between the metadata. */
@Composable
private fun MetaDot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.nuvio.colors.textMuted.copy(alpha = 0.78f)),
    )
}
