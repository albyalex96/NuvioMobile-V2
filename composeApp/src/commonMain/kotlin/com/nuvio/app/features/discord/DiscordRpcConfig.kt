package com.nuvio.app.features.discord

/** Kermit tag of the whole feature (category in the in-app logger). */
internal const val DISCORD_RPC_LOG_TAG = "DiscordRPC"

internal object DiscordRpcConfig {
    const val APP_NAME = "Nuvio"
    const val APPLICATION_ID = "962990036020756480"
    const val DISCORD_API_URL = "https://discord.com/api/v10"
    const val LOGIN_URL = "https://discord.com/login"
    const val MAX_TEXT_LENGTH = 128
    const val MAX_BUTTON_LABEL_LENGTH = 32
    const val MAX_ASSET_KEY_LENGTH = 256
    const val MAX_ARTWORK_URL_LENGTH = 200

    /**
     * Optional image proxy (Kizzy-style worker) used when Discord's external-assets API fails.
     * Set with `DISCORD_IMAGE_PROXY_URL` in local.properties; empty disables the fallback.
     */
    val imageProxyUrl: String
        get() = DiscordRpcBuildConfig.IMAGE_PROXY_URL.trim().trimEnd('/')

    fun metahubPosterUrl(imdbId: String): String = "https://images.metahub.space/poster/medium/$imdbId/img"
    val APPLICATION_ID_LENGTH_RANGE = 17..20

    const val PLAY_ASSET = "app-assets/$APPLICATION_ID/1300361266212241430.png"
    const val PAUSE_ASSET = "app-assets/$APPLICATION_ID/1300361619490209802.png"
}
