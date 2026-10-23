package com.nuvio.app.features.anime

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AnimeTrackingStorage {
    private const val preferencesName = "nuvio_anime_tracking"
    private const val excludedProvidersKey = "excluded_providers"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadExcludedProviders(): String? =
        preferences?.getString(ProfileScopedKey.of(excludedProvidersKey), null)

    actual fun saveExcludedProviders(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(excludedProvidersKey), payload)
            ?.apply()
    }
}
