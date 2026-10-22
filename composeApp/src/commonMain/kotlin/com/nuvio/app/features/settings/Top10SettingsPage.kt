package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.HomeCatalogDefinition
import com.nuvio.app.features.home.Top10Kind
import com.nuvio.app.features.home.Top10Repository
import com.nuvio.app.features.home.buildHomeCatalogDefinitions
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.top10SettingsContent(
    isTablet: Boolean,
) {
    item(key = "top10_settings") {
        Top10SettingsSection(isTablet = isTablet)
    }
}

@Composable
private fun Top10SettingsSection(
    isTablet: Boolean,
) {
    val settings by remember {
        Top10Repository.ensureLoaded()
        Top10Repository.uiState
    }.collectAsStateWithLifecycle()
    val addonsUiState by remember {
        AddonRepository.initialize()
        AddonRepository.uiState
    }.collectAsStateWithLifecycle()
    val catalogs = remember(addonsUiState.addons) {
        buildHomeCatalogDefinitions(addonsUiState.addons)
    }
    var pickerKind by remember { mutableStateOf<Top10Kind?>(null) }

    SettingsSection(
        title = stringResource(Res.string.top10_section_general),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.top10_enabled),
                description = stringResource(Res.string.top10_enabled_description),
                checked = settings.enabled,
                isTablet = isTablet,
                onCheckedChange = Top10Repository::setEnabled,
            )
        }
    }

    if (settings.enabled) {
        SettingsSection(
            title = stringResource(Res.string.top10_section_catalogs),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                Top10Kind.entries.forEachIndexed { index, kind ->
                    if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(kind.catalogTitleRes()),
                        description = catalogSummary(
                            catalogKey = settings.catalogKey(kind),
                            catalogs = catalogs,
                            fallback = stringResource(kind.catalogDescriptionRes()),
                        ),
                        icon = if (kind == Top10Kind.Movies) Icons.Rounded.Movie else Icons.Rounded.Tv,
                        isTablet = isTablet,
                        onClick = { pickerKind = kind },
                    )
                }
            }
        }
    }

    pickerKind?.let { kind ->
        Top10CatalogPickerDialog(
            kind = kind,
            selectedKey = settings.catalogKey(kind),
            catalogs = catalogs,
            onSelect = { catalogKey ->
                Top10Repository.setCatalog(kind, catalogKey)
                pickerKind = null
            },
            onDismiss = { pickerKind = null },
        )
    }
}

@Composable
private fun catalogSummary(
    catalogKey: String,
    catalogs: List<HomeCatalogDefinition>,
    fallback: String,
): String {
    if (catalogKey.isBlank()) return "${stringResource(Res.string.top10_catalog_not_set)} · $fallback"
    val catalog = catalogs.firstOrNull { it.key == catalogKey }
        ?: return stringResource(Res.string.top10_catalog_unavailable, catalogKey.substringAfterLast(':'))
    return "${catalog.defaultTitle} · ${catalog.addonName}"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Top10CatalogPickerDialog(
    kind: Top10Kind,
    selectedKey: String,
    catalogs: List<HomeCatalogDefinition>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Catalogs of the matching type first, then the others (some addons use custom types).
    val ordered = remember(catalogs, kind) {
        val wantedType = if (kind == Top10Kind.Movies) "movie" else "series"
        catalogs.sortedBy { !it.type.equals(wantedType, ignoreCase = true) }
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.top10_choose_catalog),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "none") {
                DialogOption(
                    text = stringResource(Res.string.top10_catalog_none),
                    description = stringResource(Res.string.top10_catalog_none_description),
                    selected = selectedKey.isBlank(),
                    onClick = { onSelect("") },
                )
            }
            if (ordered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(Res.string.top10_no_catalogs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                    )
                }
            }
            items(ordered, key = { it.key }) { catalog ->
                DialogOption(
                    text = catalog.defaultTitle,
                    description = catalog.addonName,
                    selected = catalog.key == selectedKey,
                    onClick = { onSelect(catalog.key) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
        }
    }
}

private fun Top10Kind.catalogTitleRes() = when (this) {
    Top10Kind.Movies -> Res.string.top10_movies_catalog
    Top10Kind.Series -> Res.string.top10_series_catalog
}

private fun Top10Kind.catalogDescriptionRes() = when (this) {
    Top10Kind.Movies -> Res.string.top10_movies_catalog_description
    Top10Kind.Series -> Res.string.top10_series_catalog_description
}
