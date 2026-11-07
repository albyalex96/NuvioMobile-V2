package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Web: only the built-in fonts; importing custom fonts is not supported. */
internal actual object SubtitleFontStorage {
    actual fun defaultFamily(): FontFamily = FontFamily.SansSerif
    actual fun read(name: String): ByteArray? = null
    actual fun write(name: String, bytes: ByteArray) = Unit
    actual fun remove(name: String) = Unit
    actual fun fontFamily(id: String): FontFamily? = null
}

@Composable
internal actual fun rememberSubtitleFontImporter(onResult: (Result<SubtitleFont>) -> Unit): () -> Unit = {}
