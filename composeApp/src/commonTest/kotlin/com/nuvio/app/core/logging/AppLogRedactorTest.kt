package com.nuvio.app.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogRedactorTest {
    @Test
    fun masksBearerTokensAndAuthorizationHeaders() {
        val redacted = AppLogRedactor.redact("Authorization: Bearer abc.DEF-123_xyz")
        assertFalse("abc.DEF-123_xyz" in redacted)
        assertTrue("***" in redacted)
    }

    @Test
    fun masksSecretQueryParameters() {
        val redacted = AppLogRedactor.redact("GET https://api.example.com/v1/items?apikey=SECRET123&page=2")
        assertEquals("GET https://api.example.com/v1/items?apikey=***&page=2", redacted)
    }

    @Test
    fun masksSecretJsonFields() {
        val redacted = AppLogRedactor.redact("""{"access_token":"tok-1","refresh_token": "tok-2","user":"bob"}""")
        assertFalse("tok-1" in redacted)
        assertFalse("tok-2" in redacted)
        assertTrue("\"user\":\"bob\"" in redacted)
    }

    @Test
    fun masksLongAddonConfigurationPathSegments() {
        val config = "eyJkZWJyaWRTZXJ2aWNlIjoicmVhbGRlYnJpZCIsImRlYnJpZEFwaUtleSI6IlNFQ1JFVCJ9"
        val redacted = AppLogRedactor.redact("Fetching https://addon.example.com/$config/manifest.json")
        assertEquals("Fetching https://addon.example.com/***/manifest.json", redacted)
    }

    @Test
    fun keepsInfoHashesAndOrdinaryText() {
        val text = "Resolved magnet 0123456789abcdef0123456789abcdef01234567 for tt1234567 (key frames: 12)"
        assertEquals(text, AppLogRedactor.redact(text))
    }
}
