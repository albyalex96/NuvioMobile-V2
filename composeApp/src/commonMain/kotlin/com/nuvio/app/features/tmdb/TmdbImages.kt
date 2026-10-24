package com.nuvio.app.features.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun tmdbImageLanguages(language: String): String {
    val normalizedLanguage = normalizeTmdbLanguage(language)
    return listOf(normalizedLanguage.substringBefore("-"), normalizedLanguage, "en", "null")
        .distinct()
        .joinToString(",")
}

internal fun List<TmdbImage>.selectBestLocalizedImagePath(normalizedLanguage: String): String? {
    val languageCode = normalizedLanguage.substringBefore("-")
    val regionCode = normalizedLanguage.substringAfter("-", "").uppercase().takeIf { it.length == 2 }
        ?: defaultLanguageRegions[languageCode]
    return filter { !it.filePath.isNullOrBlank() }.sortedWith(
        compareByDescending<TmdbImage> { it.iso6391 == languageCode && it.iso31661 == regionCode }
            .thenByDescending { it.iso6391 == languageCode && it.iso31661 == null }
            .thenByDescending { it.iso6391 == languageCode }
            .thenByDescending { it.iso6391 == "en" }
            .thenByDescending { it.iso6391 == null },
    ).firstOrNull()?.filePath?.trim()
}

/**
 * Gallery images for the detail "extra images" tab: images in the TMDB language first, then English,
 * then textless ones, keeping the TMDB order (by vote) inside each group.
 */
internal fun List<TmdbImage>.toGalleryImagePaths(normalizedLanguage: String, limit: Int = GALLERY_IMAGE_LIMIT): List<String> {
    val languageCode = normalizedLanguage.substringBefore("-")
    return mapNotNull { image -> image.filePath?.trim()?.takeIf(String::isNotBlank)?.let { image to it } }
        .distinctBy { it.second }
        .sortedBy { (image, _) ->
            when (image.iso6391) {
                languageCode -> 0
                "en" -> 1
                null -> 2
                else -> 3
            }
        }
        .take(limit)
        .map { it.second }
}

private const val GALLERY_IMAGE_LIMIT = 40

private val defaultLanguageRegions = mapOf(
    "pt" to "PT",
    "es" to "ES",
)

@Serializable
internal data class TmdbImagesResponse(
    val logos: List<TmdbImage> = emptyList(),
    val backdrops: List<TmdbImage> = emptyList(),
    val posters: List<TmdbImage> = emptyList(),
)

@Serializable
internal data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val iso6391: String? = null,
    @SerialName("iso_3166_1") val iso31661: String? = null,
)
