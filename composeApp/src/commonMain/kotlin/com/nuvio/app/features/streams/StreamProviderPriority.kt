package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import com.nuvio.app.features.plugins.PluginRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val providerPriorityLog = Logger.withTag("ProviderPriority")

/** Kinds of stream providers that the provider-priority auto-play mode can rank. */
enum class StreamProviderCategory {
    ADDONS,
    PLUGINS,
    CLOUDSTREAM,
}

val STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES: List<Int> = listOf(
    3, 5, 8, 10, 15, 20, 30, 45, 60, Int.MAX_VALUE,
)
const val DEFAULT_STREAM_PROVIDER_PRIORITY_TIMEOUT_SECONDS: Int = 10

/** Safety net of the "unlimited" timeout, for providers that never answer. */
internal const val PROVIDER_PRIORITY_HARD_TIMEOUT_MS: Long = 120_000L

/**
 * Settings of [StreamAutoPlayMode.PROVIDER_PRIORITY], saved as one JSON value so the whole
 * configuration travels atomically in the profile settings blob.
 *
 * [order] holds provider keys (see [StreamItem.priorityKey]) from the highest priority down.
 * Providers missing from it are new ones: they rank after every ordered provider and are
 * enabled unless listed in [disabled].
 */
@Serializable
data class StreamProviderPriorityConfig(
    val order: List<String> = emptyList(),
    val disabled: Set<String> = emptySet(),
    val categories: Set<StreamProviderCategory> = StreamProviderCategory.entries.toSet(),
    val timeoutSeconds: Int = DEFAULT_STREAM_PROVIDER_PRIORITY_TIMEOUT_SECONDS,
    val applyToFirstPlayback: Boolean = true,
) {
    fun includes(key: String): Boolean {
        val category = streamProviderCategoryOf(key) ?: return false
        if (category != StreamProviderCategory.ADDONS && !AppFeaturePolicy.pluginsEnabled) return false
        return category in categories && key !in disabled
    }

    /** Lower is better; unknown providers share the rank after the ordered ones. */
    fun rankOf(key: String): Int = order.indexOf(key).takeIf { it >= 0 } ?: order.size

    fun normalized(): StreamProviderPriorityConfig = copy(
        order = order.filter { it.isNotBlank() }.distinct(),
        timeoutSeconds = timeoutSeconds.takeIf { it in STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES }
            ?: DEFAULT_STREAM_PROVIDER_PRIORITY_TIMEOUT_SECONDS,
    )

    fun encode(): String = priorityJson.encodeToString(serializer(), this)

    companion object {
        private val priorityJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): StreamProviderPriorityConfig? {
            if (raw.isNullOrBlank()) return null
            return runCatching { priorityJson.decodeFromString(serializer(), raw).normalized() }
                .onFailure { error -> providerPriorityLog.w(error) { "Invalid provider priority settings, using defaults" } }
                .getOrNull()
        }
    }
}

/** Provider identity used for ranking: the plugin scraper or CloudStream provider, else the source group. */
val StreamItem.priorityKey: String
    get() = providerKey ?: addonId

fun streamProviderCategoryOf(key: String): StreamProviderCategory? = when {
    key.startsWith("addon:") -> StreamProviderCategory.ADDONS
    key.startsWith("plugin:") -> StreamProviderCategory.PLUGINS
    key.startsWith("cloudstream:") -> StreamProviderCategory.CLOUDSTREAM
    else -> null
}

internal fun pluginProviderKey(scraperId: String): String = "plugin:$scraperId"

/** Provider keys still waiting for a response in this group. */
internal fun AddonStreamGroup.pendingKeys(): Set<String> = when {
    !isLoading -> emptySet()
    pendingProviderKeys.isNotEmpty() -> pendingProviderKeys
    else -> setOf(addonId)
}

/** A provider shown in the priority settings. */
data class StreamPriorityProvider(
    val key: String,
    val name: String,
    val category: StreamProviderCategory,
    val sourceLabel: String? = null,
)

object StreamProviderCatalog {
    /** Every installed and enabled provider, grouped by category, in a stable order. */
    fun current(): List<StreamPriorityProvider> = buildList {
        AddonRepository.uiState.value.addons
            .enabledAddons()
            .forEach { addon ->
                val manifest = addon.manifest ?: return@forEach
                if (manifest.resources.none { resource -> resource.name == "stream" }) return@forEach
                add(
                    StreamPriorityProvider(
                        key = addon.streamAddonInstanceId(manifest.id),
                        name = addon.displayTitle.ifBlank { manifest.name },
                        category = StreamProviderCategory.ADDONS,
                    ),
                )
            }
        if (!AppFeaturePolicy.pluginsEnabled) return@buildList

        PluginRepository.initialize()
        val pluginState = PluginRepository.uiState.value
        val repositoryNames = pluginState.repositories.associate { it.manifestUrl to it.name }
        pluginState.scrapers
            .filter { it.enabled }
            .sortedBy { it.name.lowercase() }
            .forEach { scraper ->
                add(
                    StreamPriorityProvider(
                        key = pluginProviderKey(scraper.id),
                        name = scraper.name,
                        category = StreamProviderCategory.PLUGINS,
                        sourceLabel = repositoryNames[scraper.repositoryUrl]?.takeIf { it.isNotBlank() },
                    ),
                )
            }

        CloudStreamRepository.initialize()
        val cloudStreamState = CloudStreamRepository.uiState.value
        val cloudStreamRepositoryNames = cloudStreamState.repositories
            .associate { it.manifest.sourceUrl to it.manifest.name }
        cloudStreamState.plugins
            .filter(CloudStreamPluginItem::isRunnable)
            .sortedBy { it.metadata.name.lowercase() }
            .forEach { plugin ->
                add(
                    StreamPriorityProvider(
                        key = cloudStreamAddonId(plugin.metadata.id.value),
                        name = plugin.metadata.name.ifBlank { plugin.metadata.internalName },
                        category = StreamProviderCategory.CLOUDSTREAM,
                        sourceLabel = cloudStreamRepositoryNames[plugin.metadata.repositoryManifestUrl]
                            ?.takeIf { it.isNotBlank() },
                    ),
                )
            }
    }.distinctBy { it.key }
}

