package com.nuvio.app.features.player

/**
 * Player buttons; the order here is the default order. [topBar] buttons sit in the top-right group
 * (next to lock and close) and can only be shown or hidden there; the others are the bottom bar.
 */
enum class PlayerToolbarButton(val storageId: String, val topBar: Boolean = false) {
    NEXT_EPISODE("next_episode"),
    SUBTITLES("subtitles"),
    AUDIO("audio"),
    SOURCES("sources"),
    EPISODES("episodes"),
    CHAPTERS("chapters"),
    LIVE_CHANNELS("live_channels"),
    LIVE_GUIDE("live_guide"),
    SWITCH_ENGINE("switch_engine"),
    SPEED("speed"),
    RESIZE("resize"),
    EXTERNAL_PLAYER("external_player"),
    STREAM_INFO("stream_info"),
    VIDEO_SETTINGS("video_settings"),
    SUBMIT_INTRO("submit_intro"),
    PICTURE_IN_PICTURE("picture_in_picture", topBar = true),
    CAST("cast", topBar = true),
    WATCH_PARTY("watch_party", topBar = true),
    ;

    companion object {
        fun fromStorageId(value: String): PlayerToolbarButton? =
            entries.firstOrNull { it.storageId == value }
    }
}

data class PlayerToolbarEntry(
    val button: PlayerToolbarButton,
    val visible: Boolean,
)

data class PlayerToolbarLayout(val entries: List<PlayerToolbarEntry>) {
    /** Bottom bar buttons always on the bar. */
    val visibleButtons: List<PlayerToolbarButton>
        get() = entries.filter { it.visible && !it.button.topBar }.map { it.button }

    /** Bottom bar buttons behind the arrow. */
    val hiddenButtons: List<PlayerToolbarButton>
        get() = entries.filter { !it.visible && !it.button.topBar }.map { it.button }

    /** Top bar buttons that are shown, in order. */
    val topBarButtons: List<PlayerToolbarButton>
        get() = entries.filter { it.visible && it.button.topBar }.map { it.button }

    fun withVisibility(button: PlayerToolbarButton, visible: Boolean): PlayerToolbarLayout =
        PlayerToolbarLayout(entries.map { if (it.button == button) it.copy(visible = visible) else it })

    fun moved(fromIndex: Int, toIndex: Int): PlayerToolbarLayout {
        if (fromIndex !in entries.indices || toIndex !in entries.indices || fromIndex == toIndex) return this
        val updated = entries.toMutableList()
        updated.add(toIndex, updated.removeAt(fromIndex))
        return PlayerToolbarLayout(updated)
    }

    fun encode(): String = entries.joinToString(",") { entry ->
        "${entry.button.storageId}:${if (entry.visible) "v" else "h"}"
    }

    companion object {
        /** Same as the bar before it was customizable: the first five buttons shown, the rest behind the arrow. */
        val Default = PlayerToolbarLayout(
            PlayerToolbarButton.entries.mapIndexed { index, button ->
                PlayerToolbarEntry(button, visible = button.topBar || index < 5)
            },
        )

        fun decode(value: String?): PlayerToolbarLayout {
            if (value.isNullOrBlank()) return Default
            val parsed = value.split(',').mapNotNull { token ->
                val button = PlayerToolbarButton.fromStorageId(token.substringBefore(':').trim())
                    ?: return@mapNotNull null
                PlayerToolbarEntry(button, visible = token.substringAfter(':', "v").trim() != "h")
            }.distinctBy { it.button }
            val missing = Default.entries.filter { default -> parsed.none { it.button == default.button } }
            return PlayerToolbarLayout(parsed + missing)
        }
    }
}
