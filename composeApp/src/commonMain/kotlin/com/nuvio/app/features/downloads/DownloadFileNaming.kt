package com.nuvio.app.features.downloads

/** Where an episode is saved when the episode folders are on: "Series/Season N/<code> - <title>.<ext>". */
internal data class EpisodeFileLocation(
    val folderPath: List<String>,
    val fileName: String,
)

/**
 * Builds the location of an episode from its localized names. [episodeCode] is already formatted
 * with the format chosen by the user (e.g. "S01E01" or "1x01").
 */
internal fun episodeFileLocation(
    seriesTitle: String,
    seasonFolderName: String,
    episodeCode: String,
    episodeTitle: String?,
    extension: String,
): EpisodeFileLocation? {
    val series = seriesTitle.toSafeFileName(MAX_FOLDER_NAME_LENGTH)
    val season = seasonFolderName.toSafeFileName(MAX_FOLDER_NAME_LENGTH)
    val code = episodeCode.toSafeFileName(MAX_FOLDER_NAME_LENGTH)
    if (series.isEmpty() || season.isEmpty() || code.isEmpty()) return null
    val title = episodeTitle?.toSafeFileName(MAX_FILE_NAME_LENGTH).orEmpty()
    val baseName = if (title.isEmpty()) code else "$code - $title"
    return EpisodeFileLocation(
        folderPath = listOf(series, season),
        fileName = "${baseName.take(MAX_FILE_NAME_LENGTH).trimEnd('.', ' ')}.$extension",
    )
}

/**
 * Removes only what file systems (Android shared storage, FAT/exFAT SD cards) refuse, keeping
 * accents and non-Latin letters of the localized names.
 */
internal fun String.toSafeFileName(maxLength: Int): String =
    replace(Regex("""[\/:*?"<>|\p{Cntrl}]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(maxLength)
        .trimEnd('.', ' ')
        .trimStart('.', ' ')

private const val MAX_FOLDER_NAME_LENGTH = 80
private const val MAX_FILE_NAME_LENGTH = 120
