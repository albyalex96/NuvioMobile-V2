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
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.ui.glass.LiquidGlassMaterial
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
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
                // Tablet: the bar sits at the top, so the bottom-only Classic style is not offered.
                NavBarStyle.entries
                    .filter { !isTablet || it != NavBarStyle.CLASSIC }
                    .forEach { style ->
                        item(key = style.key) {
                            NavBarOptionRow(
                                title = stringResource(style.labelRes),
                                selected = style == selectedStyle,
                                onClick = { onStyleSelected(style) },
                            )
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
                if (isLiquidGlassAvailable() && selectedStyle != NavBarStyle.CLASSIC) {
                    navBarGlassOptions(isTablet)
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

/** Liquid glass of the floating bar: material, numbers, flags and the unselected icon color. */
private fun LazyListScope.navBarGlassOptions(isTablet: Boolean) {
    item(key = "nav_bar_glass") {
        val effectsEnabled by ThemeSettingsRepository.liquidGlassEffectsEnabled.collectAsStateWithLifecycle()
        val style by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
        val effects by ThemeSettingsRepository.navBarGlassEffects.collectAsStateWithLifecycle()
        val iconColor by ThemeSettingsRepository.navBarIconColor.collectAsStateWithLifecycle()
        Column {
            NuvioBottomSheetDivider()
            Text(
                text = stringResource(Res.string.settings_nav_bar_glass),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.nuvio.colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_appearance_liquid_glass_effects),
                description = stringResource(Res.string.settings_appearance_liquid_glass_effects_description),
                checked = effectsEnabled,
                isTablet = isTablet,
                onCheckedChange = ThemeSettingsRepository::setLiquidGlassEffectsEnabled,
            )
            AnimatedVisibility(effectsEnabled) {
                Column {
                    SettingsChipRow(
                        title = stringResource(Res.string.settings_nav_bar_glass_material),
                        description = stringResource(Res.string.settings_nav_bar_glass_material_description),
                        isTablet = isTablet,
                    ) {
                        LiquidGlassMaterial.entries.forEach { material ->
                            FilterChip(
                                selected = style.material == material,
                                onClick = { ThemeSettingsRepository.setNavBarGlass(style.copy(material = material)) },
                                label = { Text(stringResource(material.labelRes())) },
                            )
                        }
                    }
                    AnimatedVisibility(style.usesBackdrop) {
                        Column {
                            if (style.usesBlur) {
                                SettingsSliderRow(
                                    title = stringResource(Res.string.settings_nav_bar_glass_blur),
                                    value = effects.blurRadiusDp.roundToInt(),
                                    valueText = { stringResource(Res.string.settings_nav_bar_glass_value_dp, it) },
                                    valueRange = 0..32,
                                    step = 1,
                                    isTablet = isTablet,
                                    onValueChange = {
                                        ThemeSettingsRepository.setNavBarGlassEffects(effects.copy(blurRadiusDp = it.toFloat()))
                                    },
                                )
                            }
                            if (style.usesLens) {
                                SettingsSliderRow(
                                    title = stringResource(Res.string.settings_nav_bar_glass_refraction),
                                    value = effects.refractionHeightDp.roundToInt(),
                                    valueText = { stringResource(Res.string.settings_nav_bar_glass_value_dp, it) },
                                    valueRange = 0..48,
                                    step = 1,
                                    isTablet = isTablet,
                                    onValueChange = {
                                        ThemeSettingsRepository.setNavBarGlassEffects(
                                            effects.copy(refractionHeightDp = it.toFloat(), refractionAmountDp = it.toFloat()),
                                        )
                                    },
                                )
                            }
                            SettingsSliderRow(
                                title = stringResource(Res.string.settings_nav_bar_glass_tint),
                                value = (effects.tintOpacity * 100).roundToInt(),
                                valueText = { stringResource(Res.string.settings_nav_bar_glass_value_percent, it) },
                                valueRange = 0..100,
                                step = 5,
                                isTablet = isTablet,
                                onValueChange = {
                                    ThemeSettingsRepository.setNavBarGlassEffects(effects.copy(tintOpacity = it / 100f))
                                },
                            )
                            SettingsSwitchRow(
                                title = stringResource(Res.string.settings_nav_bar_glass_vibrancy),
                                description = stringResource(Res.string.settings_nav_bar_glass_vibrancy_description),
                                checked = style.vibrancy,
                                isTablet = isTablet,
                                onCheckedChange = { ThemeSettingsRepository.setNavBarGlass(style.copy(vibrancy = it)) },
                            )
                            SettingsSwitchRow(
                                title = stringResource(Res.string.settings_nav_bar_glass_shadow),
                                description = stringResource(Res.string.settings_nav_bar_glass_shadow_description),
                                checked = style.shadow,
                                isTablet = isTablet,
                                onCheckedChange = { ThemeSettingsRepository.setNavBarGlass(style.copy(shadow = it)) },
                            )
                            if (style.usesLens) {
                                SettingsSwitchRow(
                                    title = stringResource(Res.string.settings_nav_bar_glass_dispersion),
                                    description = stringResource(Res.string.settings_nav_bar_glass_dispersion_description),
                                    checked = effects.chromaticAberration,
                                    isTablet = isTablet,
                                    onCheckedChange = {
                                        ThemeSettingsRepository.setNavBarGlassEffects(effects.copy(chromaticAberration = it))
                                    },
                                )
                            }
                            SettingsSwitchRow(
                                title = stringResource(Res.string.settings_nav_bar_glass_selection_lens),
                                description = stringResource(Res.string.settings_nav_bar_glass_selection_lens_description),
                                checked = style.selectionLens,
                                isTablet = isTablet,
                                onCheckedChange = { ThemeSettingsRepository.setNavBarGlass(style.copy(selectionLens = it)) },
                            )
                        }
                    }
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_nav_bar_glass_highlight),
                        description = stringResource(Res.string.settings_nav_bar_glass_highlight_description),
                        checked = style.edgeHighlight,
                        isTablet = isTablet,
                        onCheckedChange = { ThemeSettingsRepository.setNavBarGlass(style.copy(edgeHighlight = it)) },
                    )
                }
            }
            NuvioBottomSheetDivider()
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_nav_bar_icon_color),
                description = stringResource(Res.string.settings_nav_bar_icon_color_description),
                checked = iconColor != null,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    ThemeSettingsRepository.setNavBarIconColor(if (enabled) NavBarIconColorPresets.first() else null)
                },
            )
            AnimatedVisibility(iconColor != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NavBarIconColorPresets.forEach { argb ->
                        val selected = iconColor == argb
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                )
                                .semantics { this.selected = selected }
                                .clickable { ThemeSettingsRepository.setNavBarIconColor(argb) },
                        )
                    }
                }
            }
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.settings_nav_bar_glass_reset),
                onClick = ThemeSettingsRepository::resetNavBarGlass,
            )
        }
    }
}

private val NavBarIconColorPresets = listOf(
    0xFFFFFFFF.toInt(),
    0xFFB0B0B8.toInt(),
    0xFF8AB4F8.toInt(),
    0xFF81C995.toInt(),
    0xFFFDD663.toInt(),
    0xFFF28B82.toInt(),
)

private fun LiquidGlassMaterial.labelRes() = when (this) {
    LiquidGlassMaterial.LIQUID -> Res.string.settings_nav_bar_glass_material_liquid
    LiquidGlassMaterial.FROSTED -> Res.string.settings_nav_bar_glass_material_frosted
    LiquidGlassMaterial.CLEAR -> Res.string.settings_nav_bar_glass_material_clear
    LiquidGlassMaterial.SOLID -> Res.string.settings_nav_bar_glass_material_solid
}