/** Ordered providers for the settings list: saved order first, then new providers at the end. */
fun StreamProviderPriorityConfig.orderedProviders(
    providers: List<StreamPriorityProvider>,
): List<StreamPriorityProvider> {
    val byKey = providers.associateBy { it.key }
    val ordered = order.mapNotNull(byKey::get)
    val orderedKeys = ordered.mapTo(HashSet()) { it.key }
    return ordered + providers.filter { it.key !in orderedKeys }
}

/**
 * Saves [visibleOrder] as the new ranking. Providers that are not installed here (for example
 * only on another device of the profile) keep their saved place relative to the end.
 */
fun StreamProviderPriorityConfig.withVisibleOrder(visibleOrder: List<String>): StreamProviderPriorityConfig {
    val visible = visibleOrder.toSet()
    return copy(order = (visibleOrder + order.filter { it !in visible }).distinct())
}

data class StreamProviderPriorityEvaluation(
    val stream: StreamItem? = null,
    /** Ready streams in priority order; the next ones are the fallback when playback fails. */
    val readyStreams: List<StreamItem> = emptyList(),
    /** The higher-priority provider whose answer is still awaited, if any. */
    val waitingFor: String? = null,
    val hasPendingProviders: Boolean = false,
) {
    /** True when a stream was picked or no included provider can answer anymore. */
    val isDecided: Boolean
        get() = stream != null || !hasPendingProviders

    fun toAutoPlayEvaluation(): StreamAutoPlayEvaluation = StreamAutoPlayEvaluation(
        stream = stream,
        readyStreams = readyStreams,
        hasPendingDebridCandidate = stream == null && hasPendingProviders,
    )
}

object StreamProviderPrioritySelector {
    /**
     * Picks the stream of the highest-ranked provider that has a playable stream, waiting for
     * every higher-ranked provider that has not answered yet. Once [timeoutElapsed] is true
     * pending providers are skipped and the best ranked ready stream wins.
     */
    fun evaluate(
        groups: List<AddonStreamGroup>,
        config: StreamProviderPriorityConfig,
        timeoutElapsed: Boolean,
        debridEnabled: Boolean = true,
        activeResolverProviderId: String? = null,
    ): StreamProviderPriorityEvaluation {
        val streamsByKey = LinkedHashMap<String, MutableList<StreamItem>>()
        val pendingKeys = LinkedHashSet<String>()
        groups.forEach { group ->
            group.pendingKeys().filterTo(pendingKeys, config::includes)
            group.streams.forEach { stream ->
                val key = stream.priorityKey
                if (config.includes(key)) streamsByKey.getOrPut(key) { mutableListOf() }.add(stream)
            }
        }
        streamsByKey.forEach { (key, streams) ->
            val waitingForDebrid = streams.none { StreamAutoPlaySelector.isReadyForAutoPlay(it, debridEnabled, activeResolverProviderId) } &&
                streams.any { StreamAutoPlaySelector.isPendingDebridForAutoPlay(it, debridEnabled, activeResolverProviderId) }
            if (waitingForDebrid) pendingKeys.add(key)
        }

        val rankedKeys = (streamsByKey.keys + pendingKeys).distinct().sortedBy(config::rankOf)
        var selected: StreamItem? = null
        var waitingFor: String? = null
        val readyStreams = mutableListOf<StreamItem>()
        rankedKeys.forEach { key ->
            val ready = streamsByKey[key].orEmpty().filter {
                StreamAutoPlaySelector.isReadyForAutoPlay(it, debridEnabled, activeResolverProviderId)
            }
            if (selected == null && waitingFor == null) {
                when {
                    ready.isNotEmpty() -> selected = ready.first()
                    key in pendingKeys && !timeoutElapsed -> waitingFor = key
                }
            }
            readyStreams += ready
        }
        val chosen = selected
        return StreamProviderPriorityEvaluation(
            stream = chosen,
            readyStreams = if (chosen != null) listOf(chosen) + readyStreams.filterNot { it == chosen } else readyStreams,
            waitingFor = waitingFor,
            hasPendingProviders = pendingKeys.isNotEmpty(),
        )
    }

    fun logDecision(context: String, evaluation: StreamProviderPriorityEvaluation, timeoutElapsed: Boolean) {
        val stream = evaluation.stream
        when {
            stream != null -> providerPriorityLog.i {
                "$context: selected provider=${stream.priorityKey} (${stream.addonName}) " +
                    "fallbacks=${evaluation.readyStreams.size - 1} timeoutElapsed=$timeoutElapsed"
            }
            evaluation.waitingFor != null -> providerPriorityLog.d {
                "$context: waiting for higher priority provider=${evaluation.waitingFor}"
            }
            evaluation.hasPendingProviders -> providerPriorityLog.d {
                "$context: timeout elapsed, no ready stream yet, waiting for any provider"
            }
            else -> providerPriorityLog.w { "$context: no included provider returned a playable stream" }
        }
    }
}
