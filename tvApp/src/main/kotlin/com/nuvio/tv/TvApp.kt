package com.nuvio.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.nuvio.app.App
import com.nuvio.app.tv.LocalTvShell
import com.nuvio.app.tv.TvShell
import com.nuvio.tv.auth.TvAuthScreen
import com.nuvio.tv.details.TvDetailsScreen
import com.nuvio.tv.home.TvHomeScreen
import com.nuvio.tv.shell.TvSidebarChrome
import com.nuvio.tv.ui.TvFocusCoordinator
import com.nuvio.tv.ui.TvThemeOverlay

/** The shared app, rendered through the TV shell. */
@Composable
fun TvApp() {
    val shell = remember {
        TvShell(
            themeOverlay = { content -> TvThemeOverlay(content) },
            tabsChrome = { chrome, content -> TvSidebarChrome(chrome, content) },
            home = { model -> TvHomeScreen(model) },
            details = { model -> TvDetailsScreen(model) },
            auth = { model -> TvAuthScreen(model) },
        )
    }
    CompositionLocalProvider(LocalTvShell provides shell) {
        TvFocusCoordinator {
            App()
        }
    }
}
