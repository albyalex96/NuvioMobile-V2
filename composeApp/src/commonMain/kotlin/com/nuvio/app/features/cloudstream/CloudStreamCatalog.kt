package com.nuvio.app.features.cloudstream

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogDefinition
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.search.DiscoverCatalogOption

/**
 * Catalogs imported from CloudStream providers whose "import catalogs" toggle is on. They are
 * exposed as regular Home definitions, Discover options and search sections, so the catalog
 * pickers and the row components treat them like addon catalogs.
 */
internal object CloudStreamCatalog {
    private val log = Logger.withTag("CloudStreamCatalog")

    private fun catalogProviders(): List<CloudStreamPluginItem> {
        if (!AppFeaturePolicy.pluginsEnabled) return emptyList()
        CloudStreamRepository.initialize()
        return CloudStreamRepository.uiState.value.plugins.filter(CloudStreamPluginItem::providesCatalogs)
    }

    /** Changes whenever the imported catalogs change, so catalog consumers can resync. */
    fun revision(): String = catalogProviders().joinToString("|") { plugin ->
        "${plugin.metadata.id.storageKey}:${plugin.catalogs.joinToString(",") { it.id.hashCode().toString() }}"
    }

    fun isCloudStreamKey(key: String): Boolean = key.startsWith(HOME_KEY_PREFIX)

    fun homeDefinitions(): List<HomeCatalogDefinition> = catalogProviders().flatMap { plugin ->
        val type = plugin.primaryContentType()
        val providerName = plugin.displayName()
        plugin.catalogs.map { catalog ->
            HomeCatalogDefinition(
                key = homeKey(plugin, type, catalog),
                defaultTitle = catalog.name,
                catalogName = catalog.name,
                addonName = providerName,
                manifestUrl = "",
                type = type,
                catalogId = catalogKey(catalog),
                supportsPagination = true,
                descriptorSignature = "${plugin.metadata.version}|${catalog.name}|${catalog.horizontalImages}",
                cloudStreamTarget = plugin.target(catalog, type),
            )
        }
    }

    fun discoverOptions(): List<DiscoverCatalogOption> = catalogProviders().flatMap { plugin ->
        val type = plugin.primaryContentType()
        plugin.catalogs.map { catalog ->
            DiscoverCatalogOption(
                key = homeKey(plugin, type, catalog),
                addonName = plugin.displayName(),
                manifestUrl = "",
                type = type,
                catalogId = catalogKey(catalog),
                catalogName = catalog.name,
                supportsPagination = true,
                cloudStream = plugin.target(catalog, type),
            )
        }
    }

    fun searchProviders(): List<CloudStreamPluginItem> = catalogProviders()

    suspend fun page(target: CatalogTarget.CloudStream, skip: Int): CatalogPage {
        target.searchQuery?.let { query ->
            val items = CloudStreamRepository.search(query, target.providerId)
                .firstOrNull()
                ?.getOrThrow()
                .orEmpty()
                .map { it.toMetaPreview() }
            return CatalogPage(items = items, rawItemCount = items.size, nextSkip = null)
        }
        val catalogId = requireNotNull(target.catalogId) { "CloudStream catalog is missing" }
        // CloudStream pages are numbered from 1: the catalog "skip" carries the next page number.
        val pageNumber = skip.takeIf { it > 0 } ?: 1
        val page = CloudStreamRepository.catalogPage(target.providerId, catalogId, pageNumber)
            .onFailure { error -> log.w(error) { "CloudStream catalog page failed provider=${target.providerId} page=$pageNumber" } }
            .getOrThrow()
        val items = page.items.map { item ->
            item.toMetaPreview().let { preview ->
                if (target.horizontalImages) preview.copy(posterShape = PosterShape.Landscape) else preview
            }
        }
        return CatalogPage(
            items = items,
            rawItemCount = items.size,
            nextSkip = (pageNumber + 1).takeIf { page.hasNext && items.isNotEmpty() },
        )
    }

    suspend fun section(definition: HomeCatalogDefinition, limit: Int): HomeCatalogSection {
        val target = requireNotNull(definition.cloudStreamTarget)
        val page = page(target, skip = 0)
        return HomeCatalogSection(
            key = definition.key,
            title = definition.defaultTitle,
            subtitle = definition.addonName,
            addonName = definition.addonName,
            target = target,
            items = page.items.take(limit),
            availableItemCount = page.rawItemCount,
            hasMore = page.items.size > limit || page.nextSkip != null,
        )
    }

    suspend fun searchSection(plugin: CloudStreamPluginItem, query: String): HomeCatalogSection {
        val results = CloudStreamRepository.search(query, plugin.metadata.id.value)
            .firstOrNull()
            ?.getOrThrow()
            .orEmpty()
        CloudStreamSearchRouteIndex.remember(providerId = plugin.metadata.id.value, query = query, items = results)
        val providerName = plugin.displayName()
        val type = results.firstOrNull()?.type?.nuvioType ?: plugin.primaryContentType()
        return HomeCatalogSection(
            key = "$HOME_KEY_PREFIX${plugin.metadata.id.storageKey}:search:${query.lowercase()}",
            title = providerName,
            subtitle = CLOUDSTREAM_LABEL,
            addonName = providerName,
            target = CatalogTarget.CloudStream(
                providerId = plugin.metadata.id.value,
                catalogId = null,
                contentType = type,
                searchQuery = query,
            ),
            items = results.map { it.toMetaPreview() },
        )
    }

    fun sourceLabel(target: CatalogTarget.CloudStream): String {
        CloudStreamRepository.initialize()
        return CloudStreamRepository.uiState.value.plugins
            .firstOrNull { it.metadata.id.value == target.providerId }
            ?.displayName()
            ?: CLOUDSTREAM_LABEL
    }

    private fun CloudStreamPluginItem.target(catalog: CloudStreamCatalogInfo, type: String) =
        CatalogTarget.CloudStream(
            providerId = metadata.id.value,
            catalogId = catalog.id,
            contentType = type,
            horizontalImages = catalog.horizontalImages,
        )

    // The key keeps four ':'-separated parts so the Home catalog sync carries it verbatim.
    private fun homeKey(plugin: CloudStreamPluginItem, type: String, catalog: CloudStreamCatalogInfo): String =
        "$HOME_KEY_PREFIX${plugin.metadata.id.storageKey.take(24)}:$type:${catalogKey(catalog)}"

    private fun catalogKey(catalog: CloudStreamCatalogInfo): String =
        sha256Hex(catalog.id.encodeToByteArray()).take(16)

    private fun CloudStreamPluginItem.displayName(): String = metadata.name.ifBlank { metadata.internalName }

    private fun CloudStreamPluginItem.primaryContentType(): String {
        val types = metadata.tvTypes.toSet()
        return when {
            CloudStreamTvType.Movie in types && CloudStreamTvType.TvSeries !in types -> "movie"
            CloudStreamTvType.TvSeries in types || CloudStreamTvType.Anime in types ||
                CloudStreamTvType.AsianDrama in types || CloudStreamTvType.Cartoon in types -> "series"
            CloudStreamTvType.Live in types -> "tv"
            else -> "movie"
        }
    }

    private const val HOME_KEY_PREFIX = "cloudstream:"
    private const val CLOUDSTREAM_LABEL = "CloudStream"
}
