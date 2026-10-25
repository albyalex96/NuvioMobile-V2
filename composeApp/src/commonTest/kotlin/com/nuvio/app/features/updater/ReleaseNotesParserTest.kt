package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseNotesParserTest {
    private val repository = "https://github.com/owner/repo"

    @Test
    fun commitLinesBecomeLinkedEntriesGroupedByType() {
        val parsed = ReleaseNotesParser.parse(
            """
            - e70a0de2 feat(tv): Always Show Recent Searches as Scrollable Chips @albyalex96
            - 81983491 fix(tv): Sidebar Selection Focus @albyalex96
            - 3c033699 feat(player): Clock in Player Controls @albyalex96

            **Full changelog:** https://github.com/owner/repo/compare/v0.7.4...v0.7.5
            """.trimIndent(),
            repository,
        )

        assertEquals(3, parsed.entries.size)
        val first = parsed.entries.first()
        assertEquals("Always Show Recent Searches as Scrollable Chips", first.title)
        assertEquals("tv", first.scope)
        assertEquals(ReleaseNoteKind.Feature, first.kind)
        assertEquals("e70a0de2", first.reference)
        assertEquals("$repository/commit/e70a0de2", first.url)
        assertEquals("albyalex96", first.author)
        assertEquals(
            listOf(ReleaseNoteKind.Feature to 2, ReleaseNoteKind.Fix to 1),
            parsed.sections.map { (kind, entries) -> kind to entries.size },
        )
        assertEquals("https://github.com/owner/repo/compare/v0.7.4...v0.7.5", parsed.compareUrl)
        assertEquals("", parsed.text)
    }

    @Test
    fun pullRequestLinesKeepTheirLink() {
        val entry = ReleaseNotesParser.parse(
            "- [perf(player): Faster Seek (#42)](https://github.com/owner/repo/pull/42) @someone  ",
            repository,
        ).entries.single()

        assertEquals("Faster Seek", entry.title)
        assertEquals(ReleaseNoteKind.Performance, entry.kind)
        assertEquals("#42", entry.reference)
        assertEquals("https://github.com/owner/repo/pull/42", entry.url)
        assertEquals("someone", entry.author)
    }

    @Test
    fun plainBulletsAndTextAreKeptWithoutLinks() {
        val parsed = ReleaseNotesParser.parse("Intro line.\n\n- Maintenance release", repository)

        val entry = parsed.entries.single()
        assertEquals("Maintenance release", entry.title)
        assertEquals(ReleaseNoteKind.Other, entry.kind)
        assertNull(entry.url)
        assertNull(entry.author)
        assertEquals("Intro line.", parsed.text)
        assertNull(parsed.compareUrl)
    }

    @Test
    fun repositoryUrlComesFromTheReleasePage() {
        assertEquals(
            "https://github.com/someone/fork",
            ReleaseNotesParser.repositoryUrl("https://github.com/someone/fork/releases/tag/v1.0.0"),
        )
        assertEquals(ReleaseRepositoryUrl, ReleaseNotesParser.repositoryUrl(null))
        assertEquals(ReleaseRepositoryUrl, ReleaseNotesParser.repositoryUrl("https://example.com/x"))
    }

    @Test
    fun releaseByTagResponseIsRead() {
        val source = AppUpdaterRepository.parseReleaseNotes(
            """{"tag_name": "v0.7.5", "name": "Nuvio Enhanced 0.7.5", "body": "- notes",
                "html_url": "https://github.com/owner/repo/releases/tag/v0.7.5"}""",
            fallbackTag = "v0.7.5",
        )

        assertEquals("v0.7.5", source.tag)
        assertEquals("Nuvio Enhanced 0.7.5", source.title)
        assertEquals("- notes", source.notes)
        assertEquals("https://github.com/owner/repo/releases/tag/v0.7.5", source.releaseUrl)
    }
}
