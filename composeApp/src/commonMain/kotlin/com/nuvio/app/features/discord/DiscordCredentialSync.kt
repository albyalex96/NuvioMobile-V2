package com.nuvio.app.features.discord

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.sync.SupabaseProviderCredential
import com.nuvio.app.core.sync.putSyncOriginClientId
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Syncs the Discord token with the profile through the provider credentials RPCs, as provider
 * `discord`, in calls of its own: the regular credential push (`ProviderCredentialSync`) never
 * carries it, so if the server does not accept this provider only the Discord sync fails (logged
 * as a warning) and the other credentials keep syncing. The push upserts the listed providers only
 * (NuvioTV pushes a smaller set without wiping the others), so sending `discord` alone is safe.
 *
 * Rules: a web login pushes the token, an explicit logout pushes an empty token (the other
 * devices of the profile log out on their next pull), a token rejected by Discord is only dropped
 * on this device. If the server has no row: a token never synced from this device seeds it, a
 * token already synced means it was removed elsewhere (the server may delete empty credentials).
 */
internal object DiscordCredentialSync {
    private const val PROVIDER = "discord"
    private const val TOKEN_FIELD = "token"

    private val log = Logger.withTag(DISCORD_RPC_LOG_TAG)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /** Pushes [token] (empty = logged out) for the active profile; no-op when not signed in. */
    fun requestPush(token: String) {
        if (!DiscordRpcPlatform.isSupported) return
        val profileId = activeProfileIdIfSignedIn() ?: run {
            log.d { "Discord token not synced: no signed-in account" }
            return
        }
        scope.launch {
            mutex.withLock { push(profileId, token) }
        }
    }

    /** Pulls the profile's Discord token. Never throws: a failure only means no Discord sync. */
    suspend fun pull(profileId: Int) {
        if (!DiscordRpcPlatform.isSupported) return
        mutex.withLock {
            if (activeProfileIdIfSignedIn() != profileId) return
            val rows = try {
                SupabaseProvider.client.postgrest
                    .rpc("sync_pull_provider_credentials", buildJsonObject { put("p_profile_id", profileId) })
                    .decodeList<SupabaseProviderCredential>()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Discord token pull failed for profile $profileId" }
                return
            }
            if (activeProfileIdIfSignedIn() != profileId) return
            val row = rows.firstOrNull { it.provider.equals(PROVIDER, ignoreCase = true) }
            val remoteToken = (row?.credentialJson?.get(TOKEN_FIELD) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val localToken = DiscordRpcRepository.currentToken()
            log.d { "Discord token pulled for profile $profileId (row=${row != null}, present=${remoteToken.isNotEmpty()})" }
            when {
                remoteToken.isNotEmpty() -> {
                    if (remoteToken == localToken) {
                        DiscordRpcStorage.saveSyncedTokenFingerprint(tokenFingerprint(remoteToken))
                    }
                    DiscordRpcRepository.applyRemoteToken(remoteToken)
                }
                localToken == null -> Unit
                // Already synced once and now gone or empty: logged out on another device.
                DiscordRpcStorage.loadSyncedTokenFingerprint() == tokenFingerprint(localToken) -> {
                    log.i { "Discord token removed from profile $profileId on another device" }
                    DiscordRpcRepository.applyRemoteToken("")
                }
                else -> {
                    log.i { "No Discord token on the server for profile $profileId, seeding it from this device" }
                    push(profileId, localToken)
                }
            }
        }
    }

    private suspend fun push(profileId: Int, token: String) {
        if (activeProfileIdIfSignedIn() != profileId) return
        val params = buildJsonObject {
            put("p_profile_id", profileId)
            put("p_credentials", buildJsonArray {
                add(buildJsonObject {
                    put("provider", PROVIDER)
                    put("credential_json", buildJsonObject { put(TOKEN_FIELD, token.trim()) })
                })
            })
            putSyncOriginClientId()
        }
        try {
            SupabaseProvider.client.postgrest.rpc("sync_push_provider_credentials", params)
            if (activeProfileIdIfSignedIn() == profileId) {
                DiscordRpcStorage.saveSyncedTokenFingerprint(token.trim().takeIf { it.isNotEmpty() }?.let(::tokenFingerprint))
            }
            log.i {
                if (token.isBlank()) {
                    "Discord logout synced with profile $profileId"
                } else {
                    "Discord token synced with profile $profileId"
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) {
                "Server rejected the Discord token sync for profile $profileId " +
                    "(provider '$PROVIDER' may not be allowed by sync_push_provider_credentials)"
            }
        }
    }

    private fun activeProfileIdIfSignedIn(): Int? {
        val state = AuthRepository.state.value as? AuthState.Authenticated ?: return null
        if (state.isAnonymous) return null
        return ProfileRepository.activeProfileId
    }
}
