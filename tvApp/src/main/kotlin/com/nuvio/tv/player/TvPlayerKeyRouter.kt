package com.nuvio.tv.player

import android.view.KeyEvent
import co.touchlab.kermit.Logger
import com.nuvio.app.tv.TvPlayerRemote
import com.nuvio.app.tv.TvPlayerRemoteRegistry

/**
 * Remote-control mapping for the in-app player, applied before Compose sees the key.
 *
 * - Controls hidden: OK = play/pause (or the visible skip / next-episode prompt),
 *   left/right = seek by the configured interval (with seek thumbnails: the controls open and the
 *   timeline previews the position, the player seeks once the presses stop), up/down/menu = show controls.
 * - Controls visible: D-pad moves between the player buttons, Back hides the controls.
 * - A panel, modal or prompt is open: keys go untouched to it.
 * - "Info" panel open: up/down scroll it, other navigation keys close it.
 * - Media keys work in every state.
 * - Dedicated buttons work in every state unless a blocking prompt is open: subtitles and audio
 *   open/close their menu, guide opens the episodes (series) or channels (Live TV) panel,
 *   next/previous and channel up/down change episode or channel, zoom cycles the resize mode,
 *   info shows/hides the controls.
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
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (firstPress && remote.isPlaying) remote.togglePlayback()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            -> {
                remote.seekStep(forward = true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            -> {
                remote.seekStep(forward = false)
                return true
            }
        }

        val shortcut = shortcutFor(event.keyCode)
        if (shortcut != null) {
            // Held keys repeat nothing: the menus would flicker open and closed.
            if (!firstPress) return event.keyCode in consumedKeys
            return shortcut(remote).also { consumed ->
                log.d { "Remote button ${KeyEvent.keyCodeToString(event.keyCode)} consumed=$consumed" }
            }
        }

        if (remote.overlayOpen) return false

        if (remote.streamInfoOpen && handleStreamInfoKey(remote, event.keyCode, firstPress)) return true

        if (remote.controlsVisible) {
            remote.registerActivity()
            // The timeline that opened for a previewed seek has not taken its steps yet: held keys
            // keep queueing, or they would move the focus among the buttons meanwhile.
            if (remote.seekPreviewPending &&
                (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
            ) {
                remote.seekStep(forward = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                return true
            }
            if ((event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_INFO) && firstPress) {
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
            KeyEvent.KEYCODE_INFO,
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

    /**
     * "Info" panel (no buttons): up/down scroll its details while there is more to read that way,
     * any other navigation key (and up/down at the end of the details) closes it.
     * Held keys only scroll, so holding down to read does not close the panel at the end.
     */
    private fun handleStreamInfoKey(remote: TvPlayerRemote, keyCode: Int, firstPress: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                if (!remote.scrollStreamInfo(down = keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && firstPress) {
                    remote.closeStreamInfo()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK,
            -> if (firstPress) remote.closeStreamInfo()
            else -> return false
        }
        if (remote.controlsVisible) remote.registerActivity()
        return true
    }

    /** Dedicated remote buttons; null for keys handled by the state-dependent mapping. */
    private fun shortcutFor(keyCode: Int): ((TvPlayerRemote) -> Boolean)? = when (keyCode) {
        KeyEvent.KEYCODE_CAPTIONS -> TvPlayerRemote::toggleSubtitleMenu
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> TvPlayerRemote::toggleAudioMenu
        KeyEvent.KEYCODE_GUIDE -> TvPlayerRemote::toggleGuidePanel
        KeyEvent.KEYCODE_MEDIA_NEXT -> TvPlayerRemote::playNext
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvPlayerRemote::playPrevious
        KeyEvent.KEYCODE_CHANNEL_UP -> { remote -> remote.zapChannel(1) }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> { remote -> remote.zapChannel(-1) }
        KeyEvent.KEYCODE_TV_ZOOM_MODE -> TvPlayerRemote::cycleResizeMode
        else -> null
    }
}
