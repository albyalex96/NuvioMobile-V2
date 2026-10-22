package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Top10RowsTest {
    private fun section(key: String, count: Int, type: String = "movie") = HomeCatalogSection(
        key = key,
        title = key,
        subtitle = "",
        addonName = "Addon",
        target = CatalogTarget.Addon(
            manifestUrl = "https://addon/manifest.json",
            contentType = type,
            catalogId = key,
            supportsPagination = false,
        ),
        items = (1..count).map { MetaPreview(id = "tt$it", type = type, name = "Title $it") },
    )

    @Test
    fun disabledSettingsBuildNoRows() {
        val rows = buildTop10Rows(Top10Settings(enabled = false, movieCatalogKey = "a")) { section(it, 20) }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun rowsKeepTheFirstTenItemsInOrder() {
        val rows = buildTop10Rows(
            Top10Settings(enabled = true, movieCatalogKey = "movies", seriesCatalogKey = "series"),
        ) { key -> section(key, 18, if (key == "series") "series" else "movie") }

        assertEquals(listOf(Top10Kind.Movies, Top10Kind.Series), rows.map { it.kind })
        rows.forEach { row ->
            assertEquals(TOP10_ITEM_LIMIT, row.items.size)
            assertEquals("tt1", row.items.first().id)
            assertEquals("tt10", row.items.last().id)
        }
    }

    @Test
    fun missingOrEmptyCatalogsAreSkipped() {
        val rows = buildTop10Rows(
            Top10Settings(enabled = true, movieCatalogKey = "gone", seriesCatalogKey = "empty"),
        ) { key -> if (key == "empty") section(key, 0) else null }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun duplicatedItemsDoNotTakeTwoRanks() {
        val base = section("movies", 3)
        val duplicated = base.copy(items = base.items + base.items.first())
        val rows = buildTop10Rows(Top10Settings(enabled = true, movieCatalogKey = "movies")) { duplicated }
        assertEquals(3, rows.single().items.size)
    }

    @Test
    fun settingsRoundTripThroughTheSyncPayload() {
        val settings = Top10Settings(enabled = true, movieCatalogKey = "a:movie:top", seriesCatalogKey = "a:series:top")
        assertEquals(settings, Top10Repository.decode(Top10Repository.encode(settings)))
    }
}
