package com.nuvio.app.features.player

/** Buttons of the bottom player bar; the order here is the default order. */
enum class PlayerToolbarButton(val storageId: String) {
    NEXT_EPISODE("next_episode"),
    SUBTITLES("subtitles"),
    AUDIO("audio"),
    SOURCES("sources"),
    EPISODES("episodes"),
    CHAPTERS("chapters"),
    LIVE_CHANNELS("live_channels"),
    SWITCH_ENGINE("switch_engine"),
    SPEED("speed"),
    RESIZE("resize"),
    EXTERNAL_PLAYER("external_player"),
    STREAM_INFO("stream_info"),
    VIDEO_SETTINGS("video_settings"),
    SUBMIT_INTRO("submit_intro"),
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
    val visibleButtons: List<PlayerToolbarButton>
        get() = entries.filter { it.visible }.map { it.button }

    val hiddenButtons: List<PlayerToolbarButton>
        get() = entries.filterNot { it.visible }.map { it.button }

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
                PlayerToolbarEntry(button, visible = index < 5)
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
