package com.nuvio.app.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocaleDisplayNamesTest {
    @Test
    fun countryCodesAreLocalized() {
        assertEquals("Giappone", formatCountriesForDisplay("JP", "it-IT"))
        assertEquals("Japan, United States", formatCountriesForDisplay("JP, us", "en-US"))
    }

    @Test
    fun unknownCountryCodesAndNamesAreKept() {
        assertEquals("ZZ, Giappone", formatCountriesForDisplay("ZZ,JP", "it-IT"))
        assertEquals("QQ", formatCountriesForDisplay("QQ", "it-IT"))
        assertEquals("United States", formatCountriesForDisplay("United States", "it-IT"))
    }

    @Test
    fun languageCodesAreLocalizedAndCapitalized() {
        assertEquals("Giapponese", formatLanguageForDisplay("ja", "it-IT"))
        assertEquals("Japanese", formatLanguageForDisplay("ja", "en-US"))
    }

    @Test
    fun unknownLanguagesFallBackToTheCode() {
        assertEquals("QQ", formatLanguageForDisplay("qq", "it-IT"))
        assertEquals("XX", formatLanguageForDisplay("xx", "it-IT"))
        assertNull(languageDisplayName("und", "it-IT"))
        assertEquals("English", formatLanguageForDisplay("English", "it-IT"))
    }
}
