package com.nuvio.app.core.format

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode

internal actual fun platformCountryDisplayName(code: String, localeTag: String): String? =
    NSLocale(localeIdentifier = localeTag).displayNameForKey(NSLocaleCountryCode, code)

internal actual fun platformLanguageDisplayName(code: String, localeTag: String): String? =
    NSLocale(localeIdentifier = localeTag).displayNameForKey(NSLocaleLanguageCode, code)
