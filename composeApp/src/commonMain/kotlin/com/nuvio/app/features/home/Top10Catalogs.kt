package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val TOP10_LOG_TAG = "Top10"
internal const val TOP10_ITEM_LIMIT = 10

/** The two "Top 10" rows of the home: one for movies, one for series. */
enum class Top10Kind(val rowKey: String) {
    Movies("top10_movies"),
    Series("top10_series"),
}

/**
 * "Top 10" home rows, synced with the profile through [Top10ProfileSync].
 * The catalogs are stored by their home catalog key (`manifestId:type:catalogId`, the same key
 * used by the home layout and the hero sources), so the choice survives addon URL changes.
 */
@Serializable
internal data class Top10Settings(
    val enabled: Boolean = false,
    val movieCatalogKey: String = "",
    val seriesCatalogKey: String = "",
) {
    fun catalogKey(kind: Top10Kind): String = when (kind) {
        Top10Kind.Movies -> movieCatalogKey
        Top10Kind.Series -> seriesCatalogKey
    }
}

/** A "Top 10" row ready to draw: the first [TOP10_ITEM_LIMIT] items of the chosen catalog. */
data class HomeTop10Row(
    val kind: Top10Kind,
    val section: HomeCatalogSection,
    val items: List<MetaPreview>,
) {
    val key: String get() = kind.rowKey
}

/**
 * Builds the "Top 10" rows from the loaded home catalogs. [sectionFor] returns the loaded (and
 * already filtered) section of a catalog key, or null when it is not loaded / not available.
 */
internal fun buildTop10Rows(
    settings: Top10Settings,
    sectionFor: (catalogKey: String) -> HomeCatalogSection?,
): List<HomeTop10Row> {
    if (!settings.enabled) return emptyList()
    return Top10Kind.entries.mapNotNull { kind ->
        val catalogKey = settings.catalogKey(kind).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val section = sectionFor(catalogKey) ?: return@mapNotNull null
        val items = section.items.distinctBy { it.stableKey() }.take(TOP10_ITEM_LIMIT)
        if (items.isEmpty()) return@mapNotNull null
        HomeTop10Row(kind = kind, section = section, items = items)
    }
}

internal object Top10Repository {
    private val log = Logger.withTag(TOP10_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(Top10Settings())
    val uiState: StateFlow<Top10Settings> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.value = Top10Storage.loadSettingsPayload()
            ?.let(::decode)
            ?: Top10Settings()
    }

    fun snapshot(): Top10Settings {
        ensureLoaded()
        return _uiState.value
    }

    fun onProfileChanged() {
        hasLoaded = false
        ensureLoaded()
        log.d { "Profile changed, enabled=${_uiState.value.enabled}" }
    }

    /** Account sign-out: the platform cleaner wipes the storage, here only the in-memory state goes. */
    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = Top10Settings()
    }

    fun applyRemoteSettings(settings: Top10Settings) {
        ensureLoaded()
        if (settings == _uiState.value) return
        log.d { "Applying Top 10 settings synced with the profile: enabled=${settings.enabled}" }
        update { settings }
    }

    fun setEnabled(enabled: Boolean) {
        log.i { "Top 10 rows ${if (enabled) "enabled" else "disabled"}" }
        update { it.copy(enabled = enabled) }
    }

    fun setCatalog(kind: Top10Kind, catalogKey: String) {
        log.i { "Top 10 ${kind.name} catalog ${if (catalogKey.isBlank()) "cleared" else "set to $catalogKey"}" }
        update {
            when (kind) {
                Top10Kind.Movies -> it.copy(movieCatalogKey = catalogKey)
                Top10Kind.Series -> it.copy(seriesCatalogKey = catalogKey)
            }
        }
    }

    private fun update(transform: (Top10Settings) -> Top10Settings) {
        ensureLoaded()
        val updated = transform(_uiState.value)
        if (updated == _uiState.value) return
        _uiState.value = updated
        Top10Storage.saveSettingsPayload(encode(updated))
        HomeRepository.applyCurrentSettings()
    }

    internal fun encode(settings: Top10Settings): String = json.encodeToString(settings)

    internal fun decode(payload: String): Top10Settings? =
        runCatching { json.decodeFromString<Top10Settings>(payload) }
            .onFailure { error -> log.w(error) { "Invalid Top 10 settings payload" } }
            .getOrNull()
}
