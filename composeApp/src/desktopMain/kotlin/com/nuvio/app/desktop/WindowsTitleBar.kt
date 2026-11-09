package com.nuvio.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * Windows draws the title bar (minimise/maximise/close and the icon) in light mode by default,
 * which clashes with Nuvio. DWM is asked for the dark style and, on Windows 11, for a caption
 * colour equal to the background of the Nuvio theme (updated when the theme changes).
 */
internal object WindowsTitleBar {
    private val log = Logger.withTag("DesktopTitleBar")
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19 // Windows 10 before 20H1
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    @Suppress("FunctionName")
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm: Dwm? by lazy {
        runCatching { Native.load("dwmapi", Dwm::class.java) }
            .onFailure { log.w(it) { "dwmapi not available" } }
            .getOrNull()
    }

    fun apply(window: Window, background: Color) {
        if (!isWindows) return
        val api = dwm ?: return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            fun set(attribute: Int, value: Int): Int {
                val memory = Memory(4).apply { setInt(0, value) }
                return api.DwmSetWindowAttribute(hwnd, attribute, memory, 4)
            }
            if (set(DWMWA_USE_IMMERSIVE_DARK_MODE, 1) != 0) set(DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, 1)
            // COLORREF is 0x00BBGGRR. Ignored (non-zero result) before Windows 11: dark mode is enough there.
            val argb = background.toArgb()
            val colorRef = ((argb and 0xFF) shl 16) or (argb and 0xFF00) or ((argb shr 16) and 0xFF)
            set(DWMWA_CAPTION_COLOR, colorRef)
            set(DWMWA_BORDER_COLOR, colorRef)
            set(DWMWA_TEXT_COLOR, 0x00F8F7F5)
            log.d { "Title bar styled" }
        }.onFailure { log.w(it) { "Title bar not styled" } }
    }
}

/** Keeps the title bar of [window] in the colours of the current Nuvio theme. */
@Composable
internal fun NuvioWindowsTitleBar(window: Window) {
    val theme by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.selectedTheme
    }.collectAsStateWithLifecycle()
    val amoled by ThemeSettingsRepository.amoledEnabled.collectAsStateWithLifecycle()
    val customColors by ThemeSettingsRepository.customThemeColors.collectAsStateWithLifecycle()
    LaunchedEffect(window, theme, amoled, customColors) {
        val background = if (amoled) Color.Black else ThemeColors.getColorPalette(theme, customColors).background
        WindowsTitleBar.apply(window, background)
    }
}
