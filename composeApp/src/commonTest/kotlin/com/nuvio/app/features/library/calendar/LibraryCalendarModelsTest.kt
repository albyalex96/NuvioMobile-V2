package com.nuvio.app.features.library.calendar

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.library.LibraryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryCalendarModelsTest {

    @Test
    fun `epoch day conversion round trips across leap years and eras`() {
        assertEquals(0L, isoDateToEpochDay("1970-01-01"))
        assertEquals(19_723L, isoDateToEpochDay("2024-01-01"))
        assertEquals("2024-02-29", epochDayToIsoDate(isoDateToEpochDay("2024-02-29")!!))
        assertEquals("1969-12-31", epochDayToIsoDate(-1L))
        listOf("2000-02-29", "2026-10-20", "2100-03-01", "1999-12-31").forEach { date ->
            assertEquals(date, epochDayToIsoDate(isoDateToEpochDay(date)!!))
        }
        assertNull(isoDateToEpochDay("2024-13-01"))
    }

    @Test
    fun `day of week follows ISO numbering`() {
        assertEquals(1, isoDayOfWeek(isoDateToEpochDay("2024-01-01")!!))
        assertEquals(4, isoDayOfWeek(0L))
        assertEquals(2, isoDayOfWeek(isoDateToEpochDay("2026-10-20")!!))
        assertEquals(7, isoDayOfWeek(isoDateToEpochDay("2026-10-25")!!))
    }

    @Test
    fun `year month arithmetic handles year boundaries and lengths`() {
        val january = CalendarYearMonth(2026, 1)
        assertEquals(CalendarYearMonth(2025, 12), january.plusMonths(-1))
        assertEquals(CalendarYearMonth(2027, 2), january.plusMonths(13))
        assertEquals(29, CalendarYearMonth(2024, 2).lengthInDays)
        assertEquals(28, CalendarYearMonth(2026, 2).lengthInDays)
        assertEquals(31, CalendarYearMonth(2026, 10).lengthInDays)
        assertEquals("2026-10-01", CalendarYearMonth.ofIsoDate("2026-10-20")!!.firstDayIso)
        assertEquals("2026-11-02", plusDaysIso("2026-10-20", 13))
    }

    @Test
    fun `series events keep dated episodes inside the window`() {
        val target = LibraryItem(id = "tt1", type = "series", name = "Show", savedAtEpochMs = 0L).toLibraryCalendarTarget()!!
        val meta = MetaDetails(
            id = "tt1",
            type = "series",
            name = "Show",
            videos = listOf(
                MetaVideo(id = "tt1:1:1", title = "Old", released = "2020-01-01", season = 1, episode = 1),
                MetaVideo(id = "tt1:2:1", title = "Pilot", released = "2026-11-03", season = 2, episode = 1),
                MetaVideo(id = "tt1:2:2", title = "Undated", season = 2, episode = 2),
                MetaVideo(id = "trailer", title = "Trailer", released = "2026-11-01"),
            ),
        )

        val events = buildLibraryCalendarEvents(target, meta, minDateIso = "2026-07-22")

        assertEquals(1, events.size)
        assertEquals("2026-11-03", events.single().dateIso)
        assertEquals("Pilot", events.single().episodeTitle)
        assertTrue(events.single().isEpisode)
    }

    @Test
    fun `movies use the full release date and skip a bare year`() {
        val target = LibraryItem(id = "tt2", type = "movie", name = "Film", savedAtEpochMs = 0L).toLibraryCalendarTarget()!!
        val dated = MetaDetails(id = "tt2", type = "movie", name = "Film", released = "2026-12-18")
        val yearOnly = MetaDetails(id = "tt2", type = "movie", name = "Film", releaseInfo = "2026")

        assertEquals("2026-12-18", buildLibraryCalendarEvents(target, dated, "2026-07-22").single().dateIso)
        assertTrue(buildLibraryCalendarEvents(target, yearOnly, "2026-07-22").isEmpty())
    }

    @Test
    fun `unsupported library types are not tracked`() {
        assertNull(LibraryItem(id = "ch1", type = "channel", name = "Channel", savedAtEpochMs = 0L).toLibraryCalendarTarget())
        assertEquals("series:tt3", LibraryItem(id = "tt3", type = "tv", name = "Show", savedAtEpochMs = 0L).toLibraryCalendarTarget()?.key)
    }
}
