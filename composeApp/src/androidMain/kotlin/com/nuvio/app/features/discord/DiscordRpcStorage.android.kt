package com.nuvio.app.features.discord

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.ProfileScopedKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal actual object DiscordRpcStorage {
    private const val preferencesName = "nuvio_discord_rpc"
    private const val settingsKey = "settings_payload"
    private const val userKey = "user_payload"
    private const val tokenKey = "token"
    private const val rejectedTokenKey = "rejected_token_fingerprint"
    private const val syncedTokenKey = "synced_token_fingerprint"
    private const val profileSyncTokenKey = "profile_sync_token"
    private const val keyAlias = "com.nuvio.media.discord.token.v1"

    private val log = Logger.withTag(DISCORD_RPC_LOG_TAG)
    private var preferences: SharedPreferences? = null
    private val lock = Any()

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadSettingsPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(settingsKey), null)

    actual fun saveSettingsPayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(settingsKey), payload)
            ?.apply()
    }

    actual fun loadUserPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(userKey), null)

    actual fun saveUserPayload(payload: String?) {
        saveString(ProfileScopedKey.of(userKey), payload)
    }

    actual fun loadRejectedTokenFingerprint(): String? =
        preferences?.getString(ProfileScopedKey.of(rejectedTokenKey), null)

    actual fun saveRejectedTokenFingerprint(fingerprint: String?) {
        saveString(ProfileScopedKey.of(rejectedTokenKey), fingerprint)
    }

    actual fun loadSyncedTokenFingerprint(): String? =
        preferences?.getString(ProfileScopedKey.of(syncedTokenKey), null)

    actual fun saveSyncedTokenFingerprint(fingerprint: String?) {
        saveString(ProfileScopedKey.of(syncedTokenKey), fingerprint)
    }

    actual fun loadToken(): String? = loadEncrypted(tokenKey)

    actual fun saveToken(token: String?) {
        saveEncrypted(tokenKey, token?.takeIf { it.isNotBlank() })
    }

    actual fun loadProfileSyncToken(): String? = loadEncrypted(profileSyncTokenKey)

    actual fun saveProfileSyncToken(token: String?) {
        saveEncrypted(profileSyncTokenKey, token?.trim())
    }

    // The account cleaner (`PlatformLocalAccountDataCleaner`) already wipes the whole preferences file.
    actual fun clearProfileSyncTokens() = Unit

    private fun loadEncrypted(keyBase: String): String? = synchronized(lock) {
        val prefs = preferences ?: return@synchronized null
        val key = ProfileScopedKey.of(keyBase)
        val value = prefs.getString(key, null) ?: return@synchronized null
        try {
            val parts = value.split('.', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decode(parts[0])))
            cipher.updateAAD(aad(keyBase))
            cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            log.w(error) { "Stored Discord value '$keyBase' unreadable, removing it" }
            prefs.edit().remove(key).commit()
            null
        }
    }

    /** Encrypts [value] (an empty string is kept as such); null removes it. */
    private fun saveEncrypted(keyBase: String, value: String?) {
        synchronized(lock) {
            val prefs = preferences ?: return
            val key = ProfileScopedKey.of(keyBase)
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(key)
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                cipher.updateAAD(aad(keyBase))
                val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                editor.putString(key, "${encode(cipher.iv)}.${encode(encrypted)}")
            }
            editor.commit()
        }
    }

    private fun saveString(key: String, value: String?) {
        val editor = preferences?.edit() ?: return
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun aad(keyBase: String): ByteArray = "$keyAlias:$keyBase".toByteArray(Charsets.UTF_8)

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
