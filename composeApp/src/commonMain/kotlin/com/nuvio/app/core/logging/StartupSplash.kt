package com.nuvio.app.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cold start: the startup splash stays on screen until [release] is called.
 * On phones it is the system splash screen; on Android TV it is the Compose startup screen
 * (logo and profile background), because older TV systems show the system splash as a black
 * screen without the logo.
 *
 * Released when the home shows its first content, when the gate needs the user (sign in,
 * profile selection), when the app opens on another screen, or by the platform time limit.
 */
object StartupSplash {
    private val released = MutableStateFlow(false)

    val releasedState: StateFlow<Boolean> = released.asStateFlow()

    val isReleased: Boolean
        get() = released.value

    fun release(reason: String) {
        if (!released.compareAndSet(expect = false, update = true)) return
        StartupTrace.mark("Splash released ($reason)")
    }
}
