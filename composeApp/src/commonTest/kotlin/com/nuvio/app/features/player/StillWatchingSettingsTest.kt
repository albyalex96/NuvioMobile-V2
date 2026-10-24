package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StillWatchingSettingsTest {
    @Test
    fun roundTripsThroughTheSyncedJson() {
        val settings = StillWatchingSettings(
            promptEnabled = true,
            episodeCount = 5,
            nightOnly = true,
            liveTvEnabled = true,
            liveTvMinutes = 90,
        )
        assertEquals(settings, StillWatchingSettings.decode(settings.encode()))
    }

    @Test
    fun decodingClampsOutOfRangeValues() {
        val decoded = StillWatchingSettings.decode("""{"episodeCount":99,"liveTvMinutes":100}""")
        assertEquals(STILL_WATCHING_EPISODE_COUNT_RANGE.last, decoded?.episodeCount)
        assertEquals(105, decoded?.liveTvMinutes)
    }

    @Test
    fun missingFieldsUseDefaultsAndUnknownOnesAreIgnored() {
        assertEquals(
            StillWatchingSettings(promptEnabled = true),
            StillWatchingSettings.decode("""{"promptEnabled":true,"futureOption":1}"""),
        )
    }

    @Test
    fun blankOrBrokenPayloadsDecodeToNull() {
        assertNull(StillWatchingSettings.decode(null))
        assertNull(StillWatchingSettings.decode(" "))
        assertNull(StillWatchingSettings.decode("not json"))
    }

    @Test
    fun everyDeviceClassStartsWithItsOwnDefaults() {
        assertEquals(SubtitleSizeFormFactor.entries.toSet(), defaultStillWatchingSettings().keys)
    }
}
