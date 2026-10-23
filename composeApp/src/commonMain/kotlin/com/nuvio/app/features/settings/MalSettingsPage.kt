package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.mal.MalAuthRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_mal_sync_login_description
import nuvio.composeapp.generated.resources.settings_mal_sync_login_title
import nuvio.composeapp.generated.resources.settings_tracking_mal_section
import nuvio.composeapp.generated.resources.settings_tracking_services
import org.jetbrains.compose.resources.stringResource

/** The MyAnimeList integration page: account card, login sync and the anime update selector. */
internal fun LazyListScope.myAnimeListSettingsContent(isTablet: Boolean) {
    item(key = "mal_account") {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_services),
            isTablet = isTablet,
        ) {
            MalProviderCard(Modifier.fillMaxWidth())
        }
    }

    item(key = "mal_login_sync") {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_mal_section),
            isTablet = isTablet,
        ) {
            MalLoginSyncSection(isTablet = isTablet)
        }
    }

    item(key = "mal_anime_updates") {
        AnimeUpdatesSection(isTablet = isTablet)
    }
}

@Composable
private fun MalLoginSyncSection(isTablet: Boolean) {
    val malUiState by remember {
        MalAuthRepository.ensureLoaded()
        MalAuthRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsGroup(isTablet = isTablet) {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_mal_sync_login_title),
            description = stringResource(Res.string.settings_mal_sync_login_description),
            checked = malUiState.syncLoginAcrossDevices,
            isTablet = isTablet,
            onCheckedChange = MalAuthRepository::setSyncLoginAcrossDevices,
        )
    }
}
