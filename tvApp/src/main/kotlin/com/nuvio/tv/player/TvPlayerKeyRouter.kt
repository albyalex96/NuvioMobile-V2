package com.nuvio.tv.player

import android.view.KeyEvent
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvPlayerRemote
import com.nuvio.app.tv.TvPlayerRemoteRegistry

/**
 * Remote-control mapping for the in-app player, applied before Compose sees the key.
 *
 * - Controls hidden: OK = play/pause (or the visible skip / next-episode prompt),
 *   left/right = cumulative seek by the configured interval, up/down/menu = show controls.
 * - Controls visible: D-pad moves between the player buttons, Back hides the controls.
 * - A panel, modal or prompt is open: keys go untouched to it.
 * - Media keys work in every state.
 */
object TvPlayerKeyRouter {
    private val log = Logger.withTag("TvRemote")

    /** Key codes whose ACTION_DOWN was consumed here: their ACTION_UP is swallowed too. */
    private val consumedKeys = mutableSetOf<Int>()

    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        val remote = TvPlayerRemoteRegistry.active
        if (remote == null) {
            consumedKeys.clear()
            return false
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(remote, event).also { consumed ->
                if (consumed) consumedKeys += code
            }
            KeyEvent.ACTION_UP -> consumedKeys.remove(code)
            else -> false
        }
    }

    private fun handleDown(remote: TvPlayerRemote, event: KeyEvent): Boolean {
        val firstPress = event.repeatCount == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (firstPress) remote.togglePlayback()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (firstPress && !remote.isPlaying) remote.togglePlayback()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (firstPress && remote.isPlaying) remote.togglePlayback()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                remote.seekStep(forward = true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                remote.seekStep(forward = false)
                return true
            }
        }

        if (remote.overlayOpen) return false

        if (remote.controlsVisible) {
            remote.registerActivity()
            if (event.keyCode == KeyEvent.KEYCODE_BACK && firstPress) {
                remote.hideControls()
                return true
            }
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (firstPress && !remote.activatePrompt()) {
                    remote.togglePlayback()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                remote.seekStep(forward = false)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                remote.seekStep(forward = true)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MENU,
            -> {
                if (firstPress) {
                    log.d { "Showing player controls from remote" }
                    remote.showControls()
                }
                true
            }
            else -> false
        }
    }
}
