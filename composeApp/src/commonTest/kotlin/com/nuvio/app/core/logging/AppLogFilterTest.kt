package com.nuvio.app.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogFilterTest {
    private fun entry(
        id: Long,
        level: AppLogLevel = AppLogLevel.INFO,
        tag: String = "Player",
        message: String = "message $id",
        throwable: String? = null,
    ) = AppLogEntry(id = id, epochMs = id * 1_000L, level = level, tag = tag, message = message, throwable = throwable)

    @Test
    fun defaultFilterMatchesEverything() {
        val filter = AppLogFilter()
        AppLogLevel.entries.forEachIndexed { index, level ->
            assertTrue(filter.matches(entry(index.toLong(), level = level)))
        }
    }

    @Test
    fun filtersByLevelAndCategory() {
        val filter = AppLogFilter(categories = setOf("Sync", "Auth"), levels = setOf(AppLogLevel.WARN, AppLogLevel.ERROR))
        assertTrue(filter.matches(entry(1, level = AppLogLevel.ERROR, tag = "Sync")))
        assertTrue(filter.matches(entry(2, level = AppLogLevel.WARN, tag = "Auth")))
        assertFalse(filter.matches(entry(3, level = AppLogLevel.INFO, tag = "Sync")))
        assertFalse(filter.matches(entry(4, level = AppLogLevel.ERROR, tag = "Player")))
    }

    @Test
    fun queryIsCaseInsensitiveAndSearchesTagMessageAndStackTrace() {
        val filter = AppLogFilter(query = "  timeout ")
        assertTrue(filter.matches(entry(1, message = "Request TIMEOUT after 10s")))
        assertTrue(filter.matches(entry(2, tag = "TimeoutWatcher")))
        assertTrue(filter.matches(entry(3, throwable = "java.net.SocketTimeoutException")))
        assertFalse(filter.matches(entry(4, message = "ok")))
    }

    @Test
    fun categoriesAreCountedAndSortedByName() {
        val categories = listOf(
            entry(1, tag = "sync"),
            entry(2, tag = "Player"),
            entry(3, tag = "sync"),
            entry(4, tag = "Addons"),
        ).logCategories()
        assertEquals(listOf("Addons" to 1, "Player" to 1, "sync" to 2), categories)
    }
}
