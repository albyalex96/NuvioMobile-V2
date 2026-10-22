package com.nuvio.app.features.cloudstream

object CloudStreamCompatibilityResolver {
    fun resolve(
        metadata: CloudStreamPluginMetadata,
        supportsAndroidDex: Boolean = false,
    ): CloudStreamCompatibility = if (supportsAndroidDex) {
        CloudStreamCompatibility(
            runtimeKind = CloudStreamRuntimeKind.AndroidDex,
            platformSupport = CloudStreamPlatformSupport.AndroidOnly,
            reason = "Android full builds run this standard CloudStream .cs3 package with the embedded runtime.",
        )
    } else {
        CloudStreamCompatibility(
            runtimeKind = CloudStreamRuntimeKind.UnsupportedAndroidDex,
            platformSupport = CloudStreamPlatformSupport.Unsupported,
            reason = "This standard .cs3 package contains Android DEX code, which cannot run on this platform.",
        )
    }
}

fun sortCloudStreamEpisodes(episodes: List<CloudStreamEpisode>): List<CloudStreamEpisode> =
    episodes.sortedWith(
        compareBy<CloudStreamEpisode>(
            { it.season ?: Int.MAX_VALUE },
            { it.episode ?: Int.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )
