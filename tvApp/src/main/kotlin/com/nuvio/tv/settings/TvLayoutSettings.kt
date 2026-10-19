package com.nuvio.tv.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val log = Logger.withTag("TvLayout")

/** Poster width presets of NuvioTV's layout settings (dp of the base 2:3 poster). */
val TvPosterWidthPresets = listOf(104, 112, 120, 126, 134, 140)
val TvCornerRadiusPresets = listOf(0, 4, 8, 12, 16)
val TvExpandDelayRange = 0..10

const val DefaultTvPosterWidthDp = 126
const val DefaultTvCornerRadiusDp = 12
const val DefaultTvExpandDelaySeconds = 3

/**
 * Layout of the TV app (Settings -> Appearance -> TV layout), ported from NuvioTV's layout
 * settings. TV only: saved on the device for each profile, never synced, so the phone's
 * poster settings do not change the TV rows and vice versa.
 */
data class TvLayoutSettings(
    val posterWidthDp: Int = DefaultTvPosterWidthDp,
    val cornerRadiusDp: Int = DefaultTvCornerRadiusDp,
    val landscapePosters: Boolean = false,
    val alwaysShowLandscapeClearlogo: Boolean = false,
    val posterLabels: Boolean = true,
    val continueWatchingStyle: ContinueWatchingSectionStyle = ContinueWatchingSectionStyle.Card,
    val expandFocusedPoster: Boolean = true,
    val expandDelaySeconds: Int = DefaultTvExpandDelaySeconds,
    val heroFullScreenBackdrop: Boolean = false,
    val hideFloatingPill: Boolean = false,
    val sidebarBlur: Boolean = false,
) {
    /** Height of the base poster: NuvioTV keeps the 2:3 shape when the width changes. */
    val posterHeightDp: Int get() = posterWidthDp * 3 / 2
}

object TvLayoutSettingsRepository {
    private const val PREFERENCES_NAME = "nuvio_tv_layout"
    private const val KEY_POSTER_WIDTH = "poster_width_dp"
    private const val KEY_CORNER_RADIUS = "corner_radius_dp"
    private const val KEY_LANDSCAPE_POSTERS = "landscape_posters"
    private const val KEY_ALWAYS_CLEARLOGO = "always_show_landscape_clearlogo"
    private const val KEY_POSTER_LABELS = "poster_labels"
    private const val KEY_CONTINUE_WATCHING_STYLE = "continue_watching_style"
    private const val KEY_EXPAND_FOCUSED_POSTER = "expand_focused_poster"
    private const val KEY_EXPAND_DELAY = "expand_delay_seconds"
    private const val KEY_HERO_FULL_SCREEN = "hero_full_screen_backdrop"
    private const val KEY_HIDE_FLOATING_PILL = "hide_floating_pill"
    private const val KEY_SIDEBAR_BLUR = "sidebar_blur"

