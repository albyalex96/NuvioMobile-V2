package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal actual object SubtitleFontStorage {
    actual fun defaultFamily(): FontFamily = FontFamily.SansSerif

    private fun file(name: String): File {
        require(name == "fonts.json" || name.matches(Regex("custom-[a-f0-9-]{36}")))
        return DesktopStorage.rootDir.resolve("subtitle-fonts").toFile().resolve(name)
    }

    actual fun read(name: String): ByteArray? = file(name).takeIf {
        it.isFile && it.length() <= MaxSubtitleFontBytes
    }?.readBytes()

    actual fun write(name: String, bytes: ByteArray) {
        val target = file(name)
        target.parentFile?.mkdirs()
        val temporary = File(target.path + ".tmp")
        temporary.writeBytes(bytes)
        // renameTo does not replace an existing file on Windows: drop it first.
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target))
        }
    }

    actual fun remove(name: String) {
        file(name).delete()
    }

    actual fun fontFamily(id: String): FontFamily? = file(id).takeIf { it.isFile }?.let {
        FontFamily(Font(file = it))
    }
}

@Composable
internal actual fun rememberSubtitleFontImporter(onResult: (Result<SubtitleFont>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latestResult = rememberUpdatedState(onResult)
    return {
        scope.launch {
            val chosen = withContext(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("Fonts (.ttf, .otf)", "ttf", "otf")
                    isAcceptAllFileFilterUsed = false
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            } ?: return@launch
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    require(chosen.length() <= MaxSubtitleFontBytes)
                    SubtitleFonts.import(chosen.name, chosen.readBytes())
                }
            }
            latestResult.value(result)
        }
    }
}
