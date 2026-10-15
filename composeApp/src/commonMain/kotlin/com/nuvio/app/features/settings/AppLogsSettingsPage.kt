package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.logging.AppLogEntry
import com.nuvio.app.core.logging.AppLogLevel
import com.nuvio.app.core.logging.AppLogPlatform
import com.nuvio.app.core.logging.AppLogSource
import com.nuvio.app.core.logging.AppLogStore
import com.nuvio.app.core.logging.AppLogText
import com.nuvio.app.core.logging.AppLoggerSettings
import com.nuvio.app.core.logging.rememberAppLogFileSharer
import com.nuvio.app.core.logging.writeAppLogExportFile
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.app_logs_category_all
import nuvio.composeapp.generated.resources.app_logs_category_option
import nuvio.composeapp.generated.resources.app_logs_category_sheet_title
import nuvio.composeapp.generated.resources.app_logs_category_title
import nuvio.composeapp.generated.resources.app_logs_clear_confirm
import nuvio.composeapp.generated.resources.app_logs_clear_description
import nuvio.composeapp.generated.resources.app_logs_clear_dialog_message
import nuvio.composeapp.generated.resources.app_logs_clear_dialog_title
import nuvio.composeapp.generated.resources.app_logs_clear_title
import nuvio.composeapp.generated.resources.app_logs_copied
import nuvio.composeapp.generated.resources.app_logs_copied_truncated
import nuvio.composeapp.generated.resources.app_logs_copy_description
import nuvio.composeapp.generated.resources.app_logs_copy_title
import nuvio.composeapp.generated.resources.app_logs_empty
import nuvio.composeapp.generated.resources.app_logs_empty_disabled
import nuvio.composeapp.generated.resources.app_logs_enabled_description
import nuvio.composeapp.generated.resources.app_logs_enabled_title
import nuvio.composeapp.generated.resources.app_logs_import_description
import nuvio.composeapp.generated.resources.app_logs_import_failed
import nuvio.composeapp.generated.resources.app_logs_import_title
import nuvio.composeapp.generated.resources.app_logs_imported
import nuvio.composeapp.generated.resources.app_logs_level_debug
import nuvio.composeapp.generated.resources.app_logs_level_error
import nuvio.composeapp.generated.resources.app_logs_level_info
import nuvio.composeapp.generated.resources.app_logs_level_warn
import nuvio.composeapp.generated.resources.app_logs_levels_description
import nuvio.composeapp.generated.resources.app_logs_levels_title
import nuvio.composeapp.generated.resources.app_logs_live_description
import nuvio.composeapp.generated.resources.app_logs_live_title
import nuvio.composeapp.generated.resources.app_logs_nothing_to_export
import nuvio.composeapp.generated.resources.app_logs_search_clear
import nuvio.composeapp.generated.resources.app_logs_search_placeholder
import nuvio.composeapp.generated.resources.app_logs_section_actions
import nuvio.composeapp.generated.resources.app_logs_section_entries
import nuvio.composeapp.generated.resources.app_logs_section_filters
import nuvio.composeapp.generated.resources.app_logs_section_recording
import nuvio.composeapp.generated.resources.app_logs_share_description
import nuvio.composeapp.generated.resources.app_logs_share_failed
import nuvio.composeapp.generated.resources.app_logs_share_title
import nuvio.composeapp.generated.resources.app_logs_system_badge
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The clipboard can't carry megabytes: "copy" keeps the newest entries within this size. */
private const val CLIPBOARD_MAX_CHARS = 300_000
private const val COLLAPSED_MESSAGE_MAX_LINES = 4
private const val EXPORT_FILE_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"
private const val ENTRY_TIME_PATTERN = "HH:mm:ss.SSS"
private const val ENTRY_DAY_TIME_PATTERN = "dd/MM HH:mm:ss.SSS"
private const val DAY_KEY_PATTERN = "yyyyMMdd"

/** Spacing the settings lists put between items (see `NuvioScreen` and the tablet column of `SettingsScreen`). */
private val TabletSettingsItemGap = 18.dp

