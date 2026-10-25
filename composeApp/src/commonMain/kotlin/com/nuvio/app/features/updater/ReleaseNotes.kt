package com.nuvio.app.features.updater

import androidx.compose.runtime.Immutable

internal const val ReleaseRepositoryUrl = "https://github.com/albyalex96/NuvioMobile-V2"

/** Release notes as published on GitHub, shown by the release notes dialog. */
@Immutable
internal data class ReleaseNotesSource(
    val tag: String,
    val title: String,
    val notes: String,
    val releaseUrl: String?,
)

internal fun AppUpdate.toReleaseNotesSource(): ReleaseNotesSource =
    ReleaseNotesSource(tag = tag, title = title, notes = notes, releaseUrl = releaseUrl)

internal enum class ReleaseNoteKind {
    Feature,
    Fix,
    Performance,
    Improvement,
    Other,
}

@Immutable
internal data class ReleaseNoteEntry(
    val title: String,
    val scope: String?,
    val kind: ReleaseNoteKind,
    /** Short commit hash or `#<pull request number>`. */
    val reference: String?,
    val url: String?,
    val author: String?,
)

@Immutable
internal data class ParsedReleaseNotes(
    val entries: List<ReleaseNoteEntry>,
    /** Lines that are neither a change nor the changelog link, kept as plain text. */
    val text: String,
    val compareUrl: String?,
) {
    val sections: List<Pair<ReleaseNoteKind, List<ReleaseNoteEntry>>>
        get() = ReleaseNoteKind.entries.mapNotNull { kind ->
            entries.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { kind to it }
        }
}

/**
 * Reads the notes written by `scripts/generate-release-notes.sh`:
 * `- <hash> type(scope): Title @author`, `- [Title (#12)](url) @author` and the final
 * `**Full changelog:** <compare url>`. Anything else stays as plain text.
 */
internal object ReleaseNotesParser {
    private val commitLine = Regex("^[-*]\\s+([0-9a-f]{7,40})\\s+(.+?)(?:\\s+@([\\w.-]+))?$")
    private val pullRequestLine =
        Regex("^[-*]\\s+\\[(.+?)\\s+\\(#(\\d+)\\)]\\((\\S+)\\)(?:\\s+@([\\w.-]+))?$")
    private val bulletLine = Regex("^[-*]\\s+(.+?)(?:\\s+@([\\w.-]+))?$")
    private val compareLine = Regex("^\\*\\*full changelog:?\\*\\*:?\\s*(\\S+)$", RegexOption.IGNORE_CASE)
    private val conventionalSubject = Regex("^([A-Za-z]+)(?:\\(([^)]*)\\))?!?:\\s*(.+)$")
    private val releasePage = Regex("^(https://github\\.com/[^/\\s]+/[^/\\s]+)/releases(?:/.*)?$")

    fun repositoryUrl(releaseUrl: String?): String =
        releaseUrl?.let { releasePage.matchEntire(it.trim())?.groupValues?.get(1) } ?: ReleaseRepositoryUrl

    fun parse(notes: String, repositoryUrl: String = ReleaseRepositoryUrl): ParsedReleaseNotes {
        val entries = mutableListOf<ReleaseNoteEntry>()
        val text = mutableListOf<String>()
        var compareUrl: String? = null
        notes.lineSequence().map(String::trim).forEach { line ->
            if (line.isEmpty()) {
                if (text.lastOrNull()?.isNotEmpty() == true) text += ""
                return@forEach
            }
            compareLine.matchEntire(line)?.let { match ->
                compareUrl = match.groupValues[1]
                return@forEach
            }
            pullRequestLine.matchEntire(line)?.let { match ->
                entries += entry(
                    subject = match.groupValues[1],
                    reference = "#${match.groupValues[2]}",
                    url = match.groupValues[3],
                    author = match.groupValues[4],
                )
                return@forEach
            }
            commitLine.matchEntire(line)?.let { match ->
                val hash = match.groupValues[1]
                entries += entry(
                    subject = match.groupValues[2],
                    reference = hash,
                    url = "$repositoryUrl/commit/$hash",
                    author = match.groupValues[3],
                )
                return@forEach
            }
            bulletLine.matchEntire(line)?.let { match ->
                entries += entry(
                    subject = match.groupValues[1],
                    reference = null,
                    url = null,
                    author = match.groupValues[2],
                )
                return@forEach
            }
            text += line
        }
        return ParsedReleaseNotes(
            entries = entries,
            text = text.joinToString("\n").trim(),
            compareUrl = compareUrl,
        )
    }

    private fun entry(subject: String, reference: String?, url: String?, author: String): ReleaseNoteEntry {
        val conventional = conventionalSubject.matchEntire(subject.trim())
        return ReleaseNoteEntry(
            title = (conventional?.groupValues?.get(3) ?: subject).trim().removeSuffix("."),
            scope = conventional?.groupValues?.get(2)?.trim()?.takeIf(String::isNotEmpty),
            kind = kindOf(conventional?.groupValues?.get(1)),
            reference = reference,
            url = url,
            author = author.takeIf(String::isNotEmpty),
        )
    }

    private fun kindOf(type: String?): ReleaseNoteKind = when (type?.lowercase()) {
        "feat", "feature" -> ReleaseNoteKind.Feature
        "fix", "hotfix" -> ReleaseNoteKind.Fix
        "perf" -> ReleaseNoteKind.Performance
        "refactor", "improve", "improvement", "ui" -> ReleaseNoteKind.Improvement
        else -> ReleaseNoteKind.Other
    }
}
