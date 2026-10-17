package com.nuvio.app.features.streams.parser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.streams.StreamItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_parser_original_description
import nuvio.composeapp.generated.resources.stream_parser_original_description_close
import nuvio.composeapp.generated.resources.stream_parser_original_description_copied
import nuvio.composeapp.generated.resources.stream_parser_original_description_copy
import nuvio.composeapp.generated.resources.stream_parser_original_description_empty
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("StreamParser")

/**
 * The raw text the addon sent for a stream (name, title, description), which the Stream Parser
 * card replaces with badges. Selectable and copyable.
 */
@Composable
internal fun StreamOriginalDescriptionDialog(
    stream: StreamItem,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val clipboardManager = LocalClipboardManager.current
    val copiedText = stringResource(Res.string.stream_parser_original_description_copied)
    val name = stream.name?.trim()?.takeIf { it.isNotEmpty() }
    val body = remember(stream) { stream.originalDescriptionText() }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.stream_parser_original_description),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SelectionContainer {
                Column {
                    if (name != null) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.colors.textPrimary,
                        )
                    }
                    Text(
                        text = body ?: stringResource(Res.string.stream_parser_original_description_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (body != null) tokens.colors.textSecondary else tokens.colors.textMuted,
                    )
                }
            }
        }
        DialogButtons {
            val fullText = listOfNotNull(name, body).joinToString("\n")
            if (fullText.isNotEmpty()) {
                DialogButton(
                    text = stringResource(Res.string.stream_parser_original_description_copy),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(fullText))
                        NuvioToastController.show(copiedText)
                        log.d { "Original description copied (${fullText.length} chars)" }
                    },
                )
            }
            DialogButton(
                text = stringResource(Res.string.stream_parser_original_description_close),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

/** Title and description as sent by the addon, without repeating identical lines. */
private fun StreamItem.originalDescriptionText(): String? {
    val name = name?.trim()
    return listOfNotNull(title, description)
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != name }
        .distinct()
        .joinToString("\n\n")
        .takeIf { it.isNotEmpty() }
}
