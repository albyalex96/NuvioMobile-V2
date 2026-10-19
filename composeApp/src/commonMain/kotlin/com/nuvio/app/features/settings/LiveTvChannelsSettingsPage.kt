package com.nuvio.app.features.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvChannelPreferences
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.LiveTvUiState
import com.nuvio.app.features.livetv.liveTvCategoryKey
import com.nuvio.app.features.livetv.liveTvChannelPreferenceKey
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.live_tv_categories_apply_hide
import nuvio.composeapp.generated.resources.live_tv_categories_apply_show
import nuvio.composeapp.generated.resources.live_tv_categories_channel_count
import nuvio.composeapp.generated.resources.live_tv_categories_search_placeholder
import nuvio.composeapp.generated.resources.live_tv_categories_select_all
import nuvio.composeapp.generated.resources.live_tv_categories_sheet_empty_hide
import nuvio.composeapp.generated.resources.live_tv_categories_sheet_empty_show
import nuvio.composeapp.generated.resources.live_tv_categories_sheet_hide_hint
import nuvio.composeapp.generated.resources.live_tv_categories_sheet_show_hint
import nuvio.composeapp.generated.resources.live_tv_channels_category_hidden
import nuvio.composeapp.generated.resources.live_tv_channels_deselect_all
import nuvio.composeapp.generated.resources.live_tv_channels_empty
import nuvio.composeapp.generated.resources.live_tv_channels_hide_categories
import nuvio.composeapp.generated.resources.live_tv_channels_hint
import nuvio.composeapp.generated.resources.live_tv_channels_hint_tv
import nuvio.composeapp.generated.resources.live_tv_channels_loading
import nuvio.composeapp.generated.resources.live_tv_channels_move_down
import nuvio.composeapp.generated.resources.live_tv_channels_move_up
import nuvio.composeapp.generated.resources.live_tv_channels_no_results
import nuvio.composeapp.generated.resources.live_tv_channels_reorder
import nuvio.composeapp.generated.resources.live_tv_channels_reset_order
import nuvio.composeapp.generated.resources.live_tv_channels_select_all
import nuvio.composeapp.generated.resources.live_tv_channels_show_categories
import nuvio.composeapp.generated.resources.live_tv_channels_summary
import nuvio.composeapp.generated.resources.live_tv_group_uncategorized
import nuvio.composeapp.generated.resources.live_tv_search_placeholder
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val CHANNEL_LIST_WINDOW_HEIGHT_FRACTION = 0.7f
private val ChannelListMinHeight = 320.dp
private val ChannelListMaxHeight = 1000.dp

/** Settings → Integrations → Live TV → Manage channels: visibility, categories and order. */
internal fun LazyListScope.liveTvChannelsSettingsContent(
    isTablet: Boolean,
    uiState: LiveTvUiState,
) {
    item(key = "live-tv-channels-manager") {
        LiveTvChannelsManager(isTablet = isTablet, uiState = uiState)
    }
}

private enum class LiveTvCategorySheetMode { Show, Hide }

private data class LiveTvCategoryInfo(
    val key: String,
    val label: String,
    val channelCount: Int,
)