private val AppLogInfoColor = Color(0xFF4FA3FF)
private val AppLogWarnColor = Color(0xFFFFB020)
private val AppLogErrorColor = Color(0xFFFF5A5F)

/** "App logs" page (Settings → Advanced → Diagnostics). */
internal fun LazyListScope.appLogsSettingsContent(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    item(key = "app_logs_recording") {
        AppLogsRecordingSection(isTablet = isTablet)
    }
    item(key = "app_logs_filters") {
        AppLogsFiltersSection(isTablet = isTablet, viewer = viewer)
    }
    item(key = "app_logs_actions") {
        AppLogsActionsSection(isTablet = isTablet, viewer = viewer)
    }

    val result = viewer.result
    item(key = "app_logs_entries_header") {
        NuvioSectionLabel(
            text = stringResource(Res.string.app_logs_section_entries, result.entries.size, result.totalCount),
        )
    }
    if (result.entries.isEmpty()) {
        item(key = "app_logs_empty") {
            AppLogsEmptyState(isTablet = isTablet)
        }
    } else {
        val lastIndex = result.entries.lastIndex
        itemsIndexed(
            items = result.entries,
            key = { _, entry -> "app_log_${entry.id}" },
            contentType = { _, _ -> "app_log_entry" },
        ) { index, entry ->
            AppLogEntryRow(
                entry = entry,
                expanded = entry.id in viewer.expandedIds,
                isFirst = index == 0,
                isLast = index == lastIndex,
                isTablet = isTablet,
                onClick = { viewer.toggleExpanded(entry.id) },
            )
        }
    }
}

@Composable
private fun AppLogsRecordingSection(isTablet: Boolean) {
    val enabled by remember {
        AppLoggerSettings.ensureLoaded()
        AppLoggerSettings.enabled
    }.collectAsStateWithLifecycle()

    SettingsSection(
        title = stringResource(Res.string.app_logs_section_recording),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.app_logs_enabled_title),
                description = stringResource(Res.string.app_logs_enabled_description),
                checked = enabled,
                isTablet = isTablet,
                onCheckedChange = AppLoggerSettings::setEnabled,
            )
        }
    }
}

@Composable
private fun AppLogsFiltersSection(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    val tokens = MaterialTheme.nuvio
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    val filter = viewer.filter
    val result = viewer.result
    val allCategoriesLabel = stringResource(Res.string.app_logs_category_all)

    SettingsSection(
        title = stringResource(Res.string.app_logs_section_filters),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_category_title),
                description = filter.category ?: allCategoriesLabel,
                isTablet = isTablet,
                onClick = { showCategorySheet = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsChipRow(
                title = stringResource(Res.string.app_logs_levels_title),
                description = stringResource(Res.string.app_logs_levels_description),
                isTablet = isTablet,
            ) {
                AppLogLevel.entries.forEach { level ->
                    AppLogLevelCheckbox(
                        label = stringResource(level.labelRes),
                        color = level.color(),
                        checked = level in filter.levels,
                        onToggle = { viewer.toggleLevel(level) },
                    )
                }
            }
            SettingsGroupDivider(isTablet = isTablet)
            NuvioInputField(
                value = filter.query,
                onValueChange = { viewer.filter = viewer.filter.copy(query = it) },
                placeholder = stringResource(Res.string.app_logs_search_placeholder),
                modifier = Modifier.padding(
                    horizontal = if (isTablet) 20.dp else 16.dp,
                    vertical = 12.dp,
                ),
                trailingContent = if (filter.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewer.filter = viewer.filter.copy(query = "") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(Res.string.app_logs_search_clear),
                                tint = tokens.colors.textMuted,
                            )
                        }
                    }
                } else {
                    null
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.app_logs_live_title),
                description = stringResource(Res.string.app_logs_live_description),
                checked = viewer.live,
                isTablet = isTablet,
                onCheckedChange = { viewer.live = it },
            )
        }
    }

    if (showCategorySheet) {
        val options = remember(result.categories) { listOf<String?>(null) + result.categories.map { it.first } }
        val counts = remember(result.categories) { result.categories.toMap() }
        val categoryOptionFormat = Res.string.app_logs_category_option
        AppearanceOptionBottomSheet(
            title = stringResource(Res.string.app_logs_category_sheet_title),
            options = options,
            selected = filter.category,
            optionLabel = { category ->
                if (category == null) {
                    stringResource(categoryOptionFormat, allCategoriesLabel, result.totalCount)
                } else {
                    stringResource(categoryOptionFormat, category, counts[category] ?: 0)
                }
            },
            onSelected = { viewer.filter = viewer.filter.copy(category = it) },
            onDismiss = { showCategorySheet = false },
        )
    }
}

