package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.core.ui.AnimatedArtworkImage
import com.nuvio.app.core.ui.AnimatedArtworkMode

/**
 * Like Android, every animated image on a collection card plays (cover image or focus GIF):
 * Skia has no built-in GIF playback, so the request always asks SkiaGifDecoder for all frames.
 * Static images are still decoded by Coil's default decoder.
 */
@Composable
internal actual fun CollectionCardRemoteImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    animateIfPossible: Boolean,
) {
    val context = LocalPlatformContext.current
    val request: ImageRequest = remember(context, imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCacheKey("home-collection:$imageUrl")
            .diskCacheKey(imageUrl)
            .build()
    }

    AnimatedArtworkImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        mode = AnimatedArtworkMode.Always,
    )
}
