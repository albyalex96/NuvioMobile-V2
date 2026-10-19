package com.nuvio.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvRowCardFocusBorder
import com.nuvio.tv.ui.tvFocusable

// Settings building blocks of NuvioTV ("classic" settings style): pill rows on the page
// background, focus ring without scaling, toggle pill, preset chips and stepped sliders.
private val RowShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)
private val CardShape = RoundedCornerShape(16.dp)
private val RowInset = 18.dp

@Composable
private fun Modifier.settingsRowSurface(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onFocusChange: (Boolean) -> Unit = {},
): Modifier = this
    .tvFocusable(
        shape = RowShape,
        onClick = onClick,
        focusedScale = 1f,
        borderWidth = TvRowCardFocusBorder,
        enabled = enabled,
        onFocusChange = onFocusChange,
    )
    .background(MaterialTheme.nuvio.colors.background)

/** Header row of a collapsible section (NuvioTV `SettingsCollapsibleSection`). */
@Composable
fun TvSettingsCollapsibleSection(
    title: String,
    description: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvSettingsActionRow(
            title = title,
            subtitle = description,
            value = stringResource(if (expanded) R.string.tv_layout_open else R.string.tv_layout_closed),
            leadingIcon = icon,
            trailingIcon = if (expanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onToggle,
        )
        if (expanded) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.nuvio.colors.surfaceCard.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun TvSettingsActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    value: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .settingsRowSurface(onClick = onClick)
            .padding(horizontal = RowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            SettingsIconChip(leadingIcon)
            Spacer(modifier = Modifier.width(16.dp))
        }
        RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        if (!value.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(imageVector = trailingIcon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Icon of a settings row in the style of the shared settings pages (`SettingsNavigationRow`):
 * accent icon on a tinted rounded square.
 */
@Composable
private fun SettingsIconChip(icon: ImageVector) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.accent.copy(alpha = tokens.opacity.pressed)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(24.dp))
    }
}

/** Switch row (NuvioTV `SettingsToggleRow`): OK toggles, the pill shows the state. */
@Composable
fun TvSettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .settingsRowSurface(onClick = { onCheckedChange(!checked) }, enabled = enabled)
            .padding(horizontal = RowInset, vertical = 12.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        TogglePill(checked = checked)
    }
}

@Composable
private fun TogglePill(checked: Boolean) {
    val colors = MaterialTheme.nuvio.colors
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(24.dp)
            .clip(PillShape)
            .background(if (checked) colors.accent else colors.textMuted.copy(alpha = 0.35f))
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else Color.White),
        )
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.nuvio.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nuvio.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

class TvPresetOption<T>(val label: String, val value: T)

/**
 * "Title (selected preset)" and a row of preset chips (NuvioTV `OptionRow`). Entering the row
 * with the D-pad lands on the last focused chip.
 */
@Composable
fun <T> TvSettingsOptionRow(
    title: String,
    selected: T,
    options: List<TvPresetOption<T>>,
    onSelected: (T) -> Unit,
) {
    val selectedLabel = options.firstOrNull { it.value == selected }?.label ?: stringResource(R.string.tv_layout_custom)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "$title ($selectedLabel)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.nuvio.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp),
        )
        val firstChip = remember { FocusRequester() }
        LazyRow(
            modifier = Modifier
                .focusRestorer(firstChip)
                .focusGroup(),
            contentPadding = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(options, key = { _, option -> option.label }) { index, option ->
                TvSettingsChoiceChip(
                    label = option.label,
                    selected = option.value == selected,
                    onClick = { onSelected(option.value) },
                    modifier = if (index == 0) Modifier.focusRequester(firstChip) else Modifier,
                )
            }
        }
    }
}

@Composable
fun TvSettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.nuvio.colors
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected || focused) colors.textPrimary else colors.textSecondary,
        modifier = modifier
            .tvFocusable(
                shape = PillShape,
                onClick = onClick,
                focusedScale = 1f,
                borderWidth = TvRowCardFocusBorder,
                onFocusChange = { focused = it },
            )
            .background(if (selected) colors.focusRing.copy(alpha = 0.2f) else colors.background)
            .then(if (selected && !focused) Modifier.border(1.dp, colors.focusRing, PillShape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Stepped value (NuvioTV `SliderSettingsItem`): left/right on the row change the value. */
@Composable
fun TvSettingsSliderRow(
    title: String,
    subtitle: String?,
    value: Int,
    valueText: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val decrease = { if (value > range.first) onValueChange(value - 1) }
    val increase = { if (value < range.last) onValueChange(value + 1) }
    val span = (range.last - range.first).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (rtl) increase() else decrease()
                        true
                    }
                    Key.DirectionRight -> {
                        if (rtl) decrease() else increase()
                        true
                    }
                    else -> false
                }
            }
            .settingsRowSurface(onClick = {})
            .padding(horizontal = RowInset, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = valueText, style = MaterialTheme.typography.bodyLarge, color = colors.accent)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton(icon = Icons.Filled.Remove, description = stringResource(R.string.tv_layout_decrease), onClick = decrease)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceCard),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((value - range.first).toFloat() / span)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.accent),
                )
            }
            StepButton(icon = Icons.Filled.Add, description = stringResource(R.string.tv_layout_increase), onClick = increase)
        }
    }
}

/** Touch target of the slider; the D-pad uses left/right on the row instead. */
@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.nuvio.colors.surfaceCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = MaterialTheme.nuvio.colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TvSettingsResetButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .tvFocusable(shape = PillShape, onClick = onClick, focusedScale = 1f, borderWidth = TvRowCardFocusBorder)
            .background(MaterialTheme.nuvio.colors.background)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            tint = MaterialTheme.nuvio.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.nuvio.colors.textPrimary)
    }
}

/** Small explanatory text inside a section (NuvioTV `SettingsNote`). */
@Composable
fun TvSettingsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
        color = MaterialTheme.nuvio.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset, vertical = 4.dp),
    )
}
