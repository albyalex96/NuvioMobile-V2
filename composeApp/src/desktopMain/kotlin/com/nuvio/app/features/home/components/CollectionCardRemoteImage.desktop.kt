package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.core.ui.AnimatedArtworkImage
import com.nuvio.app.core.ui.AnimatedArtworkMode

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

    if (animateIfPossible) {
        // Skia has no built-in GIF playback: AnimatedArtworkImage decodes every frame and plays them.
        AnimatedArtworkImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            mode = AnimatedArtworkMode.Always,
        )
    } else {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
