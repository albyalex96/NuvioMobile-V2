package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalSearchMatchTest {

    @Test
    fun `opens the only title named like the query`() {
        val sections = listOf(
            section(
                MetaPreview(id = "tt0452046", type = "series", name = "Criminal Minds", releaseInfo = "2005–"),
                MetaPreview(id = "tt4974768", type = "series", name = "Criminal Minds: Beyond Borders", releaseInfo = "2016–2017"),
            ),
            // Same title from another addon: same year, different id.
            section(MetaPreview(id = "tmdb:4057", type = "series", name = "Criminal minds", releaseInfo = "2005")),
        )
        assertEquals("tt0452046", findExactSearchMatch("criminal minds", sections)?.id)
    }

    @Test
    fun `ignores case, accents and punctuation`() {
        val sections = listOf(section(MetaPreview(id = "tt1", type = "movie", name = "Amélie")))
        assertEquals("tt1", findExactSearchMatch("amelie", sections)?.id)
        val tom = listOf(section(MetaPreview(id = "tt2", type = "series", name = "Tom & Jerry")))
        assertEquals("tt2", findExactSearchMatch("Tom e Jerry", tom)?.id)
    }

    @Test
    fun `ambiguous or partial matches show the results`() {
        val filmAndSeries = listOf(
            section(
                MetaPreview(id = "tt1", type = "movie", name = "Fargo"),
                MetaPreview(id = "tt2", type = "series", name = "Fargo"),
            ),
        )
        assertNull(findExactSearchMatch("Fargo", filmAndSeries))
        val remakes = listOf(
            section(
                MetaPreview(id = "tt3", type = "movie", name = "Dune", releaseInfo = "2021"),
                MetaPreview(id = "tt4", type = "movie", name = "Dune", releaseInfo = "1984"),
            ),
        )
        assertNull(findExactSearchMatch("Dune", remakes))
        val partial = listOf(section(MetaPreview(id = "tt5", type = "series", name = "Criminal Minds: Evolution")))
        assertNull(findExactSearchMatch("Criminal Minds", partial))
    }

    private fun section(vararg items: MetaPreview) = HomeCatalogSection(
        key = items.first().id,
        title = "Search",
        subtitle = "",
        addonName = "Addon",
        target = CatalogTarget.Addon(
            manifestUrl = "https://addon/manifest.json",
            contentType = items.first().type,
            catalogId = "search",
            supportsPagination = false,
        ),
        items = items.toList(),
    )
}
