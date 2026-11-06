package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.logging.StartupSplash
import com.nuvio.app.features.profiles.ProfileBackgroundBackdrop
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.AppBrandWordmark
import kotlinx.coroutines.delay

/** Safety net: the startup screen never stays longer than this. */
private const val TV_STARTUP_SCREEN_MAX_MS = 10_000L

/**
 * Android TV startup screen, like the original TV app: the active profile background, the
 * brand logo of the current theme and a spinner, until [StartupSplash] is released (home ready,
 * the gate needs the user, or the time limit). It covers the gate, so the loading screen and
 * the first home composition are never shown half-built.
 */
@Composable
internal fun TvStartupScreen() {
    val released by StartupSplash.releasedState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        delay(TV_STARTUP_SCREEN_MAX_MS)
        StartupSplash.release("time limit")
    }
    AnimatedVisibility(
        visible = !released,
        enter = EnterTransition.None,
        exit = fadeOut(tween(400)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.nuvio.colors.background),
        ) {
            ProfileBackgroundBackdrop(
                profile = profileState.activeProfile,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppBrandWordmark(modifier = Modifier.height(48.dp))
                Spacer(modifier = Modifier.height(32.dp))
                NuvioLoadingIndicator()
            }
        }
    }
}