@Composable
private fun AppLogLevelCheckbox(
    label: String,
    color: Color,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = color,
                uncheckedColor = color.copy(alpha = 0.7f),
                checkmarkColor = Color.Black,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) tokens.colors.textPrimary else tokens.colors.textMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AppLogsActionsSection(
    isTablet: Boolean,
    viewer: AppLogsViewerState,
) {
    val clipboardManager = LocalClipboardManager.current
    val shareFile = rememberAppLogFileSharer()
    val scope = rememberCoroutineScope()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val nothingToExport = stringResource(Res.string.app_logs_nothing_to_export)
    val shareFailed = stringResource(Res.string.app_logs_share_failed)
    val importFailed = stringResource(Res.string.app_logs_import_failed)

    SettingsSection(
        title = stringResource(Res.string.app_logs_section_actions),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_copy_title),
                description = stringResource(Res.string.app_logs_copy_description),
                icon = Icons.Rounded.ContentCopy,
                isTablet = isTablet,
                onClick = {
                    val entries = viewer.result.filteredInOrder
                    if (entries.isEmpty()) {
                        NuvioToastController.show(nothingToExport)
                        return@SettingsNavigationRow
                    }
                    scope.launch {
                        val (text, count) = withContext(Dispatchers.Default) {
                            AppLogText.formatNewestWithin(entries, CLIPBOARD_MAX_CHARS)
                        }
                        clipboardManager.setText(AnnotatedString(text))
                        NuvioToastController.show(
                            if (count < entries.size) {
                                getString(Res.string.app_logs_copied_truncated, count)
                            } else {
                                getString(Res.string.app_logs_copied, count)
                            },
                        )
                    }
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_share_title),
                description = stringResource(Res.string.app_logs_share_description),
                icon = Icons.Rounded.Share,
                isTablet = isTablet,
                onClick = {
                    val entries = viewer.result.filteredInOrder
                    if (entries.isEmpty()) {
                        NuvioToastController.show(nothingToExport)
                        return@SettingsNavigationRow
                    }
                    scope.launch {
                        val path = withContext(Dispatchers.Default) {
                            val now = AppLogStore.nowEpochMs()
                            val fileName = "nuvio-log-${AppLogPlatform.formatTimestamp(now, EXPORT_FILE_TIMESTAMP_PATTERN)}.txt"
                            writeAppLogExportFile(fileName, AppLogText.format(entries))
                        }
                        if (path == null) {
                            NuvioToastController.show(shareFailed)
                        } else {
                            runCatching { shareFile(path) }.onFailure { NuvioToastController.show(shareFailed) }
                        }
                    }
                },
            )
            if (AppLogPlatform.supportsSystemLogImport) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.app_logs_import_title),
                    description = stringResource(Res.string.app_logs_import_description),
                    icon = Icons.Rounded.Terminal,
                    isTablet = isTablet,
                    onClick = {
                        scope.launch {
                            val lines = withContext(Dispatchers.Default) {
                                runCatching { AppLogPlatform.readSystemLog() }.getOrNull()
                            }
                            if (lines == null) {
                                NuvioToastController.show(importFailed)
                            } else {
                                val added = AppLogStore.import(lines)
                                NuvioToastController.show(getString(Res.string.app_logs_imported, added))
                            }
                        }
                    },
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.app_logs_clear_title),
                description = stringResource(Res.string.app_logs_clear_description),
                icon = Icons.Rounded.DeleteOutline,
                isTablet = isTablet,
                onClick = { showClearDialog = true },
            )
        }
    }

    if (showClearDialog) {
        AppLogsClearDialog(
            onConfirm = viewer::clear,
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun AppLogsClearDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.app_logs_clear_dialog_title),
    ) {
        Text(
            text = stringResource(Res.string.app_logs_clear_dialog_message),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textMuted,
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.app_logs_clear_confirm),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun AppLogsEmptyState(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    val recording by AppLoggerSettings.enabled.collectAsStateWithLifecycle()
    SettingsGroup(isTablet = isTablet) {
        Text(
            text = stringResource(
                if (recording) Res.string.app_logs_empty else Res.string.app_logs_empty_disabled,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = 18.dp,
            ),
        )
    }
}

/**
 * One line of the log "console". Rows are separate lazy items (the list can hold thousands of entries)
 * but must look like one continuous card, so each row except the last swallows the list item gap.
 */
@Composable
private fun AppLogEntryRow(
    entry: AppLogEntry,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val levelColor = entry.level.color()
    val itemGap = if (isTablet) TabletSettingsItemGap else tokens.spacing.listGap
    val corner = 12.dp
    val shape: Shape = RoundedCornerShape(
        topStart = if (isFirst) corner else 0.dp,
        topEnd = if (isFirst) corner else 0.dp,
        bottomStart = if (isLast) corner else 0.dp,
        bottomEnd = if (isLast) corner else 0.dp,
    )
    val monoSmall = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    Column(
        modifier = Modifier
            .then(if (isLast) Modifier else Modifier.swallowListGap(itemGap))
            .fillMaxWidth()
            .background(tokens.colors.surface, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(onClick = onClick)
                .padding(end = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(levelColor),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = rememberEntryTimeLabel(entry.epochMs),
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = tokens.colors.textMuted,
                    )
                    Text(
                        text = entry.level.shortLabel,
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = entry.tag,
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.source == AppLogSource.SYSTEM) {
                        Text(
                            text = stringResource(Res.string.app_logs_system_badge),
                            style = monoSmall.copy(fontSize = 10.sp),
                            color = tokens.colors.textMuted,
                            modifier = Modifier
                                .background(tokens.colors.borderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                Text(
                    text = entry.message,
                    style = monoSmall,
                    color = if (entry.level == AppLogLevel.ERROR) levelColor else tokens.colors.textPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MESSAGE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                val throwable = entry.throwable
                if (throwable != null) {
                    Text(
                        text = throwable,
                        style = monoSmall.copy(fontSize = 11.sp),
                        color = tokens.colors.textMuted,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                thickness = tokens.borders.hairline,
                color = tokens.colors.borderSubtle,
            )
        }
    }
}

/** Reports [gap] less height than measured, so the next lazy item starts right below this one. */
private fun Modifier.swallowListGap(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height - gap.roundToPx()).coerceAtLeast(0)
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** `HH:mm:ss.SSS` for today's entries, with the day in front for older ones (retention spans 24 h). */
@Composable
private fun rememberEntryTimeLabel(epochMs: Long): String =
    remember(epochMs) {
        val today = AppLogPlatform.formatTimestamp(AppLogStore.nowEpochMs(), DAY_KEY_PATTERN)
        val day = AppLogPlatform.formatTimestamp(epochMs, DAY_KEY_PATTERN)
        AppLogPlatform.formatTimestamp(epochMs, if (day == today) ENTRY_TIME_PATTERN else ENTRY_DAY_TIME_PATTERN)
    }

private val AppLogLevel.labelRes: StringResource
    get() = when (this) {
        AppLogLevel.DEBUG -> Res.string.app_logs_level_debug
        AppLogLevel.INFO -> Res.string.app_logs_level_info
        AppLogLevel.WARN -> Res.string.app_logs_level_warn
        AppLogLevel.ERROR -> Res.string.app_logs_level_error
    }

@Composable
private fun AppLogLevel.color(): Color =
    when (this) {
        AppLogLevel.DEBUG -> MaterialTheme.nuvio.colors.textMuted
        AppLogLevel.INFO -> AppLogInfoColor
        AppLogLevel.WARN -> AppLogWarnColor
        AppLogLevel.ERROR -> AppLogErrorColor
    }
