package com.nuvio.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.primaryActionContentColor
import com.nuvio.app.core.ui.primaryActionSurface

// Buttons of the detail page hero, also used by the expanded home hero.
private val ActionButtonSize = 48.dp
private val PlayButtonShape = RoundedCornerShape(32.dp)

/** White pill like NuvioTV's `PlayButton`; OK held (or Menu) opens the manual source choice. */
@Composable
fun TvPlayButton(
    text: String,
    enabled: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = primaryActionContentColor(enabled)
    val resume = progress != null && progress > 0f
    Box(
        modifier = modifier
            .focusProperties { up = FocusRequester.Cancel }
            .tvFocusable(
                shape = PlayButtonShape,
                onClick = onClick,
                onLongClick = onLongClick,
                focusedScale = 1.05f,
                enabled = enabled,
                onFocusChange = { focused = it },
            )
            // Same gradient as the phone's main buttons; the accent glow lights up with the focus.
            .primaryActionSurface(shape = PlayButtonShape, enabled = enabled, glow = focused),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp),
        ) {
            Icon(
                imageVector = if (resume) Icons.Rounded.PlayCircle else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 22.dp, vertical = 5.dp)
                    .width(64.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.nuvio.colors.accent),
                )
            }
        }
    }
}

/** Round icon button of the hero (NuvioTV `ActionIconButton`). */
@Composable
fun TvActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.nuvio.colors.accent
    val container = when {
        focused -> accent
        selected -> Color.White
        else -> MaterialTheme.nuvio.colors.surfaceCard
    }
    val content = when {
        focused -> MaterialTheme.nuvio.colors.onAccent
        selected -> Color.Black
        else -> MaterialTheme.nuvio.colors.textPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ActionButtonSize)
            .focusProperties { up = FocusRequester.Cancel }
            .tvFocusable(
                shape = CircleShape,
                onClick = onClick,
                focusedScale = 1.08f,
                onFocusChange = { focused = it },
            )
            .background(container, CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(22.dp))
    }
}