@Composable
private fun LiveTvChannelsManager(
    isTablet: Boolean,
    uiState: LiveTvUiState,
) {
    LaunchedEffect(Unit) { LiveTvRepository.ensureChannelsLoaded() }
    val tokens = MaterialTheme.nuvio
    val isTv = TvAppRuntime.isTvApp
    val preferences = uiState.channelPreferences
    val arranged = uiState.arrangedChannels
    val uncategorizedLabel = stringResource(Res.string.live_tv_group_uncategorized)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categorySheetMode by remember { mutableStateOf<LiveTvCategorySheetMode?>(null) }

    val filtered = remember(arranged, searchQuery, uncategorizedLabel) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            arranged
        } else {
            arranged.filter { channel ->
                channel.name.lowercase().contains(query) ||
                    (channel.group?.trim()?.takeIf(String::isNotBlank) ?: uncategorizedLabel).lowercase().contains(query) ||
                    channel.playlistName.orEmpty().lowercase().contains(query)
            }
        }
    }
    val categories = remember(uiState.channels, uncategorizedLabel) {
        uiState.channels
            .groupBy { liveTvCategoryKey(it.group) }
            .map { (key, channels) ->
                LiveTvCategoryInfo(
                    key = key,
                    label = channels.first().group?.trim()?.takeIf(String::isNotBlank) ?: uncategorizedLabel,
                    channelCount = channels.size,
                )
            }
            .sortedBy { it.label.lowercase() }
    }
    val visibleCount = uiState.displayedChannels.size
    val hiddenCategories = categories.filter { it.key in preferences.hiddenCategoryKeys }
    val shownCategories = categories.filterNot { it.key in preferences.hiddenCategoryKeys }

    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val listHeight = (windowHeight * CHANNEL_LIST_WINDOW_HEIGHT_FRACTION).coerceIn(ChannelListMinHeight, ChannelListMaxHeight)
    val horizontalPadding = if (isTablet) 20.dp else 16.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioInputField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = stringResource(Res.string.live_tv_search_placeholder),
            trailingContent = {
                if (searchQuery.isBlank()) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted)
                } else {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = null, tint = tokens.colors.textMuted)
                    }
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveTvChannelsChip(
                label = stringResource(Res.string.live_tv_channels_select_all),
                enabled = filtered.any { preferences.isChannelHidden(it) },
                onClick = { LiveTvRepository.setChannelsVisible(filtered, visible = true) },
            )
            LiveTvChannelsChip(
                label = stringResource(Res.string.live_tv_channels_deselect_all),
                enabled = filtered.any { !preferences.isChannelHidden(it) },
                onClick = { LiveTvRepository.setChannelsVisible(filtered, visible = false) },
            )
            LiveTvChannelsChip(
                label = stringResource(Res.string.live_tv_channels_show_categories),
                enabled = hiddenCategories.isNotEmpty(),
                trailingIcon = true,
                onClick = { categorySheetMode = LiveTvCategorySheetMode.Show },
            )
            LiveTvChannelsChip(
                label = stringResource(Res.string.live_tv_channels_hide_categories),
                enabled = shownCategories.isNotEmpty(),
                trailingIcon = true,
                onClick = { categorySheetMode = LiveTvCategorySheetMode.Hide },
            )
            if (preferences.channelOrder.isNotEmpty()) {
                LiveTvChannelsChip(
                    label = stringResource(Res.string.live_tv_channels_reset_order),
                    enabled = true,
                    onClick = LiveTvRepository::resetChannelOrder,
                )
            }
        }

        if (uiState.channels.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.live_tv_channels_summary, visibleCount, uiState.channels.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(if (isTv) Res.string.live_tv_channels_hint_tv else Res.string.live_tv_channels_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            }
        }

        SettingsGroup(isTablet = isTablet) {
            when {
                uiState.channels.isEmpty() -> Text(
                    text = stringResource(
                        if (uiState.isLoading) Res.string.live_tv_channels_loading else Res.string.live_tv_channels_empty,
                    ),
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                filtered.isEmpty() -> Text(
                    text = stringResource(Res.string.live_tv_channels_no_results),
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                else -> LiveTvReorderableChannelList(
                    channels = filtered,
                    preferences = preferences,
                    uncategorizedLabel = uncategorizedLabel,
                    isTablet = isTablet,
                    isTv = isTv,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listHeight),
                )
            }
        }
    }

    categorySheetMode?.let { mode ->
        LiveTvCategoriesSheet(
            mode = mode,
            categories = if (mode == LiveTvCategorySheetMode.Show) hiddenCategories else shownCategories,
            onApply = { keys ->
                LiveTvRepository.setCategoriesVisible(keys, visible = mode == LiveTvCategorySheetMode.Show)
            },
            onDismiss = { categorySheetMode = null },
        )
    }
}

