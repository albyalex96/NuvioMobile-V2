package com.nuvio.app.features.settings

import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import co.touchlab.kermit.Logger
import com.nuvio.app.core.format.DateFormatPreset
import com.nuvio.app.core.format.EpisodeCodeFormat
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.LiquidGlassSettings
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.decodeLiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.encodeLiquidGlassEffectSettings
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.CustomThemeColors
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.availableAppThemes
import com.nuvio.app.features.membership.resolveAppTheme
import com.nuvio.app.features.membership.resolveCustomThemeColors
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ThemeSettingsRepository {
    private val log = Logger.withTag("LiquidGlass")
    private const val NAV_BAR_GLASS_KEY = "enhanced_local_nav_bar_glass"
    private const val SEE_THROUGH_DETAIL_CONTROLS_KEY = "enhanced_local_see_through_detail_controls"
    private const val ROUND_PROFILE_BUTTONS_KEY = "enhanced_local_round_profile_buttons"
    private const val NAV_BAR_GLASS_EFFECTS_KEY = "enhanced_local_nav_bar_glass_effects"
    private const val NAV_BAR_ICON_COLOR_KEY = "enhanced_local_nav_bar_icon_color"
    private const val SYNC_ENHANCED_MARKER_KEY = "enhanced_theme_options"
    private const val SYNC_LIQUID_GLASS_EFFECTS_KEY = "enhanced_liquid_glass_effects"

    /** Device settings that also travel with the profile sync (stored as local strings). */
    private val syncedLocalKeys = listOf(
        NAV_BAR_GLASS_KEY,
        NAV_BAR_GLASS_EFFECTS_KEY,
        NAV_BAR_ICON_COLOR_KEY,
        SEE_THROUGH_DETAIL_CONTROLS_KEY,
        ROUND_PROFILE_BUTTONS_KEY,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _selectedThemePreference = MutableStateFlow<AppTheme?>(null)
    val selectedThemePreference: StateFlow<AppTheme?> = _selectedThemePreference.asStateFlow()
    private val _selectedTheme = MutableStateFlow(AppTheme.WHITE)
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    private val _customThemePreference = MutableStateFlow(CustomThemeColors.Default)
    val customThemePreference: StateFlow<CustomThemeColors> = _customThemePreference.asStateFlow()
    private val _customThemeColors = MutableStateFlow(CustomThemeColors.solid(CustomThemeColors.Default.second))
    val customThemeColors: StateFlow<CustomThemeColors> = _customThemeColors.asStateFlow()

    private val _amoledEnabled = MutableStateFlow(false)
    val amoledEnabled: StateFlow<Boolean> = _amoledEnabled.asStateFlow()

    private val _amoledSurfacesEnabled = MutableStateFlow(false)
    val amoledSurfacesEnabled: StateFlow<Boolean> = _amoledSurfacesEnabled.asStateFlow()

    private val _liquidGlassNativeTabBarEnabled = MutableStateFlow(false)
    val liquidGlassNativeTabBarEnabled: StateFlow<Boolean> = _liquidGlassNativeTabBarEnabled.asStateFlow()

    private val _selectedAppLanguage = MutableStateFlow(AppLanguage.DEVICE)
    val selectedAppLanguage: StateFlow<AppLanguage> = _selectedAppLanguage.asStateFlow()

    private val _navBarStyle = MutableStateFlow(NavBarStyle.ADAPTIVE)
    val navBarStyle: StateFlow<NavBarStyle> = _navBarStyle.asStateFlow()

    private val _navBarGlowEnabled = MutableStateFlow(true)
    val navBarGlowEnabled: StateFlow<Boolean> = _navBarGlowEnabled.asStateFlow()

    private val _navBarGlass = MutableStateFlow(LiquidGlassStyle.Default)
    val navBarGlass: StateFlow<LiquidGlassStyle> = _navBarGlass.asStateFlow()

    private val _navBarGlassEffects = MutableStateFlow(LiquidGlassEffectDefaults.floatingBar)
    internal val navBarGlassEffects: StateFlow<LiquidGlassEffectSettings> = _navBarGlassEffects.asStateFlow()

    /** ARGB color of the unselected nav bar icons and labels; null keeps the theme color. */
    private val _navBarIconColor = MutableStateFlow<Int?>(null)
    val navBarIconColor: StateFlow<Int?> = _navBarIconColor.asStateFlow()

    /** Details buttons and chips turn see-through over the cinematic / dominant-color backdrop. */
    private val _seeThroughDetailControls = MutableStateFlow(true)
    val seeThroughDetailControls: StateFlow<Boolean> = _seeThroughDetailControls.asStateFlow()

    /** Profile edit/switch/save actions as round icon buttons in the header instead of full-width buttons. */
    private val _roundProfileButtons = MutableStateFlow(true)
    val roundProfileButtons: StateFlow<Boolean> = _roundProfileButtons.asStateFlow()

    private val _liquidGlassEffectsEnabled = MutableStateFlow(false)
    val liquidGlassEffectsEnabled: StateFlow<Boolean> = _liquidGlassEffectsEnabled.asStateFlow()

    private val _topNavBarEnabled = MutableStateFlow(false)
    val topNavBarEnabled: StateFlow<Boolean> = _topNavBarEnabled.asStateFlow()

    private val _topNavBarStyle = MutableStateFlow(TopNavBarStyle.EXPANDED)
    val topNavBarStyle: StateFlow<TopNavBarStyle> = _topNavBarStyle.asStateFlow()

    private val _dateFormat = MutableStateFlow(DateFormatPreset.SYSTEM)
    val dateFormat: StateFlow<DateFormatPreset> = _dateFormat.asStateFlow()

    // Snapshot mirror of [dateFormat]: date formatters read it during composition so that
    // visible dates recompose immediately when the preset changes.
    private val _dateFormatState = mutableStateOf(DateFormatPreset.SYSTEM)
    val dateFormatState: State<DateFormatPreset> = _dateFormatState

    private val _episodeCodeFormat = MutableStateFlow(EpisodeCodeFormat.DEFAULT)
    val episodeCodeFormat: StateFlow<EpisodeCodeFormat> = _episodeCodeFormat.asStateFlow()

    // Snapshot mirror of [episodeCodeFormat], read by episode code formatters during composition.
    private val _episodeCodeFormatState = mutableStateOf(EpisodeCodeFormat.DEFAULT)
    val episodeCodeFormatState: State<EpisodeCodeFormat> = _episodeCodeFormatState

    private var hasLoaded = false
    private var observesMembership = false

    fun ensureLoaded() {
        observeMembership()
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        _selectedThemePreference.value = null
        _selectedTheme.value = AppTheme.WHITE
        _customThemePreference.value = CustomThemeColors.Default
        _customThemeColors.value = CustomThemeColors.solid(CustomThemeColors.Default.second)
        _amoledEnabled.value = false
        _amoledSurfacesEnabled.value = false
        _liquidGlassNativeTabBarEnabled.value = false
        NativeTabBridge.publishAccentColor(ThemeColors.White.nativeAccentHex)
        NativeTabBridge.publishLiquidGlassEnabled(false)
        _selectedAppLanguage.value = AppLanguage.DEVICE
        _navBarGlowEnabled.value = true
        _navBarStyle.value = NavBarStyle.ADAPTIVE
        _topNavBarEnabled.value = false
        _topNavBarStyle.value = TopNavBarStyle.EXPANDED
        applyDateFormat(DateFormatPreset.SYSTEM)
        applyEpisodeCodeFormat(EpisodeCodeFormat.DEFAULT)
        applyLiquidGlassEffectsEnabled(false)
        _navBarGlass.value = LiquidGlassStyle.Default
        _navBarGlassEffects.value = LiquidGlassEffectDefaults.floatingBar
        _navBarIconColor.value = null
        _seeThroughDetailControls.value = true
        _roundProfileButtons.value = true
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val stored = ThemeSettingsStorage.loadSelectedTheme()
        val theme = if (stored != null) {
            try {
                AppTheme.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            null
        }
        _selectedThemePreference.value = theme
        _customThemePreference.value = CustomThemeColors.decode(ThemeSettingsStorage.loadCustomThemeColors())
        applyEffectiveTheme()
        val amoledEnabled = ThemeSettingsStorage.loadAmoledEnabled() ?: false
        _amoledEnabled.value = amoledEnabled
        _amoledSurfacesEnabled.value = amoledEnabled && (ThemeSettingsStorage.loadAmoledSurfacesEnabled() ?: false)
        val liquidGlassEnabled = ThemeSettingsStorage.loadLiquidGlassNativeTabBarEnabled() ?: false
        _liquidGlassNativeTabBarEnabled.value = liquidGlassEnabled
        NativeTabBridge.publishLiquidGlassEnabled(liquidGlassEnabled)
        val appLanguage = AppLanguage.fromCode(ThemeSettingsStorage.loadSelectedAppLanguage())
        ThemeSettingsStorage.applySelectedAppLanguage(appLanguage.code)
        _selectedAppLanguage.value = appLanguage
        _navBarGlowEnabled.value = ThemeSettingsStorage.loadNavBarGlowEnabled() ?: true
        _navBarStyle.value = NavBarStyle.fromKey(ThemeSettingsStorage.loadNavBarStyle())
        _topNavBarEnabled.value = ThemeSettingsStorage.loadTopNavBarEnabled() ?: false
        _topNavBarStyle.value = TopNavBarStyle.fromKey(ThemeSettingsStorage.loadTopNavBarStyle())
        applyDateFormat(DateFormatPreset.fromKey(ThemeSettingsStorage.loadDateFormat()))
        applyEpisodeCodeFormat(EpisodeCodeFormat.fromKey(ThemeSettingsStorage.loadEpisodeCodeFormat()))
        applyLiquidGlassEffectsEnabled(ThemeSettingsStorage.loadLiquidGlassEffectsEnabled() ?: false)
        _navBarGlass.value = LiquidGlassStyle.decode(ThemeSettingsStorage.loadLocalString(NAV_BAR_GLASS_KEY))
        _navBarGlassEffects.value = decodeLiquidGlassEffectSettings(
            ThemeSettingsStorage.loadLocalString(NAV_BAR_GLASS_EFFECTS_KEY),
            LiquidGlassEffectDefaults.floatingBar,
            LiquidGlassEffectDefaults.floatingBarLimits,
        )
        _navBarIconColor.value = ThemeSettingsStorage.loadLocalString(NAV_BAR_ICON_COLOR_KEY)
            ?.toLongOrNull(16)?.toInt()
        _seeThroughDetailControls.value = ThemeSettingsStorage.loadLocalString(SEE_THROUGH_DETAIL_CONTROLS_KEY) != "0"
        _roundProfileButtons.value = ThemeSettingsStorage.loadLocalString(ROUND_PROFILE_BUTTONS_KEY) != "0"
    }

    fun setTheme(theme: AppTheme) {
        ensureLoaded()
        val access = MemberAccessRepository.access.value
        if (theme !in availableAppThemes(access.entitlements)) return
        if (_selectedThemePreference.value == theme) return
        _selectedThemePreference.value = theme
        ThemeSettingsStorage.saveSelectedTheme(theme.name)
        applyEffectiveTheme()
    }

    fun setCustomTheme(colors: CustomThemeColors) {
        ensureLoaded()
        val access = MemberAccessRepository.access.value
        val selectedColors = resolveCustomThemeColors(colors, access.tier)
        ThemeSettingsStorage.saveCustomThemeColors(selectedColors.encode())
        ThemeSettingsStorage.saveSelectedTheme(AppTheme.CUSTOM.name)
        _customThemePreference.value = selectedColors
        _selectedThemePreference.value = AppTheme.CUSTOM
        applyEffectiveTheme()
    }

    fun setAmoled(enabled: Boolean) {
        ensureLoaded()
        if (_amoledEnabled.value == enabled) return
        _amoledEnabled.value = enabled
        ThemeSettingsStorage.saveAmoledEnabled(enabled)
        if (!enabled && _amoledSurfacesEnabled.value) {
            _amoledSurfacesEnabled.value = false
            ThemeSettingsStorage.saveAmoledSurfacesEnabled(false)
        }
    }

    fun setAmoledSurfaces(enabled: Boolean) {
        ensureLoaded()
        if (enabled && !_amoledEnabled.value) return
        if (_amoledSurfacesEnabled.value == enabled) return
        _amoledSurfacesEnabled.value = enabled
        ThemeSettingsStorage.saveAmoledSurfacesEnabled(enabled)
    }

    fun setLiquidGlassNativeTabBar(enabled: Boolean) {
        ensureLoaded()
        if (_liquidGlassNativeTabBarEnabled.value == enabled) return
        _liquidGlassNativeTabBarEnabled.value = enabled
        ThemeSettingsStorage.saveLiquidGlassNativeTabBarEnabled(enabled)
        NativeTabBridge.publishLiquidGlassEnabled(enabled)
    }

    fun setAppLanguage(language: AppLanguage) {
        ensureLoaded()
        if (_selectedAppLanguage.value == language) return
        ThemeSettingsStorage.saveSelectedAppLanguage(language.code)
        ThemeSettingsStorage.applySelectedAppLanguage(language.code)
        _selectedAppLanguage.value = language
    }

    fun setNavBarStyle(style: NavBarStyle) {
        ensureLoaded()
        if (_navBarStyle.value == style) return
        _navBarStyle.value = style
        ThemeSettingsStorage.saveNavBarStyle(style.key)
    }

    fun setNavBarGlowEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_navBarGlowEnabled.value == enabled) return
        _navBarGlowEnabled.value = enabled
        ThemeSettingsStorage.saveNavBarGlowEnabled(enabled)
    }

    fun setLiquidGlassEffectsEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_liquidGlassEffectsEnabled.value == enabled) return
        applyLiquidGlassEffectsEnabled(enabled)
        ThemeSettingsStorage.saveLiquidGlassEffectsEnabled(enabled)
        log.i { "Liquid glass effects ${if (enabled) "enabled" else "disabled"}" }
    }

    fun setNavBarGlass(style: LiquidGlassStyle) {
        ensureLoaded()
        if (_navBarGlass.value == style) return
        _navBarGlass.value = style
        ThemeSettingsStorage.saveLocalString(NAV_BAR_GLASS_KEY, style.encode())
        log.d { "Nav bar glass material=${style.material.key}" }
    }

    internal fun setNavBarGlassEffects(settings: LiquidGlassEffectSettings) {
        ensureLoaded()
        val safe = settings.sanitized(LiquidGlassEffectDefaults.floatingBar, LiquidGlassEffectDefaults.floatingBarLimits)
        if (_navBarGlassEffects.value == safe) return
        _navBarGlassEffects.value = safe
        ThemeSettingsStorage.saveLocalString(
            NAV_BAR_GLASS_EFFECTS_KEY,
            encodeLiquidGlassEffectSettings(safe, LiquidGlassEffectDefaults.floatingBar, LiquidGlassEffectDefaults.floatingBarLimits),
        )
    }

    fun setNavBarIconColor(argb: Int?) {
        ensureLoaded()
        if (_navBarIconColor.value == argb) return
        _navBarIconColor.value = argb
        ThemeSettingsStorage.saveLocalString(NAV_BAR_ICON_COLOR_KEY, argb?.toUInt()?.toString(16))
    }

    fun setSeeThroughDetailControls(enabled: Boolean) {
        ensureLoaded()
        if (_seeThroughDetailControls.value == enabled) return
        _seeThroughDetailControls.value = enabled
        ThemeSettingsStorage.saveLocalString(SEE_THROUGH_DETAIL_CONTROLS_KEY, if (enabled) null else "0")
    }

    fun setRoundProfileButtons(enabled: Boolean) {
        ensureLoaded()
        if (_roundProfileButtons.value == enabled) return
        _roundProfileButtons.value = enabled
        ThemeSettingsStorage.saveLocalString(ROUND_PROFILE_BUTTONS_KEY, if (enabled) null else "0")
    }

    /** Adds the Enhanced theme options (liquid glass, nav bar glass, buttons) to the theme sync payload. */
    internal fun withEnhancedSyncOptions(payload: JsonObject): JsonObject = buildJsonObject {
        payload.forEach { (key, value) -> put(key, value) }
        put(SYNC_ENHANCED_MARKER_KEY, encodeSyncBoolean(true))
        ThemeSettingsStorage.loadLiquidGlassEffectsEnabled()?.let { put(SYNC_LIQUID_GLASS_EFFECTS_KEY, encodeSyncBoolean(it)) }
        syncedLocalKeys.forEach { key ->
            ThemeSettingsStorage.loadLocalString(key)?.let { put(key, encodeSyncString(it)) }
        }
    }

    /** Applies the Enhanced theme options from a synced payload; payloads from older versions are skipped. */
    internal fun applyEnhancedSyncOptions(payload: JsonObject) {
        if (payload.decodeSyncBoolean(SYNC_ENHANCED_MARKER_KEY) != true) {
            log.d { "Theme sync: no Enhanced options in the remote payload, keeping the local ones" }
            return
        }
        ThemeSettingsStorage.saveLiquidGlassEffectsEnabled(payload.decodeSyncBoolean(SYNC_LIQUID_GLASS_EFFECTS_KEY) ?: false)
        syncedLocalKeys.forEach { key ->
            ThemeSettingsStorage.saveLocalString(key, payload.decodeSyncString(key))
        }
        log.i { "Theme sync: Enhanced options applied from the remote profile" }
    }

    fun resetNavBarGlass() {
        setNavBarGlass(LiquidGlassStyle.Default)
        setNavBarGlassEffects(LiquidGlassEffectDefaults.floatingBar)
        setNavBarIconColor(null)
        log.i { "Nav bar glass reset to defaults" }
    }

    private fun applyLiquidGlassEffectsEnabled(enabled: Boolean) {
        _liquidGlassEffectsEnabled.value = enabled
        LiquidGlassSettings.enabled = enabled
    }

    fun setTopNavBarEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_topNavBarEnabled.value == enabled) return
        _topNavBarEnabled.value = enabled
        ThemeSettingsStorage.saveTopNavBarEnabled(enabled)
    }

    fun setTopNavBarStyle(style: TopNavBarStyle) {
        ensureLoaded()
        if (_topNavBarStyle.value == style) return
        _topNavBarStyle.value = style
        ThemeSettingsStorage.saveTopNavBarStyle(style.key)
    }

    fun setDateFormat(preset: DateFormatPreset) {
        ensureLoaded()
        if (_dateFormat.value == preset) return
        applyDateFormat(preset)
        ThemeSettingsStorage.saveDateFormat(preset.key)
    }

    private fun applyDateFormat(preset: DateFormatPreset) {
        _dateFormat.value = preset
        _dateFormatState.value = preset
    }

    fun setEpisodeCodeFormat(format: EpisodeCodeFormat) {
        ensureLoaded()
        if (_episodeCodeFormat.value == format) return
        applyEpisodeCodeFormat(format)
        ThemeSettingsStorage.saveEpisodeCodeFormat(format.key)
    }

    private fun applyEpisodeCodeFormat(format: EpisodeCodeFormat) {
        _episodeCodeFormat.value = format
        _episodeCodeFormatState.value = format
    }

    private fun observeMembership() {
        if (observesMembership) return
        observesMembership = true
        MemberAccessRepository.ensureStarted()
        scope.launch {
            MemberAccessRepository.access.collect {
                if (hasLoaded) applyEffectiveTheme()
            }
        }
    }

    private fun applyEffectiveTheme() {
        val access = MemberAccessRepository.access.value
        val effective = resolveAppTheme(
            selectedTheme = _selectedThemePreference.value,
            entitlements = access.entitlements,
        )
        _customThemeColors.value = resolveCustomThemeColors(_customThemePreference.value, access.tier)
        _selectedTheme.value = effective
        NativeTabBridge.publishAccentColor(
            ThemeColors.getColorPalette(effective, _customThemeColors.value).nativeAccentHex,
        )
    }
}