    private var preferences: SharedPreferences? = null
    private var loadedProfileId: Int? = null
    private val _settings = MutableStateFlow(TvLayoutSettings())
    val settings: StateFlow<TvLayoutSettings> = _settings.asStateFlow()

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    /** Loads the values of the active profile (again after a profile switch). */
    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        val prefs = preferences ?: return
        loadedProfileId = profileId
        fun key(name: String) = ProfileScopedKey.of(name, profileId)
        _settings.value = TvLayoutSettings(
            posterWidthDp = prefs.getInt(key(KEY_POSTER_WIDTH), DefaultTvPosterWidthDp).takeIf { it > 0 } ?: DefaultTvPosterWidthDp,
            cornerRadiusDp = prefs.getInt(key(KEY_CORNER_RADIUS), DefaultTvCornerRadiusDp).coerceAtLeast(0),
            landscapePosters = prefs.getBoolean(key(KEY_LANDSCAPE_POSTERS), false),
            alwaysShowLandscapeClearlogo = prefs.getBoolean(key(KEY_ALWAYS_CLEARLOGO), false),
            posterLabels = prefs.getBoolean(key(KEY_POSTER_LABELS), true),
            continueWatchingStyle = prefs.getString(key(KEY_CONTINUE_WATCHING_STYLE), null)
                ?.let { name -> ContinueWatchingSectionStyle.entries.firstOrNull { it.name == name } }
                ?: ContinueWatchingSectionStyle.Card,
            expandFocusedPoster = prefs.getBoolean(key(KEY_EXPAND_FOCUSED_POSTER), true),
            expandDelaySeconds = prefs.getInt(key(KEY_EXPAND_DELAY), DefaultTvExpandDelaySeconds)
                .coerceIn(TvExpandDelayRange.first, TvExpandDelayRange.last),
            heroFullScreenBackdrop = prefs.getBoolean(key(KEY_HERO_FULL_SCREEN), false),
            hideFloatingPill = prefs.getBoolean(key(KEY_HIDE_FLOATING_PILL), false),
            sidebarBlur = prefs.getBoolean(key(KEY_SIDEBAR_BLUR), false),
        )
        log.d { "TV layout loaded for profile $profileId: ${_settings.value}" }
    }

    fun setPosterWidth(widthDp: Int) = update(KEY_POSTER_WIDTH, widthDp) { copy(posterWidthDp = widthDp) }
    fun setCornerRadius(radiusDp: Int) = update(KEY_CORNER_RADIUS, radiusDp) { copy(cornerRadiusDp = radiusDp) }
    fun setLandscapePosters(enabled: Boolean) = update(KEY_LANDSCAPE_POSTERS, enabled) { copy(landscapePosters = enabled) }
    fun setAlwaysShowLandscapeClearlogo(enabled: Boolean) =
        update(KEY_ALWAYS_CLEARLOGO, enabled) { copy(alwaysShowLandscapeClearlogo = enabled) }
    fun setPosterLabels(enabled: Boolean) = update(KEY_POSTER_LABELS, enabled) { copy(posterLabels = enabled) }
    fun setContinueWatchingStyle(style: ContinueWatchingSectionStyle) =
        update(KEY_CONTINUE_WATCHING_STYLE, style.name) { copy(continueWatchingStyle = style) }
    fun setExpandFocusedPoster(enabled: Boolean) =
        update(KEY_EXPAND_FOCUSED_POSTER, enabled) { copy(expandFocusedPoster = enabled) }
    fun setExpandDelaySeconds(seconds: Int) {
        val value = seconds.coerceIn(TvExpandDelayRange.first, TvExpandDelayRange.last)
        update(KEY_EXPAND_DELAY, value) { copy(expandDelaySeconds = value) }
    }
    fun setHeroFullScreenBackdrop(enabled: Boolean) =
        update(KEY_HERO_FULL_SCREEN, enabled) { copy(heroFullScreenBackdrop = enabled) }
    fun setHideFloatingPill(enabled: Boolean) = update(KEY_HIDE_FLOATING_PILL, enabled) { copy(hideFloatingPill = enabled) }
    fun setSidebarBlur(enabled: Boolean) = update(KEY_SIDEBAR_BLUR, enabled) { copy(sidebarBlur = enabled) }

    /** "Reset to default" of the poster card section (NuvioTV `ResetPosterCardStyle`). */
    fun resetPosterCardStyle() {
        setPosterWidth(DefaultTvPosterWidthDp)
        setCornerRadius(DefaultTvCornerRadiusDp)
        setLandscapePosters(false)
        setAlwaysShowLandscapeClearlogo(false)
        log.i { "Poster card style reset to defaults" }
    }

    private fun update(name: String, value: Any, transform: TvLayoutSettings.() -> TvLayoutSettings) {
        ensureLoaded()
        val next = _settings.value.transform()
        if (next == _settings.value) return
        _settings.value = next
        val key = ProfileScopedKey.of(name, loadedProfileId ?: ProfileRepository.activeProfileId)
        preferences?.edit()?.apply {
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
            }
        }?.apply()
        log.i { "TV layout: $name = $value" }
    }
}

/** TV layout of the active profile; recomposes on changes and after a profile switch. */
@Composable
fun rememberTvLayoutSettings(): TvLayoutSettings {
    val profile by ProfileRepository.state.collectAsState()
    val profileId = profile.activeProfile?.profileIndex
    TvLayoutSettingsRepository.ensureLoaded()
    LaunchedEffect(profileId) { TvLayoutSettingsRepository.ensureLoaded() }
    val settings by TvLayoutSettingsRepository.settings.collectAsState()
    return settings
}
