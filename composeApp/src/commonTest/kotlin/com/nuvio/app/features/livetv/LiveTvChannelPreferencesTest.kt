package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveTvChannelPreferencesTest {
    private fun channel(name: String, index: Int, group: String? = null) = LiveTvChannel(
        id = "live:${name.hashCode().toUInt().toString(16)}:$index",
        name = name,
        streamUrl = "https://example.com/$name.m3u8",
        group = group,
    )

    private val alpha = channel("Alpha", 0, "News")
    private val bravo = channel("Bravo", 1, "Sport")
    private val charlie = channel("Charlie", 2, "News")
    private val delta = channel("Delta", 3)
    private val all = listOf(delta, charlie, bravo, alpha)

    private fun key(channel: LiveTvChannel) = liveTvChannelPreferenceKey(channel)

    @Test
    fun preferenceKeyIgnoresPlaylistPosition() {
        assertEquals(key(alpha), key(alpha.copy(id = alpha.id.substringBeforeLast(':') + ":42")))
        assertEquals("xtream:15", liveTvChannelPreferenceKey(alpha.copy(id = "xtream:15")))
    }

    @Test
    fun defaultOrderIsAlphabetical() {
        assertEquals(listOf(alpha, bravo, charlie, delta), arrangeLiveTvChannels(all, LiveTvChannelPreferences()))
    }

    @Test
    fun customOrderPutsListedChannelsFirst() {
        val preferences = LiveTvChannelPreferences(channelOrder = listOf(key(delta), "live:missing"))
        assertEquals(listOf(delta, alpha, bravo, charlie), arrangeLiveTvChannels(all, preferences))
    }

    @Test
    fun moveStoresOnlyTheTouchedPrefix() {
        val arranged = arrangeLiveTvChannels(all, LiveTvChannelPreferences())
        val order = moveLiveTvChannelOrder(arranged, emptyList(), fromKey = key(charlie), toKey = key(alpha))
        assertEquals(listOf(key(charlie), key(alpha), key(bravo)), order)
        val rearranged = arrangeLiveTvChannels(all, LiveTvChannelPreferences(channelOrder = order))
        assertEquals(listOf(charlie, alpha, bravo, delta), rearranged)
    }

    @Test
    fun moveKeepsKeysOfMissingChannels() {
        val current = listOf(key(bravo), "live:offline")
        val arranged = arrangeLiveTvChannels(all, LiveTvChannelPreferences(channelOrder = current))
        val order = moveLiveTvChannelOrder(arranged, current, fromKey = key(alpha), toKey = key(bravo))
        assertEquals(listOf(key(alpha), key(bravo), "live:offline"), order)
    }

    @Test
    fun hiddenChannelsAndCategoriesAreFilteredFromDisplay() {
        val state = LiveTvUiState(
            channels = all,
            channelPreferences = LiveTvChannelPreferences(
                hiddenChannelKeys = setOf(key(bravo)),
                hiddenCategoryKeys = setOf(liveTvCategoryKey(" news ")),
            ),
        )
        assertEquals(listOf(delta), state.displayedChannels)
        assertEquals(4, state.arrangedChannels.size)
    }

    @Test
    fun preferencesRoundTripThroughJson() {
        val preferences = LiveTvChannelPreferences(
            hiddenChannelKeys = setOf("a"),
            hiddenCategoryKeys = setOf(""),
            channelOrder = listOf("b", "a"),
        )
        assertEquals(preferences, decodeLiveTvChannelPreferences(encodeLiveTvChannelPreferences(preferences)))
        assertTrue(decodeLiveTvChannelPreferences(encodeLiveTvChannelPreferences(LiveTvChannelPreferences()))!!.isEmpty)
    }
}
