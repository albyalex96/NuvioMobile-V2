package com.nuvio.app.features.cloudstream

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetBytesWithHeaders
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.plugins.currentEpochMillis
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object CloudStreamRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("CloudStreamRepo")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _uiState = MutableStateFlow(CloudStreamUiState())
    actual val uiState: StateFlow<CloudStreamUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var currentProfileId = 1
    private val refreshJobs = mutableMapOf<String, Job>()
    private val catalogJobs = mutableMapOf<String, Job>()
    private var syncJob: Job? = null
    private var syncPassthrough: CloudStreamSyncState? = null

    /** Target state of a remote sync still being applied: exported instead of the half-applied local state. */
    private var pendingSyncState: CloudStreamSyncState? = null

    actual fun initialize() {
        val profileId = ProfileRepository.activeProfileId.coerceAtLeast(1)
        if (initialized && currentProfileId == profileId) return
        currentProfileId = profileId
        CloudStreamPlatformStorage.setActiveProfile(profileId)
        initialized = true
        _uiState.value = restoreState(profileId)
        resumePendingWork()
    }

    actual fun onProfileChanged(profileId: Int) {
        cancelBackgroundJobs()
        CloudStreamPlatformRuntime.clear()
        currentProfileId = profileId.coerceAtLeast(1)
        CloudStreamPlatformStorage.setActiveProfile(currentProfileId)
        initialized = false
        _uiState.value = CloudStreamUiState()
    }

    actual fun clearLocalState() {
        cancelBackgroundJobs()
        syncPassthrough = null
        initialized = false
        currentProfileId = 1
        _uiState.value = CloudStreamUiState()
        CloudStreamPlatformRuntime.clear()
        CloudStreamPlatformStorage.clearPackages()
        CloudStreamPlatformStorage.clearAllState()
    }

    actual fun acceptSecurityWarning() {
        initialize()
        _uiState.update { it.copy(securityWarningAccepted = true) }
        persist()
    }

    actual suspend fun addRepository(rawUrl: String): AddCloudStreamRepositoryResult {
        initialize()
        val manifestUrl = runCatching { resolveCloudStreamRepositoryInput(rawUrl) }
            .getOrElse { return AddCloudStreamRepositoryResult.Error(it.message ?: "Invalid repository URL") }
        if (_uiState.value.repositories.any { it.manifest.sourceUrl == manifestUrl }) {
            return AddCloudStreamRepositoryResult.Error("CloudStream repository is already added")
        }

        return runCatching { fetchRepository(manifestUrl) }
            .fold(
                onSuccess = { (repository, plugins) ->
                    _uiState.update { current ->
                        current.copy(
                            repositories = current.repositories + CloudStreamRepositoryItem(repository),
                            plugins = (current.plugins + plugins.map(::newPluginItem))
                                .distinctBy { it.metadata.id.value }
                                .sortedBy { it.metadata.name.lowercase() },
                        )
                    }
                    persist()
                    log.i { "CloudStream repository added url=$manifestUrl plugins=${plugins.size}" }
                    AddCloudStreamRepositoryResult.Success(repository)
                },
                onFailure = { error ->
                    log.w(error) { "CloudStream repository install failed url=$manifestUrl" }
                    AddCloudStreamRepositoryResult.Error(error.message ?: "Could not load CloudStream repository")
                },
            )
    }

    actual fun refreshRepository(manifestUrl: String) {
        initialize()
        val normalizedUrl = runCatching { normalizeCloudStreamRepositoryUrl(manifestUrl) }.getOrNull() ?: return
        if (refreshJobs[normalizedUrl]?.isActive == true) return
        _uiState.update { current ->
            current.copy(
                repositories = current.repositories.map { item ->
                    if (item.manifest.sourceUrl == normalizedUrl) item.copy(isRefreshing = true, errorMessage = null) else item
                },
            )
        }
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                runCatching { fetchRepository(normalizedUrl) }
                    .fold(
                        onSuccess = { (repository, plugins) -> applyRepositoryRefresh(repository, plugins) },
                        onFailure = { error ->
                            log.w(error) { "CloudStream repository refresh failed url=$normalizedUrl" }
                            _uiState.update { current ->
                                current.copy(
                                    repositories = current.repositories.map { item ->
                                        if (item.manifest.sourceUrl == normalizedUrl) {
                                            item.copy(isRefreshing = false, errorMessage = error.message ?: "Refresh failed")
                                        } else item
                                    },
                                )
                            }
                        },
                    )
                persist()
            } finally {
                if (refreshJobs[normalizedUrl] === job) refreshJobs.remove(normalizedUrl)
            }
        }
        refreshJobs[normalizedUrl] = job
        job.start()
    }

    actual fun refreshAll() {
        initialize()
        _uiState.value.repositories.forEach { refreshRepository(it.manifest.sourceUrl) }
    }

    actual fun removeRepository(manifestUrl: String) {
        initialize()
        val normalizedUrl = runCatching { normalizeCloudStreamRepositoryUrl(manifestUrl) }.getOrNull() ?: return
        val removedPlugins = _uiState.value.plugins.filter { it.metadata.repositoryManifestUrl == normalizedUrl }
        removedPlugins.forEach {
            CloudStreamPlatformRuntime.unload(it.metadata.id.value)
            CloudStreamPlatformStorage.deletePackage(it.metadata.id.storageKey)
        }
        _uiState.update { current ->
            current.copy(
                repositories = current.repositories.filterNot { it.manifest.sourceUrl == normalizedUrl },
                plugins = current.plugins.filterNot { it.metadata.repositoryManifestUrl == normalizedUrl },
                registryRevision = current.registryRevision + 1,
            )
        }
        persist()
    }

    actual suspend fun installPlugin(pluginId: String): CloudStreamInstallResult = installOrUpdate(pluginId)

    actual suspend fun updatePlugin(pluginId: String): CloudStreamInstallResult = installOrUpdate(pluginId)

    actual suspend fun installAndEnablePlugins(pluginIds: List<String>): CloudStreamBulkInstallResult {
        initialize()
        val requestedIds = pluginIds.distinct()
        if (requestedIds.isEmpty()) {
            return CloudStreamBulkInstallResult(
                requestedCount = 0,
                installedCount = 0,
                enabledCount = 0,
                skippedCount = 0,
            )
        }
        if (!_uiState.value.securityWarningAccepted) {
            return CloudStreamBulkInstallResult(
                requestedCount = requestedIds.size,
                installedCount = 0,
                enabledCount = 0,
                skippedCount = 0,
                failures = listOf(
                    CloudStreamBulkInstallFailure(
                        pluginName = "CloudStream",
                        message = "Accept the third-party plugin security warning before installation",
                    ),
                ),
            )
        }

        var installedCount = 0
        var enabledCount = 0
        var skippedCount = 0
        val failures = mutableListOf<CloudStreamBulkInstallFailure>()

        requestedIds.forEach { pluginId ->
            val before = _uiState.value.plugins.firstOrNull { it.metadata.id.value == pluginId }
            if (before == null) {
                failures += CloudStreamBulkInstallFailure(pluginName = pluginId, message = "CloudStream plugin was not found")
                return@forEach
            }
            if (before.isInstalling) {
                skippedCount += 1
                return@forEach
            }
            if (!before.metadata.status.canInstall) {
                failures += CloudStreamBulkInstallFailure(
                    pluginName = before.metadata.name,
                    message = "This CloudStream plugin is marked as down",
                )
                return@forEach
            }
            if (!before.compatibility.isRunnable) {
                failures += CloudStreamBulkInstallFailure(
                    pluginName = before.metadata.name,
                    message = before.compatibility.reason,
                )
                return@forEach
            }

            val needsInstall = !before.isInstalled || before.hasUpdate || !before.verified
            val installResult = if (needsInstall) installOrUpdate(pluginId) else CloudStreamInstallResult.Success(before)
            when (installResult) {
                is CloudStreamInstallResult.Success -> {
                    if (needsInstall) installedCount += 1
                    setPluginEnabled(pluginId, true)
                    val after = _uiState.value.plugins.firstOrNull { it.metadata.id.value == pluginId }
                    if (after?.isRunnable == true) {
                        enabledCount += 1
                    } else {
                        failures += CloudStreamBulkInstallFailure(
                            pluginName = installResult.plugin.metadata.name,
                            message = "The package was installed, but could not be enabled because verification or compatibility checks failed.",
                        )
                    }
                }
                is CloudStreamInstallResult.Error -> {
                    failures += CloudStreamBulkInstallFailure(
                        pluginName = before.metadata.name,
                        message = installResult.message,
                    )
                }
            }
        }

        return CloudStreamBulkInstallResult(
            requestedCount = requestedIds.size,
            installedCount = installedCount,
            enabledCount = enabledCount,
            skippedCount = skippedCount,
            failures = failures,
        )
    }

    actual fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        initialize()
        _uiState.update { current ->
            var changed = false
            val plugins = current.plugins.map { item ->
                if (item.metadata.id.value != pluginId) return@map item
                val nextEnabled = enabled && item.isInstalled && item.verified && item.compatibility.isRunnable &&
                    item.metadata.status.canInstall
                changed = item.enabled != nextEnabled
                item.copy(enabled = nextEnabled, errorMessage = null)
            }
            current.copy(
                plugins = plugins,
                registryRevision = current.registryRevision + if (changed) 1 else 0,
            )
        }
        if (!enabled) CloudStreamPlatformRuntime.unload(pluginId)
        persist()
        if (enabled) discoverCatalogsIfNeeded(pluginId)
    }

    actual fun setPluginImportCatalogs(pluginId: String, enabled: Boolean) {
        initialize()
        _uiState.update { current ->
            current.copy(
                plugins = current.plugins.map { item ->
                    if (item.metadata.id.value == pluginId) item.copy(importCatalogs = enabled) else item
                },
                registryRevision = current.registryRevision + 1,
            )
        }
        log.i { "CloudStream catalogs import id=$pluginId enabled=$enabled" }
        persist()
        if (enabled) discoverCatalogsIfNeeded(pluginId)
    }

    actual fun setGroupStreamsByRepository(enabled: Boolean) {
        initialize()
        if (_uiState.value.groupStreamsByRepository == enabled) return
        _uiState.update { it.copy(groupStreamsByRepository = enabled) }
        persist()
    }

    actual fun setUseFullDetails(enabled: Boolean) {
        initialize()
        if (_uiState.value.useFullDetails == enabled) return
        _uiState.update { it.copy(useFullDetails = enabled) }
        log.i { "CloudStream full details enabled=$enabled" }
        persist()
    }

    actual fun removePlugin(pluginId: String) {
        initialize()
        val plugin = _uiState.value.plugins.firstOrNull { it.metadata.id.value == pluginId } ?: return
        CloudStreamPlatformRuntime.unload(pluginId)
        CloudStreamPlatformStorage.deletePackage(plugin.metadata.id.storageKey)
        _uiState.update { current ->
            current.copy(
                plugins = current.plugins.map { item ->
                    if (item.metadata.id.value == pluginId) {
                        item.copy(
                            installedVersion = null,
                            installedAtEpochMs = 0L,
                            enabled = false,
                            verified = false,
                            errorMessage = null,
                            catalogs = emptyList(),
                            autoInstallPending = false,
                            autoInstallEnable = false,
                        )
                    } else item
                },
                registryRevision = current.registryRevision + 1,
            )
        }
        persist()
    }

    actual suspend fun getMainPage(
        providerId: String,
        page: Int,
    ): Result<List<Pair<String, List<CloudStreamSearchItem>>>> = providerResult(providerId) {
        getMainPage(page.coerceAtLeast(1))
    }

    actual suspend fun search(
        query: String,
        providerId: String?,
    ): List<Result<List<CloudStreamSearchItem>>> {
        initialize()
        val activeIds = runnableProviderIds().filter { providerId == null || it == providerId }
        return activeIds.map { id -> providerResult(id) { search(query.trim()) } }
    }

    actual suspend fun load(providerId: String, data: String): Result<CloudStreamLoadItem> =
        providerResult(providerId) { load(data) }

    actual suspend fun loadByExternalId(
        providerId: String,
        externalId: String,
    ): Result<CloudStreamLoadItem?> = providerResult(providerId) {
        loadByExternalId(externalId)
    }

    actual suspend fun loadLinks(providerId: String, data: String): Result<List<CloudStreamPlaybackSource>> =
        providerResult(providerId) { loadLinks(data) }

    actual suspend fun catalogPage(
        providerId: String,
        catalogId: String,
        page: Int,
    ): Result<CloudStreamCatalogPage> = providerResult(providerId) {
        catalogPage(catalogId, page.coerceAtLeast(1))
    }

    actual fun exportSyncState(): CloudStreamSyncState? {
        initialize()
        pendingSyncState?.let { return it }
        if (!CloudStreamPlatformRuntime.supportsAndroidDex) return syncPassthrough
        val current = _uiState.value
        return CloudStreamSyncState(
            repositories = current.repositories.map { it.manifest.sourceUrl },
            plugins = current.plugins
                .filter { it.isInstalled || it.autoInstallPending }
                .map { item ->
                    CloudStreamSyncPlugin(
                        repositoryUrl = item.metadata.repositoryManifestUrl,
                        internalName = item.metadata.internalName,
                        enabled = if (item.isInstalled) item.enabled else item.autoInstallEnable,
                        importCatalogs = item.importCatalogs,
                    )
                },
            groupStreamsByRepository = current.groupStreamsByRepository,
            securityWarningAccepted = current.securityWarningAccepted,
            useFullDetails = current.useFullDetails,
        )
    }

    actual fun applySyncState(state: CloudStreamSyncState) {
        initialize()
        if (!CloudStreamPlatformRuntime.supportsAndroidDex) {
            // Packages cannot run here: keep the remote configuration untouched so a push from
            // this device never wipes the plugins installed on the Android devices.
            syncPassthrough = state
            persist()
            log.i { "CloudStream sync kept as passthrough (unsupported platform)" }
            return
        }
        if (state == exportSyncState()) return
        pendingSyncState = state
        syncJob?.cancel()
        syncJob = scope.launch {
            try {
                applySyncStateNow(state)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                log.e(error) { "CloudStream sync apply failed" }
            } finally {
                if (pendingSyncState === state) pendingSyncState = null
            }
        }
    }

    private suspend fun applySyncStateNow(state: CloudStreamSyncState) {
        log.i { "Applying CloudStream sync repos=${state.repositories.size} plugins=${state.plugins.size}" }
        if (state.securityWarningAccepted && !_uiState.value.securityWarningAccepted) {
            _uiState.update { it.copy(securityWarningAccepted = true) }
        }
        if (_uiState.value.groupStreamsByRepository != state.groupStreamsByRepository) {
            _uiState.update { it.copy(groupStreamsByRepository = state.groupStreamsByRepository) }
        }
        if (_uiState.value.useFullDetails != state.useFullDetails) {
            _uiState.update { it.copy(useFullDetails = state.useFullDetails) }
        }
        val remoteRepositories = state.repositories.mapNotNull { url ->
            runCatching { normalizeCloudStreamRepositoryUrl(url) }.getOrNull()
        }.distinct()
        _uiState.value.repositories
            .map { it.manifest.sourceUrl }
            .filterNot { it in remoteRepositories }
            .forEach(::removeRepository)
        remoteRepositories
            .filterNot { url -> _uiState.value.repositories.any { it.manifest.sourceUrl == url } }
            .forEach { url ->
                val result = addRepository(url)
                if (result is AddCloudStreamRepositoryResult.Error) {
                    log.w { "CloudStream sync could not add repository url=$url: ${result.message}" }
                }
            }

        val wanted = state.plugins.associateBy { plugin ->
            val repositoryUrl = runCatching { normalizeCloudStreamRepositoryUrl(plugin.repositoryUrl) }
                .getOrDefault(plugin.repositoryUrl)
            CloudStreamPluginId(repositoryUrl, plugin.internalName).value
        }
        _uiState.value.plugins
            .filter { it.isInstalled && it.metadata.id.value !in wanted }
            .forEach { removePlugin(it.metadata.id.value) }
        _uiState.update { current ->
            current.copy(
                plugins = current.plugins.map { item ->
                    val remote = wanted[item.metadata.id.value]
                    when {
                        remote == null -> item.copy(autoInstallPending = false, autoInstallEnable = false)
                        item.isInstalled -> item.copy(importCatalogs = remote.importCatalogs)
                        else -> item.copy(
                            importCatalogs = remote.importCatalogs,
                            autoInstallPending = true,
                            autoInstallEnable = remote.enabled,
                        )
                    }
                },
                registryRevision = current.registryRevision + 1,
            )
        }
        _uiState.value.plugins
            .filter { it.isInstalled }
            .forEach { item ->
                val remote = wanted[item.metadata.id.value] ?: return@forEach
                if (item.enabled != remote.enabled) setPluginEnabled(item.metadata.id.value, remote.enabled)
                if (remote.importCatalogs) discoverCatalogsIfNeeded(item.metadata.id.value)
            }
        persist()
        runAutoInstalls()
    }

    /** Installs the plugins the profile sync asked for; failures stay pending and are retried later. */
    private suspend fun runAutoInstalls() {
        val pending = _uiState.value.plugins.filter { it.autoInstallPending && !it.isInstalling }
        if (pending.isEmpty()) return
        if (!_uiState.value.securityWarningAccepted) {
            log.w { "CloudStream auto-install skipped: security warning not accepted" }
            return
        }
        log.i { "CloudStream auto-install of ${pending.size} plugin(s) from profile sync" }
        pending.forEach { item ->
            val pluginId = item.metadata.id.value
            when (val result = installOrUpdate(pluginId)) {
                is CloudStreamInstallResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            plugins = current.plugins.map { plugin ->
                                if (plugin.metadata.id.value == pluginId) {
                                    plugin.copy(autoInstallPending = false, autoInstallEnable = false)
                                } else plugin
                            },
                        )
                    }
                    setPluginEnabled(pluginId, item.autoInstallEnable)
                    log.i { "CloudStream auto-installed id=$pluginId enabled=${item.autoInstallEnable}" }
                }
                is CloudStreamInstallResult.Error ->
                    log.w { "CloudStream auto-install failed id=$pluginId: ${result.message}" }
            }
        }
        persist()
    }

    private fun resumePendingWork() {
        val state = _uiState.value
        if (state.plugins.any { it.autoInstallPending } && CloudStreamPlatformRuntime.supportsAndroidDex) {
            if (syncJob?.isActive != true) syncJob = scope.launch { runAutoInstalls() }
        }
        state.plugins
            .filter { it.providesCatalogs && it.catalogs.isEmpty() }
            .forEach { discoverCatalogsIfNeeded(it.metadata.id.value) }
    }

    private fun discoverCatalogsIfNeeded(pluginId: String, force: Boolean = false) {
        val item = _uiState.value.plugins.firstOrNull { it.metadata.id.value == pluginId } ?: return
        if (!item.providesCatalogs || (!force && item.catalogs.isNotEmpty())) return
        if (catalogJobs[pluginId]?.isActive == true) return
        catalogJobs[pluginId] = scope.launch {
            providerResult(pluginId) { catalogs() }
                .onSuccess { catalogs ->
                    log.i { "CloudStream catalogs discovered id=$pluginId count=${catalogs.size}" }
                    _uiState.update { current ->
                        current.copy(
                            plugins = current.plugins.map { plugin ->
                                if (plugin.metadata.id.value == pluginId) plugin.copy(catalogs = catalogs) else plugin
                            },
                            registryRevision = current.registryRevision + 1,
                        )
                    }
                    persist()
                }
                .onFailure { error -> log.w(error) { "CloudStream catalog discovery failed id=$pluginId" } }
        }
    }

    private fun cancelBackgroundJobs() {
        refreshJobs.values.forEach { it.cancel() }
        refreshJobs.clear()
        catalogJobs.values.forEach { it.cancel() }
        catalogJobs.clear()
        syncJob?.cancel()
        syncJob = null
        pendingSyncState = null
    }

    private suspend fun installOrUpdate(pluginId: String): CloudStreamInstallResult {
        initialize()
        if (!_uiState.value.securityWarningAccepted) {
            return CloudStreamInstallResult.Error("Accept the third-party plugin security warning before installation")
        }
        val item = _uiState.value.plugins.firstOrNull { it.metadata.id.value == pluginId }
            ?: return CloudStreamInstallResult.Error("CloudStream plugin was not found")
        if (!item.metadata.status.canInstall) {
            return CloudStreamInstallResult.Error("This CloudStream plugin is marked as down")
        }
        markInstalling(pluginId, true, null)
        return runCatching {
            val bytes = httpGetBytesWithHeaders(
                item.metadata.packageUrl,
                headers = mapOf("Accept" to "application/zip, application/octet-stream"),
            )
            require(bytes.isNotEmpty()) { "Downloaded .cs3 package is empty" }
            item.metadata.fileSize?.let { expectedSize ->
                if (bytes.size.toLong() != expectedSize) {
                    log.w {
                        "CloudStream package metadata size mismatch id=$pluginId expected=$expectedSize actual=${bytes.size}"
                    }
                }
            }
            CloudStreamPackageInspector.inspect(bytes)
            val expectedHash = item.metadata.fileHash
            val verified = expectedHash?.matches(bytes) ?: true
            if (expectedHash != null) require(verified) { ".cs3 SHA-256 hash mismatch" }
            CloudStreamPlatformRuntime.unload(pluginId)
            CloudStreamPlatformStorage.savePackageAtomically(item.metadata.id.storageKey, bytes)
            val installed = item.copy(
                installedVersion = item.metadata.version,
                installedAtEpochMs = currentEpochMillis(),
                enabled = item.enabled && verified && item.compatibility.isRunnable,
                verified = verified,
                isInstalling = false,
                errorMessage = null,
            )
            _uiState.update { current ->
                current.copy(
                    plugins = current.plugins.map { if (it.metadata.id.value == pluginId) installed else it },
                    registryRevision = current.registryRevision + 1,
                )
            }
            persist()
            log.i { "CloudStream plugin installed id=$pluginId version=${item.metadata.version}" }
            discoverCatalogsIfNeeded(pluginId, force = true)
            installed
        }.fold(
            onSuccess = { CloudStreamInstallResult.Success(it) },
            onFailure = { error ->
                log.w(error) { "CloudStream plugin installation failed id=$pluginId" }
                markInstalling(pluginId, false, error.message ?: "Plugin installation failed")
                CloudStreamInstallResult.Error(error.message ?: "Plugin installation failed")
            },
        )
    }

    private suspend fun fetchRepository(
        manifestUrl: String,
    ): Pair<CloudStreamRepositoryManifest, List<CloudStreamPluginMetadata>> {
        var lastManifestError: Throwable? = null
        val manifest = cloudStreamManifestCandidates(manifestUrl).firstNotNullOfOrNull { candidate ->
            runCatching {
                CloudStreamRepositoryParser.parseRepository(candidate, httpGetText(candidate))
            }.onFailure { error ->
                lastManifestError = error
                log.w(error) { "CloudStream repository manifest failed url=$candidate" }
            }.getOrNull()
        } ?: throw lastManifestError ?: IllegalStateException("CloudStream repository manifest could not be loaded")
        var loadedListCount = 0
        val lists = manifest.pluginListUrls.mapNotNull { pluginListUrl ->
            runCatching {
                val listPayload = httpGetText(pluginListUrl)
                CloudStreamRepositoryParser.parsePluginList(manifest.sourceUrl, pluginListUrl, listPayload)
            }.onSuccess {
                loadedListCount += 1
            }.onFailure { error ->
                log.w(error) { "CloudStream plugin list failed url=$pluginListUrl" }
            }.getOrNull()
        }
        require(loadedListCount > 0) { "CloudStream repository plugin lists could not be loaded" }
        return manifest to CloudStreamRepositoryParser.mergePluginLists(lists)
    }

    private fun cloudStreamManifestCandidates(manifestUrl: String): List<String> {
        val normalized = normalizeCloudStreamRepositoryUrl(manifestUrl)
        val alternate = when {
            "/master/repo.json" in normalized -> normalized.replace("/master/repo.json", "/main/repo.json")
            "/main/repo.json" in normalized -> normalized.replace("/main/repo.json", "/master/repo.json")
            else -> null
        }
        return listOfNotNull(normalized, alternate).distinct()
    }

    private fun applyRepositoryRefresh(
        repository: CloudStreamRepositoryManifest,
        plugins: List<CloudStreamPluginMetadata>,
    ) {
        _uiState.update { current ->
            val previousById = current.plugins.associateBy { it.metadata.id.value }
            val refreshed = plugins.map { metadata ->
                previousById[metadata.id.value]?.copy(
                    metadata = metadata,
                    compatibility = resolveCompatibility(metadata),
                    enabled = previousById[metadata.id.value]?.enabled == true && metadata.status.canInstall,
                    errorMessage = null,
                ) ?: newPluginItem(metadata)
            }
            current.copy(
                repositories = current.repositories.map { item ->
                    if (item.manifest.sourceUrl == repository.sourceUrl) {
                        CloudStreamRepositoryItem(repository)
                    } else item
                },
                plugins = (current.plugins.filterNot { it.metadata.repositoryManifestUrl == repository.sourceUrl } + refreshed)
                    .sortedBy { it.metadata.name.lowercase() },
                registryRevision = current.registryRevision + 1,
            )
        }
    }

    private fun newPluginItem(metadata: CloudStreamPluginMetadata): CloudStreamPluginItem =
        CloudStreamPluginItem(
            metadata = metadata,
            compatibility = resolveCompatibility(metadata),
        )

    private fun markInstalling(pluginId: String, installing: Boolean, error: String?) {
        _uiState.update { current ->
            current.copy(
                plugins = current.plugins.map { item ->
                    if (item.metadata.id.value == pluginId) {
                        item.copy(isInstalling = installing, errorMessage = error)
                    } else item
                },
            )
        }
    }

    private fun restoreState(profileId: Int): CloudStreamUiState {
        val payload = CloudStreamPlatformStorage.loadState(profileId) ?: return CloudStreamUiState()
        val stored = runCatching { json.decodeFromString<StoredCloudStreamState>(payload) }
            .onFailure { log.w(it) { "Could not restore CloudStream state" } }
            .getOrNull() ?: return CloudStreamUiState()
        val repositories = stored.repositories.map { repository ->
            val manifestUrl = runCatching { normalizeCloudStreamRepositoryUrl(repository.manifestUrl) }
                .getOrDefault(repository.manifestUrl)
            CloudStreamRepositoryItem(
                CloudStreamRepositoryManifest(
                    sourceUrl = manifestUrl,
                    name = repository.name,
                    description = repository.description,
                    iconUrl = repository.iconUrl,
                    manifestVersion = repository.manifestVersion,
                    pluginListUrls = repository.pluginListUrls,
                ),
            )
        }
        val plugins = stored.plugins.map { plugin ->
            val metadata = plugin.toMetadata()
            val legacyStorageKey = CloudStreamPluginId(plugin.repositoryManifestUrl, plugin.internalName).storageKey
            val storageKey = metadata.id.storageKey
            val packageExists = plugin.installedVersion != null && (
                CloudStreamPlatformStorage.packageExists(storageKey) ||
                    (legacyStorageKey != storageKey &&
                        CloudStreamPlatformStorage.migratePackage(legacyStorageKey, storageKey))
                )
            val compatibility = resolveCompatibility(metadata)
            CloudStreamPluginItem(
                metadata = metadata,
                compatibility = compatibility,
                installedVersion = plugin.installedVersion.takeIf { packageExists },
                installedAtEpochMs = plugin.installedAtEpochMs.takeIf { packageExists } ?: 0L,
                enabled = plugin.enabled && packageExists && plugin.verified && compatibility.isRunnable && metadata.status.canInstall,
                verified = plugin.verified && packageExists,
                importCatalogs = plugin.importCatalogs,
                catalogs = plugin.catalogs.takeIf { packageExists }.orEmpty(),
                autoInstallPending = plugin.autoInstallPending,
                autoInstallEnable = plugin.autoInstallEnable,
            )
        }
        syncPassthrough = stored.syncPassthrough
        return CloudStreamUiState(
            repositories = repositories,
            plugins = plugins,
            registryRevision = 1,
            securityWarningAccepted = stored.securityWarningAccepted,
            groupStreamsByRepository = stored.groupStreamsByRepository,
            useFullDetails = stored.useFullDetails,
        )
    }

    private fun persist() {
        val current = _uiState.value
        val stored = StoredCloudStreamState(
            repositories = current.repositories.map { item ->
                StoredCloudStreamRepository(
                    manifestUrl = item.manifest.sourceUrl,
                    name = item.manifest.name,
                    description = item.manifest.description,
                    iconUrl = item.manifest.iconUrl,
                    manifestVersion = item.manifest.manifestVersion,
                    pluginListUrls = item.manifest.pluginListUrls,
                )
            },
            plugins = current.plugins.map { item -> item.toStored() },
            securityWarningAccepted = current.securityWarningAccepted,
            groupStreamsByRepository = current.groupStreamsByRepository,
            useFullDetails = current.useFullDetails,
            syncPassthrough = syncPassthrough,
        )
        CloudStreamPlatformStorage.saveState(currentProfileId, json.encodeToString(stored))
    }

    private fun CloudStreamPluginItem.toStored(): StoredCloudStreamPlugin = StoredCloudStreamPlugin(
        repositoryManifestUrl = metadata.repositoryManifestUrl,
        packageUrl = metadata.packageUrl,
        status = metadata.status.name,
        availableVersion = metadata.version,
        installedVersion = installedVersion,
        name = metadata.name,
        internalName = metadata.internalName,
        authors = metadata.authors,
        description = metadata.description,
        fileSize = metadata.fileSize,
        projectUrl = metadata.projectUrl,
        language = metadata.language,
        rawTvTypes = metadata.rawTvTypes,
        iconUrl = metadata.iconUrl,
        apiVersion = metadata.apiVersion,
        fileHash = metadata.fileHash?.wireValue,
        enabled = enabled,
        verified = verified,
        installedAtEpochMs = installedAtEpochMs.takeIf { installedVersion != null } ?: 0L,
        importCatalogs = importCatalogs,
        catalogs = catalogs,
        autoInstallPending = autoInstallPending,
        autoInstallEnable = autoInstallEnable,
    )

    private fun StoredCloudStreamPlugin.toMetadata(): CloudStreamPluginMetadata {
        val normalizedRepositoryUrl = runCatching { normalizeCloudStreamRepositoryUrl(repositoryManifestUrl) }
            .getOrDefault(repositoryManifestUrl)
        return CloudStreamPluginMetadata(
            id = CloudStreamPluginId(normalizedRepositoryUrl, internalName),
            repositoryManifestUrl = normalizedRepositoryUrl,
            packageUrl = packageUrl,
            status = runCatching { CloudStreamPluginStatus.valueOf(status) }
                .getOrDefault(CloudStreamPluginStatus.Unknown),
            version = availableVersion,
            name = name,
            internalName = internalName,
            authors = authors,
            description = description,
            fileSize = fileSize,
            projectUrl = projectUrl,
            language = language,
            tvTypes = rawTvTypes.map(CloudStreamTvType::fromWireValue).distinct(),
            rawTvTypes = rawTvTypes,
            iconUrl = iconUrl,
            apiVersion = apiVersion,
            fileHash = CloudStreamFileHash.parse(fileHash),
        )
    }

    private fun runnableProviderIds(): List<String> =
        _uiState.value.plugins.filter(CloudStreamPluginItem::isRunnable).map { it.metadata.id.value }

    private fun resolveCompatibility(metadata: CloudStreamPluginMetadata): CloudStreamCompatibility =
        CloudStreamCompatibilityResolver.resolve(
            metadata = metadata,
            supportsAndroidDex = CloudStreamPlatformRuntime.supportsAndroidDex,
        )

    private suspend fun <T> providerResult(
        providerId: String,
        block: suspend CloudStreamProvider.() -> T,
    ): Result<T> {
        initialize()
        if (providerId !in runnableProviderIds()) {
            return Result.failure(IllegalStateException("CloudStream provider is disabled or incompatible"))
        }
        return runCatching {
            val plugin = _uiState.value.plugins.firstOrNull { it.metadata.id.value == providerId }
                ?: error("CloudStream plugin was not found")
            val provider = CloudStreamPlatformRuntime.provider(plugin)
                ?: error("CloudStream provider adapter was not found")
            provider.block()
        }
            .onFailure { log.w(it) { "CloudStream provider request failed id=$providerId" } }
    }

    private suspend fun resolveCloudStreamRepositoryInput(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val expanded = when {
            trimmed.startsWith("!", ignoreCase = false) ->
                resolveCloudStreamShortLink("https://py.md/${trimmed.removePrefix("!")}")
            trimmed.matches(Regex("^[a-zA-Z0-9!_-]+$")) ->
                resolveCloudStreamShortLink("https://cutt.ly/$trimmed")
            else -> trimmed
        }
        return normalizeCloudStreamRepositoryUrl(expanded)
    }

    private suspend fun resolveCloudStreamShortLink(url: String): String {
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = emptyMap(),
            body = "",
            followRedirects = false,
        )
        val location = response.headers["location"]?.substringBefore(',')?.trim()
        require(!location.isNullOrBlank()) { "CloudStream short repository link did not redirect" }
        require(!location.startsWith("https://py.md/404") && !location.startsWith("https://cutt.ly/404")) {
            "CloudStream short repository link was not found"
        }
        return location
    }
}
