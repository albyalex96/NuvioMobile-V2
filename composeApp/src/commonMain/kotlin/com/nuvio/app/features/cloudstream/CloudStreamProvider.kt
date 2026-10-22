package com.nuvio.app.features.cloudstream

internal interface CloudStreamProvider {
    val id: String
    suspend fun getMainPage(page: Int): List<Pair<String, List<CloudStreamSearchItem>>>
    suspend fun search(query: String): List<CloudStreamSearchItem>
    suspend fun loadByExternalId(externalId: String): CloudStreamLoadItem? = null
    suspend fun load(data: String): CloudStreamLoadItem
    suspend fun loadLinks(data: String): List<CloudStreamPlaybackSource>
    suspend fun catalogs(): List<CloudStreamCatalogInfo> = emptyList()
    suspend fun catalogPage(catalogId: String, page: Int): CloudStreamCatalogPage =
        CloudStreamCatalogPage(items = emptyList(), hasNext = false)
}

internal expect object CloudStreamPlatformRuntime {
    val supportsAndroidDex: Boolean

    fun initialize(context: Any?)
    suspend fun provider(plugin: CloudStreamPluginItem): CloudStreamProvider?
    fun unload(pluginId: String)
    fun clear()
}

/** True where downloaded .cs3 packages can actually run (the full Android build). */
internal fun cloudStreamRuntimeSupported(): Boolean = CloudStreamPlatformRuntime.supportsAndroidDex
