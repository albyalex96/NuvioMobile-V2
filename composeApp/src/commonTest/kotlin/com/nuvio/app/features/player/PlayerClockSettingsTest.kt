package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerClockSettingsTest {
    @Test
    fun formats24HourTimeWithLeadingZeros() {
        assertEquals("00:05", formatPlayerClockTime(0, 5, use24Hour = true))
        assertEquals("14:30", formatPlayerClockTime(14, 30, use24Hour = true))
    }

    @Test
    fun formats12HourTimeWithAmPm() {
        assertEquals("12:05 AM", formatPlayerClockTime(0, 5, use24Hour = false))
        assertEquals("9:00 AM", formatPlayerClockTime(9, 0, use24Hour = false))
        assertEquals("12:00 PM", formatPlayerClockTime(12, 0, use24Hour = false))
        assertEquals("2:30 PM", formatPlayerClockTime(14, 30, use24Hour = false))
    }

    @Test
    fun settingsRoundTripThroughJson() {
        val settings = PlayerClockSettings(enabled = true, use24Hour = false, position = PlayerClockPosition.BottomEnd)
        assertEquals(settings, PlayerClockSettings.decode(settings.encode()))
    }

    @Test
    fun invalidJsonDecodesToNull() {
        assertNull(PlayerClockSettings.decode(null))
        assertNull(PlayerClockSettings.decode("not json"))
    }
}
