package com.nuvio.app.core.logging

/** Viewer filters: a tag ([category], null = all), the visible levels and a free-text query. */
data class AppLogFilter(
    val category: String? = null,
    val levels: Set<AppLogLevel> = AppLogLevel.entries.toSet(),
    val query: String = "",
) {
    fun matches(entry: AppLogEntry): Boolean {
        if (entry.level !in levels) return false
        if (category != null && entry.tag != category) return false
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return entry.tag.contains(needle, ignoreCase = true) ||
            entry.message.contains(needle, ignoreCase = true) ||
            entry.throwable?.contains(needle, ignoreCase = true) == true
    }
}

/** Tag → number of entries, sorted by name: the dynamic category list of the viewer. */
fun List<AppLogEntry>.logCategories(): List<Pair<String, Int>> =
    groupingBy { it.tag }
        .eachCount()
        .toList()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

/** Plain-text rendering used by "copy" and "share". */
object AppLogText {
    const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

    fun formatEntry(entry: AppLogEntry): String = buildString {
        append(AppLogPlatform.formatTimestamp(entry.epochMs, TIMESTAMP_PATTERN))
        append(' ')
        append(entry.level.shortLabel)
        append('/')
        append(entry.tag)
        if (entry.source == AppLogSource.SYSTEM) append(" [logcat]")
        append(": ")
        append(entry.message)
        entry.throwable?.let { stack ->
            stack.lineSequence().forEach { line ->
                append("\n    ")
                append(line)
            }
        }
    }

    /** [entries] in chronological order, one block per entry. */
    fun format(entries: List<AppLogEntry>): String =
        entries.joinToString(separator = "\n", postfix = "\n") { formatEntry(it) }

    /**
     * Keeps the newest entries that fit in [maxChars] (the clipboard can't take megabytes of text).
     * Returns the text and how many entries it contains.
     */
    fun formatNewestWithin(entries: List<AppLogEntry>, maxChars: Int): Pair<String, Int> {
        val blocks = ArrayDeque<String>()
        var length = 0
        for (entry in entries.asReversed()) {
            val block = formatEntry(entry)
            if (blocks.isNotEmpty() && length + block.length + 1 > maxChars) break
            blocks.addFirst(block)
            length += block.length + 1
        }
        return blocks.joinToString(separator = "\n", postfix = "\n") to blocks.size
    }
}