@Composable
private fun LiveTvReorderableChannelList(
    channels: List<LiveTvChannel>,
    preferences: LiveTvChannelPreferences,
    uncategorizedLabel: String,
    isTablet: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val channelsById = remember(channels) { channels.associateBy { it.id } }
    val reorderableState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val fromChannel = channelsById[from.key] ?: return@rememberReorderableLazyListState
        val toChannel = channelsById[to.key] ?: return@rememberReorderableLazyListState
        // Moves inside the full list, so dragging also works on search results.
        LiveTvRepository.moveChannel(liveTvChannelPreferenceKey(fromChannel), liveTvChannelPreferenceKey(toChannel))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(modifier = modifier, state = lazyListState) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ReorderableItem(reorderableState, key = channel.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                Surface(shadowElevation = elevation) {
                    Column {
                        if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                        LiveTvChannelManageRow(
                            channel = channel,
                            channelHidden = preferences.isChannelHidden(channel),
                            categoryHidden = preferences.isCategoryHidden(channel),
                            uncategorizedLabel = uncategorizedLabel,
                            isTablet = isTablet,
                            isTv = isTv,
                            dragHandleScope = this@ReorderableItem,
                            onMoveUp = channels.getOrNull(index - 1)?.let { previous ->
                                { LiveTvRepository.moveChannel(liveTvChannelPreferenceKey(channel), liveTvChannelPreferenceKey(previous)) }
                            },
                            onMoveDown = channels.getOrNull(index + 1)?.let { next ->
                                { LiveTvRepository.moveChannel(liveTvChannelPreferenceKey(channel), liveTvChannelPreferenceKey(next)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvChannelManageRow(
    channel: LiveTvChannel,
    channelHidden: Boolean,
    categoryHidden: Boolean,
    uncategorizedLabel: String,
    isTablet: Boolean,
    isTv: Boolean,
    dragHandleScope: ReorderableCollectionItemScope,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    val hapticFeedback = LocalHapticFeedback.current
    val toggle = { LiveTvRepository.setChannelsVisible(listOf(channel), visible = channelHidden) }
    val category = channel.group?.trim()?.takeIf(String::isNotBlank) ?: uncategorizedLabel
    val subtitle = buildList {
        add(category)
        channel.playlistName?.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = toggle)
            .padding(start = if (isTablet) 12.dp else 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = !channelHidden,
            onCheckedChange = { toggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = tokens.colors.accent,
                uncheckedColor = tokens.colors.textMuted,
                checkmarkColor = tokens.colors.onAccent,
            ),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .alpha(if (channelHidden || categoryHidden) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveTvChannelManageLogo(channel)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (categoryHidden) {
                    Text(
                        text = stringResource(Res.string.live_tv_channels_category_hidden),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.accent,
                        maxLines = 1,
                    )
                }
            }
        }
        if (isTv) {
            // D-pad cannot drag: explicit move buttons instead of the handle.
            IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.live_tv_channels_move_up),
                    tint = tokens.colors.textMuted,
                )
            }
            IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.live_tv_channels_move_down),
                    tint = tokens.colors.textMuted,
                )
            }
        } else {
            IconButton(
                modifier = with(dragHandleScope) {
                    Modifier.draggableHandle(
                        onDragStarted = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDragStopped = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            LiveTvRepository.logChannelOrderChanged()
                        },
                    )
                },
                onClick = {},
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(Res.string.live_tv_channels_reorder),
                    tint = tokens.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun LiveTvChannelManageLogo(channel: LiveTvChannel) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.colors.surfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .padding(6.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = tokens.colors.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LiveTvChannelsChip(
    label: String,
    enabled: Boolean,
    trailingIcon: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(tokens.shapes.chip)
            .background(tokens.colors.surface)
            .border(width = NuvioTokens.Border.thin, color = tokens.colors.borderSubtle, shape = tokens.shapes.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        if (trailingIcon) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                tint = tokens.colors.textMuted,
            )
        }
    }
}

/** Multi-select picker of the categories to show again ([LiveTvCategorySheetMode.Show]) or to hide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTvCategoriesSheet(
    mode: LiveTvCategorySheetMode,
    categories: List<LiveTvCategoryInfo>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        coroutineScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember(mode) { mutableStateOf(emptySet<String>()) }
    val filtered = remember(categories, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) categories else categories.filter { it.label.lowercase().contains(normalized) }
    }
    val title = stringResource(
        if (mode == LiveTvCategorySheetMode.Show) Res.string.live_tv_channels_show_categories else Res.string.live_tv_channels_hide_categories,
    )

    NuvioModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            if (mode == LiveTvCategorySheetMode.Show) Res.string.live_tv_categories_sheet_show_hint
                            else Res.string.live_tv_categories_sheet_hide_hint,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            }
            if (categories.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (mode == LiveTvCategorySheetMode.Show) Res.string.live_tv_categories_sheet_empty_show
                            else Res.string.live_tv_categories_sheet_empty_hide,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            } else {
                if (categories.size > 8) {
                    item {
                        NuvioInputField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(Res.string.live_tv_categories_search_placeholder),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                item {
                    val filteredKeys = filtered.map { it.key }.toSet()
                    LiveTvCategoryCheckRow(
                        label = stringResource(Res.string.live_tv_categories_select_all),
                        checked = filteredKeys.isNotEmpty() && selected.containsAll(filteredKeys),
                        onClick = {
                            selected = if (selected.containsAll(filteredKeys)) selected - filteredKeys else selected + filteredKeys
                        },
                    )
                }
                items(filtered, key = { it.key }) { category ->
                    LiveTvCategoryCheckRow(
                        label = stringResource(Res.string.live_tv_categories_channel_count, category.label, category.channelCount),
                        checked = category.key in selected,
                        onClick = {
                            selected = if (category.key in selected) selected - category.key else selected + category.key
                        },
                    )
                }
            }
            item {
                DialogButtons(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DialogButton(
                        text = stringResource(Res.string.action_cancel),
                        onClick = dismiss,
                        style = DialogButtonStyle.Secondary,
                    )
                    DialogButton(
                        text = stringResource(
                            if (mode == LiveTvCategorySheetMode.Show) Res.string.live_tv_categories_apply_show
                            else Res.string.live_tv_categories_apply_hide,
                            selected.size,
                        ),
                        onClick = {
                            onApply(selected)
                            dismiss()
                        },
                        enabled = selected.isNotEmpty(),
                        style = DialogButtonStyle.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvCategoryCheckRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(
                checkedColor = tokens.colors.accent,
                uncheckedColor = tokens.colors.textMuted,
                checkmarkColor = tokens.colors.onAccent,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
