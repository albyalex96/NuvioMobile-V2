package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.tv.TvAppRuntime
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.floatingNavigationGlowSupported
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationBarSettingsSheet(
    isTablet: Boolean,
    selectedStyle: NavBarStyle,
    onStyleSelected: (NavBarStyle) -> Unit,
    glowEnabled: Boolean,
    onGlowChanged: (Boolean) -> Unit,
    showLiveTv: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { dismissNuvioBottomSheet(sheetState, onDismiss) }
    }
    val isTv = TvAppRuntime.isTvApp
    val topBarEnabled by ThemeSettingsRepository.topNavBarEnabled.collectAsStateWithLifecycle()
    val topBarStyle by ThemeSettingsRepository.topNavBarStyle.collectAsStateWithLifecycle()
    NuvioModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = stringResource(Res.string.settings_appearance_nav_bar_style),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                if (!isTv) {
                    NavigationBarPreview(selectedStyle, isTablet, glowEnabled, showLiveTv)
                }
            }
            if (isTv) {
                tvNavigationBarOptions(
                    isTablet = isTablet,
                    topBarEnabled = topBarEnabled,
                    topBarStyle = topBarStyle,
                    glowEnabled = glowEnabled,
                    onGlowChanged = onGlowChanged,
                    showLiveTv = showLiveTv,
                )
            } else {
                if (!isTablet) {
                    NavBarStyle.entries.forEach { style ->
                        item(key = style.key) {
                            NavBarOptionRow(
                                title = stringResource(style.labelRes),
                                selected = style == selectedStyle,
                                onClick = { onStyleSelected(style) },
                            )
                        }
                    }
                }
                item {
                    AnimatedVisibility(floatingNavigationGlowSupported && selectedStyle != NavBarStyle.CLASSIC) {
                        Column {
                            NuvioBottomSheetDivider()
                            SettingsSwitchRow(
                                title = stringResource(Res.string.settings_nav_bar_glow),
                                description = stringResource(Res.string.settings_nav_bar_glow_description),
                                checked = glowEnabled,
                                isTablet = isTablet,
                                onCheckedChange = onGlowChanged,
                            )
                        }
                    }
                }
            }
            item {
                NuvioPrimaryButton(
                    text = stringResource(Res.string.action_done),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    onClick = dismiss,
                )
            }
        }
    }
}

/**
 * TV options: the glass top bar replaces the sidebar when enabled; its layout (always expanded or
 * always compact, mutually exclusive) and the glass glow only apply to that bar.
 */
private fun LazyListScope.tvNavigationBarOptions(
    isTablet: Boolean,
    topBarEnabled: Boolean,
    topBarStyle: TopNavBarStyle,
    glowEnabled: Boolean,
    onGlowChanged: (Boolean) -> Unit,
    showLiveTv: Boolean,
) {
    item {
        AnimatedVisibility(topBarEnabled) {
            NavigationBarPreview(
                style = NavBarStyle.ADAPTIVE,
                isTablet = isTablet,
                glowEnabled = glowEnabled,
                showLiveTv = showLiveTv,
                topBarStyle = topBarStyle,
            )
        }
    }
    item {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_top_nav_bar),
            description = stringResource(Res.string.settings_top_nav_bar_description),
            checked = topBarEnabled,
            isTablet = isTablet,
            onCheckedChange = ThemeSettingsRepository::setTopNavBarEnabled,
        )
    }
    item {
        AnimatedVisibility(topBarEnabled) {
            Column {
                NuvioBottomSheetDivider()
                Text(
                    text = stringResource(Res.string.settings_top_nav_bar_layout),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.nuvio.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                TopNavBarStyle.entries.forEach { style ->
                    NavBarOptionRow(
                        title = stringResource(style.labelRes),
                        selected = style == topBarStyle,
                        onClick = { ThemeSettingsRepository.setTopNavBarStyle(style) },
                    )
                }
                NuvioBottomSheetDivider()
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_nav_bar_glow),
                    description = stringResource(Res.string.settings_nav_bar_glow_description),
                    checked = glowEnabled,
                    isTablet = isTablet,
                    onCheckedChange = onGlowChanged,
                )
                Text(
                    text = stringResource(Res.string.settings_top_nav_bar_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.nuvio.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NavBarOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    NuvioBottomSheetActionRow(
        title = title,
        onClick = onClick,
        selected = selected,
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}
