package com.nuvio.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PosterZoomOverlayAction
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.tv.TvPosterMenu
import com.nuvio.app.tv.tvFocusOnAppear

private val log = Logger.withTag("TvPosterMenu")
private val PanelShape = RoundedCornerShape(24.dp)
private val RowShape = RoundedCornerShape(14.dp)
private val MenuConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private val MenuActionsMaxHeight = 330.dp

/**
 * Options of a long-pressed card (OK held or Menu): poster, title and a list of actions with
 * Material icons. The first action takes the focus; Back closes the menu and the focus returns
 * to the card.
 */
@Composable
fun TvPosterMenuDialog(menu: TvPosterMenu) {
    Dialog(
        onDismissRequest = menu.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The menu opens while OK is still held on the card: its repeats and its release must not
        // select an action. OK works only after a new press made inside the menu.
        var confirmArmed by remember { mutableStateOf(false) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    if (event.key !in MenuConfirmKeys) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        confirmArmed = true
                    }
                    if (!confirmArmed) log.d { "Ignoring OK still held from the card" }
                    !confirmArmed
                }
                .width(640.dp)
                .clip(PanelShape)
                .background(MaterialTheme.nuvio.colors.surface, PanelShape)
                .padding(28.dp),
        ) {
            // Menus without artwork (e.g. a stream) use the whole width for the actions.
            if (!menu.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.nuvio.colors.surfaceCard),
                ) {
                    if (!menu.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = menu.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = menu.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!menu.subtitle.isNullOrBlank()) {
                    Text(
                        text = menu.subtitle.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.nuvio.colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                // Long menus (e.g. continue watching) scroll instead of leaving the 540dp TV screen.
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .heightIn(max = MenuActionsMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    menu.actions.forEachIndexed { index, action ->
                        MenuActionRow(
                            action = action,
                            focusFirst = index == 0,
                            onClick = {
                                log.d { "Card action: ${action.label}" }
                                menu.onDismiss()
                                action.onSelected()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    action: PosterZoomOverlayAction,
    focusFirst: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val danger = MaterialTheme.nuvio.colors.danger
    val content = when {
        focused -> Color.Black
        action.isDestructive -> danger
        else -> MaterialTheme.nuvio.colors.textPrimary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusFirst) Modifier.tvFocusOnAppear() else Modifier)
            .tvFocusable(
                shape = RowShape,
                onClick = onClick,
                focusedScale = 1.02f,
                borderWidth = 0.dp,
                onFocusChange = { focused = it },
            )
            .background(if (focused) Color.White else Color.Transparent, RowShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
