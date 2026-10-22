package com.nuvio.app.features.cloudstream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.settings.IntegrationLogo
import com.nuvio.app.features.settings.integrationLogoPainter
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("CloudStreamSettings")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CloudStreamSettingsPageContent(
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        CloudStreamRepository.initialize()
    }
    val state by CloudStreamRepository.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    var editingRepositoryUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var isAddingRepository by remember { mutableStateOf(false) }
    var repositoryMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var installedOnly by rememberSaveable { mutableStateOf(false) }
    var pluginMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isBulkInstalling by remember { mutableStateOf(false) }

    val repositoryNameByUrl = remember(state.repositories) {
        state.repositories.associate { it.manifest.sourceUrl to it.manifest.name }
    }
    val sortedPlugins = remember(state.plugins, repositoryNameByUrl) {
        state.plugins.sortedWith(
            compareByDescending<CloudStreamPluginItem> { it.isInstalled }
                .thenBy { repositoryNameByUrl[it.metadata.repositoryManifestUrl]?.lowercase().orEmpty() }
                .thenBy { it.metadata.name.lowercase() },
        )
    }
    val languages = remember(state.plugins) {
        state.plugins.mapNotNull { it.metadata.language }.distinct().sorted()
    }
    val types = remember(state.plugins) {
        state.plugins.flatMap { it.metadata.rawTvTypes }.distinct().sorted()
    }
    val visiblePlugins = remember(sortedPlugins, query, selectedLanguage, selectedType, installedOnly) {
        val normalizedQuery = query.trim().lowercase()
        sortedPlugins.filter { item ->
            (normalizedQuery.isBlank() || listOf(
                item.metadata.name,
                item.metadata.internalName,
                item.metadata.description.orEmpty(),
                item.metadata.authors.joinToString(" "),
            ).any { it.lowercase().contains(normalizedQuery) }) &&
                (selectedLanguage == null || item.metadata.language == selectedLanguage) &&
                (selectedType == null || selectedType in item.metadata.rawTvTypes) &&
                (!installedOnly || item.isInstalled)
        }
    }
    val bulkActionPlugins = remember(visiblePlugins) {
        visiblePlugins.filter { item ->
            !item.isInstalling &&
                item.metadata.status.canInstall &&
                item.compatibility.isRunnable &&
                (!item.isRunnable || item.hasUpdate || !item.verified)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NuvioSectionLabel(stringResource(Res.string.cloudstream_section_overview))
        NuvioSurfaceCard {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CloudStreamBadge()
                NuvioInfoBadge(text = stringResource(Res.string.cloudstream_badge_repos, state.repositories.size))
                NuvioInfoBadge(text = stringResource(Res.string.cloudstream_badge_installed, state.plugins.count { it.isInstalled }))
                NuvioInfoBadge(text = stringResource(Res.string.cloudstream_badge_enabled, state.plugins.count { it.isRunnable }))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.cloudstream_overview_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            CloudStreamToggleRow(
                title = stringResource(Res.string.cloudstream_group_by_repo_title),
                description = stringResource(Res.string.cloudstream_group_by_repo_desc),
                checked = state.groupStreamsByRepository,
                onCheckedChange = CloudStreamRepository::setGroupStreamsByRepository,
            )
            Spacer(modifier = Modifier.height(8.dp))
            CloudStreamToggleRow(
                title = stringResource(Res.string.cloudstream_full_details_title),
                description = stringResource(Res.string.cloudstream_full_details_desc),
                checked = state.useFullDetails,
                onCheckedChange = CloudStreamRepository::setUseFullDetails,
            )
        }

        if (!state.securityWarningAccepted) {
            NuvioSurfaceCard {
                Text(
                    text = stringResource(Res.string.cloudstream_security_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.cloudstream_security_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                NuvioPrimaryButton(
                    text = stringResource(Res.string.cloudstream_security_accept),
                    onClick = CloudStreamRepository::acceptSecurityWarning,
                )
            }
        }

        NuvioSectionLabel(
            stringResource(
                if (editingRepositoryUrl == null) Res.string.cloudstream_section_add_repo else Res.string.cloudstream_section_edit_repo,
            ),
        )
        NuvioSurfaceCard {
            NuvioInputField(
                value = repositoryUrl,
                onValueChange = {
                    repositoryUrl = it
                    repositoryMessage = null
                },
                placeholder = stringResource(Res.string.cloudstream_repo_placeholder),
            )
            Spacer(modifier = Modifier.height(16.dp))
            NuvioPrimaryButton(
                text = stringResource(
                    when {
                        isAddingRepository -> Res.string.cloudstream_repo_loading
                        editingRepositoryUrl != null -> Res.string.cloudstream_repo_apply
                        else -> Res.string.cloudstream_repo_add
                    },
                ),
                enabled = repositoryUrl.isNotBlank() && !isAddingRepository,
                onClick = {
                    val requested = repositoryUrl.trim()
                    val previous = editingRepositoryUrl
                    isAddingRepository = true
                    repositoryMessage = null
                    scope.launch {
                        if (previous != null && requested == previous) {
                            CloudStreamRepository.refreshRepository(previous)
                            repositoryMessage = getString(Res.string.cloudstream_repo_refreshing)
                        } else {
                            when (val result = CloudStreamRepository.addRepository(requested)) {
                                is AddCloudStreamRepositoryResult.Success -> {
                                    previous?.let(CloudStreamRepository::removeRepository)
                                    repositoryUrl = ""
                                    editingRepositoryUrl = null
                                    repositoryMessage = getString(Res.string.cloudstream_repo_added, result.repository.name)
                                }
                                is AddCloudStreamRepositoryResult.Error -> {
                                    log.w { "Repository not added: ${result.message}" }
                                    repositoryMessage = result.message
                                }
                            }
                        }
                        isAddingRepository = false
                    }
                },
            )
            repositoryMessage?.let { text ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        NuvioSectionLabel(stringResource(Res.string.cloudstream_section_repos))
        if (state.repositories.isEmpty()) {
            NuvioSurfaceCard {
                Text(
                    text = stringResource(Res.string.cloudstream_empty_repos_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.cloudstream_empty_repos_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            state.repositories.sortedBy { it.manifest.name.lowercase() }.forEach { repository ->
                CloudStreamRepositoryCard(
                    repository = repository,
                    pluginCount = state.plugins.count { it.metadata.repositoryManifestUrl == repository.manifest.sourceUrl },
                    onEdit = {
                        repositoryUrl = repository.manifest.sourceUrl
                        editingRepositoryUrl = repository.manifest.sourceUrl
                    },
                )
            }
        }

        if (state.plugins.isNotEmpty()) {
            NuvioSectionLabel(stringResource(Res.string.cloudstream_section_plugins))
            NuvioSurfaceCard {
                NuvioInputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.cloudstream_search_placeholder),
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CloudStreamFilterChip(stringResource(Res.string.cloudstream_filter_installed), installedOnly) {
                        installedOnly = !installedOnly
                    }
                    CloudStreamFilterChip(stringResource(Res.string.cloudstream_filter_all_languages), selectedLanguage == null) {
                        selectedLanguage = null
                    }
                    languages.forEach { language ->
                        CloudStreamFilterChip(language.uppercase(), selectedLanguage == language) {
                            selectedLanguage = language.takeUnless { selectedLanguage == language }
                        }
                    }
                }
                if (types.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CloudStreamFilterChip(stringResource(Res.string.cloudstream_filter_all_types), selectedType == null) {
                            selectedType = null
                        }
                        types.forEach { type ->
                            CloudStreamFilterChip(type, selectedType == type) {
                                selectedType = type.takeUnless { selectedType == type }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = state.securityWarningAccepted && !isBulkInstalling && bulkActionPlugins.isNotEmpty(),
                    onClick = {
                        val targetIds = bulkActionPlugins.map { it.metadata.id.value }
                        isBulkInstalling = true
                        pluginMessage = null
                        scope.launch {
                            pluginMessage = runCatching {
                                CloudStreamRepository.installAndEnablePlugins(targetIds).toUserMessage()
                            }.getOrElse { error ->
                                log.e(error) { "Bulk install failed" }
                                error.message ?: getString(Res.string.cloudstream_bulk_failed)
                            }
                            isBulkInstalling = false
                        }
                    },
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isBulkInstalling -> stringResource(Res.string.cloudstream_bulk_installing)
                            bulkActionPlugins.isEmpty() -> stringResource(Res.string.cloudstream_all_installed_active)
                            else -> stringResource(Res.string.cloudstream_install_all, bulkActionPlugins.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                pluginMessage?.let { text ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (visiblePlugins.isEmpty()) {
                NuvioSurfaceCard {
                    Text(
                        text = stringResource(Res.string.cloudstream_empty_plugins),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            visiblePlugins.forEach { plugin ->
                CloudStreamPluginCard(
                    plugin = plugin,
                    securityWarningAccepted = state.securityWarningAccepted,
                    onMessage = { pluginMessage = it },
                )
            }
        }
    }
}

/** Shown where the CloudStream runtime cannot run: the synced configuration is kept untouched. */
@Composable
internal fun CloudStreamUnsupportedContent(modifier: Modifier = Modifier) {
    NuvioSurfaceCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = integrationLogoPainter(IntegrationLogo.Cloudstream),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(Res.string.cloudstream_unsupported_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.cloudstream_unsupported_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudStreamRepositoryCard(
    repository: CloudStreamRepositoryItem,
    pluginCount: Int,
    onEdit: () -> Unit,
) {
    NuvioSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repository.manifest.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = repository.manifest.sourceUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NuvioIconActionButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.cloudstream_cd_edit_repo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onEdit,
                )
                NuvioIconActionButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(Res.string.cloudstream_cd_refresh_repo),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { CloudStreamRepository.refreshRepository(repository.manifest.sourceUrl) },
                )
                NuvioIconActionButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = stringResource(Res.string.cloudstream_cd_remove_repo),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { CloudStreamRepository.removeRepository(repository.manifest.sourceUrl) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CloudStreamBadge()
            NuvioInfoBadge(
                text = stringResource(Res.string.cloudstream_badge_plugins, pluginCount),
                icon = Icons.Rounded.Extension,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
            if (repository.isRefreshing) {
                NuvioInfoBadge(text = stringResource(Res.string.cloudstream_repo_loading))
            }
        }
        repository.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudStreamPluginCard(
    plugin: CloudStreamPluginItem,
    securityWarningAccepted: Boolean,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    NuvioSurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = plugin.metadata.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.metadata.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plugin.metadata.authors.joinToString().ifBlank { stringResource(Res.string.cloudstream_unknown_author) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = plugin.enabled,
                enabled = plugin.isInstalled && plugin.verified && plugin.compatibility.isRunnable,
                onCheckedChange = { CloudStreamRepository.setPluginEnabled(plugin.metadata.id.value, it) },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = plugin.metadata.description ?: stringResource(Res.string.cloudstream_no_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NuvioInfoBadge(text = stringResource(Res.string.cloudstream_version_current, plugin.metadata.version))
            plugin.installedVersion?.let { NuvioInfoBadge(text = stringResource(Res.string.cloudstream_version_installed, it)) }
            plugin.metadata.language?.let { NuvioInfoBadge(text = it.uppercase()) }
            plugin.metadata.rawTvTypes.forEach { NuvioInfoBadge(text = it) }
            NuvioInfoBadge(
                text = stringResource(
                    when {
                        plugin.metadata.fileHash != null && plugin.verified -> Res.string.cloudstream_sha_verified
                        plugin.metadata.fileHash != null -> Res.string.cloudstream_hash_unverified
                        plugin.verified -> Res.string.cloudstream_package_checked
                        else -> Res.string.cloudstream_package_unchecked
                    },
                ),
            )
            NuvioInfoBadge(
                text = stringResource(
                    when (plugin.compatibility.platformSupport) {
                        CloudStreamPlatformSupport.AndroidAndIos -> Res.string.cloudstream_compat_android_ios
                        CloudStreamPlatformSupport.AndroidOnly -> Res.string.cloudstream_compat_android
                        CloudStreamPlatformSupport.Unsupported -> Res.string.cloudstream_compat_unsupported
                    },
                ),
            )
            if (plugin.autoInstallPending) {
                NuvioInfoBadge(text = stringResource(Res.string.cloudstream_auto_install_pending))
            }
        }
        plugin.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error.localizedCloudStreamFailure(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (plugin.isInstalled) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            CloudStreamToggleRow(
                title = stringResource(Res.string.cloudstream_import_catalogs_title),
                description = stringResource(Res.string.cloudstream_import_catalogs_desc),
                checked = plugin.importCatalogs,
                enabled = plugin.isRunnable,
                onCheckedChange = { CloudStreamRepository.setPluginImportCatalogs(plugin.metadata.id.value, it) },
            )
            if (plugin.providesCatalogs) {
                Spacer(modifier = Modifier.height(8.dp))
                NuvioInfoBadge(
                    text = if (plugin.catalogs.isEmpty()) {
                        stringResource(Res.string.cloudstream_catalogs_loading)
                    } else {
                        stringResource(Res.string.cloudstream_catalogs_count, plugin.catalogs.size)
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = securityWarningAccepted && !plugin.isInstalling,
                onClick = {
                    scope.launch {
                        val result = if (plugin.hasUpdate) {
                            CloudStreamRepository.updatePlugin(plugin.metadata.id.value)
                        } else {
                            CloudStreamRepository.installPlugin(plugin.metadata.id.value)
                        }
                        onMessage(
                            when (result) {
                                is CloudStreamInstallResult.Success ->
                                    getString(Res.string.cloudstream_plugin_installed, result.plugin.metadata.name)
                                is CloudStreamInstallResult.Error -> result.message.localizedFailureMessage()
                            },
                        )
                    }
                },
            ) {
                Text(
                    stringResource(
                        when {
                            plugin.isInstalling -> Res.string.cloudstream_downloading
                            plugin.hasUpdate -> Res.string.cloudstream_update
                            plugin.isInstalled -> Res.string.cloudstream_reinstall
                            else -> Res.string.cloudstream_install
                        },
                    ),
                )
            }
            if (plugin.isInstalled) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { CloudStreamRepository.removePlugin(plugin.metadata.id.value) },
                ) {
                    Text(stringResource(Res.string.cloudstream_remove))
                }
            }
        }
    }
}

@Composable
private fun CloudStreamToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CloudStreamFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private suspend fun CloudStreamBulkInstallResult.toUserMessage(): String {
    if (requestedCount == 0) return getString(Res.string.cloudstream_no_plugins_to_install)
    val enabledText = getString(Res.string.cloudstream_bulk_enabled, enabledCount)
    val installText = if (installedCount > 0) getString(Res.string.cloudstream_bulk_installed_updated, installedCount) else ""
    val skippedText = if (skippedCount > 0) getString(Res.string.cloudstream_bulk_pending, skippedCount) else ""
    val first = failures.firstOrNull()
    val failureText = first?.let {
        getString(Res.string.cloudstream_bulk_skipped, failures.size, it.pluginName, it.message.localizedFailureMessage())
    }.orEmpty()
    return when {
        enabledCount > 0 -> "$enabledText$installText$skippedText$failureText."
        first != null -> getString(Res.string.cloudstream_bulk_none_enabled, first.pluginName, first.message.localizedFailureMessage())
        skippedCount > 0 -> getString(Res.string.cloudstream_bulk_only_pending, skippedCount)
        else -> getString(Res.string.cloudstream_no_plugins_to_install)
    }
}

/** Maps the repository's English failure messages to localized text. */
private suspend fun String.localizedFailureMessage(): String =
    failureResource()?.let { getString(it) } ?: this

@Composable
private fun String.localizedCloudStreamFailure(): String =
    failureResource()?.let { stringResource(it) } ?: this

private fun String.failureResource() = when (this) {
    "Accept the third-party plugin security warning before installation" -> Res.string.cloudstream_failure_accept_warning
    "CloudStream plugin was not found" -> Res.string.cloudstream_failure_not_found
    "This CloudStream plugin is marked as down" -> Res.string.cloudstream_failure_down
    "The package was installed, but could not be enabled because verification or compatibility checks failed." ->
        Res.string.cloudstream_failure_enable
    else -> null
}
