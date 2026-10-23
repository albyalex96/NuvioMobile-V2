package com.nuvio.app.features.anime

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object AnimeTrackingStorage {
    private const val excludedProvidersKey = "anime_tracking_excluded_providers"

    actual fun loadExcludedProviders(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(excludedProvidersKey))

    actual fun saveExcludedProviders(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(excludedProvidersKey))
    }
}
