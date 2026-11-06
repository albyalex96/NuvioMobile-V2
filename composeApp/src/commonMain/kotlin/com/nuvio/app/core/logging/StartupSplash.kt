package com.nuvio.app.core.logging

import kotlin.concurrent.Volatile

/**
 * Cold start: the platform keeps its system splash screen on screen until [release] is called.
 * The system splash is drawn outside the app's main thread, so it stays smooth while the main
 * thread composes the shell and the home (a Compose spinner would stutter there).
 *
 * Released when the home shows its first content, when the gate needs the user (sign in,
 * profile selection), when the app opens on another screen, or by the platform time limit.
 */
object StartupSplash {
    @Volatile
    var isReleased: Boolean = false
        private set

    fun release(reason: String) {
        if (isReleased) return
        isReleased = true
        StartupTrace.mark("Splash released ($reason)")
    }
}
