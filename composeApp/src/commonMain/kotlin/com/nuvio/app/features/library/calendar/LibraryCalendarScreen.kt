package com.nuvio.app.features.library.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatCalendarDateWithPattern
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.tv.tvFocusOnAppear
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class CalendarTab { Upcoming, Recent, Month }

/** Reference Monday used to print the localized weekday names (2024-01-01). */
private const val ReferenceMondayEpochDay = 19_723L

@Composable
fun LibraryCalendarScreen(
    onBack: () -> Unit,
    onOpenTitle: (type: String, id: String, title: String) -> Unit,
) {
    val uiState by remember {
        LibraryCalendarRepository.ensureLoaded()
        LibraryCalendarRepository.uiState
    }.collectAsStateWithLifecycle()
    val libraryState by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val libraryKeys = remember(libraryState.items) {
        libraryState.items.mapNotNullTo(mutableSetOf()) { it.toLibraryCalendarTarget()?.key }
    }
    val librarySignature = libraryKeys.hashCode()
    // Opening the page (or a library change while it is open) only fetches what the cache lacks.
    LaunchedEffect(librarySignature, libraryState.isLoaded) {
        LibraryCalendarRepository.refresh(force = false)
    }

    val today = remember { CurrentDateProvider.todayIsoDate() }
    val localeTag = Locale.current.toLanguageTag()
    var tabName by rememberSaveable { mutableStateOf(CalendarTab.Upcoming.name) }
    val tab = remember(tabName) { runCatching { CalendarTab.valueOf(tabName) }.getOrDefault(CalendarTab.Upcoming) }
    var monthKey by rememberSaveable { mutableStateOf(today.take(7)) }
    val month = remember(monthKey) {
        CalendarYearMonth.ofIsoDate("$monthKey-01") ?: CalendarYearMonth.ofIsoDate(today)!!
    }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(today) }
    val eventsByDate = remember(uiState.events) { uiState.events.groupBy { it.dateIso } }

    NuvioScreen {
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NuvioScreenHeader(
                    title = stringResource(Res.string.library_calendar_title),
                    onBack = onBack,
                    actions = {
                        if (uiState.isRefreshing) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { LibraryCalendarRepository.refresh(force = true) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(Res.string.library_calendar_refresh),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
                CalendarTabs(
                    selected = tab,
                    onSelected = { tabName = it.name },
                )
                CalendarStatusLine(uiState = uiState)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        when {
            libraryState.isLoaded && libraryKeys.isEmpty() -> item(key = "empty-library") {
                HomeEmptyStateCard(
                    title = stringResource(Res.string.library_calendar_empty_title),
                    message = stringResource(Res.string.library_calendar_empty_library),
                )
            }

            !uiState.isLoaded ||
                (uiState.events.isEmpty() && (uiState.isRefreshing || uiState.lastUpdatedEpochMs == null)) ->
                item(key = "loading") {
                    Text(
                        text = stringResource(Res.string.library_calendar_loading),
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            tab == CalendarTab.Month -> monthContent(
                month = month,
                today = today,
                localeTag = localeTag,
                selectedDay = selectedDay,
                eventsByDate = eventsByDate,
                onMonthChange = { delta ->
                    val target = month.plusMonths(delta)
                    monthKey = target.firstDayIso.take(7)
                    selectedDay = if (today.startsWith(monthKey)) today else null
                },
                onGoToToday = {
                    monthKey = today.take(7)
                    selectedDay = today
                },
                onDaySelected = { selectedDay = it },
                onOpenTitle = onOpenTitle,
            )

            else -> {
                val upcoming = tab == CalendarTab.Upcoming
                val groups = if (upcoming) {
                    eventsByDate.filterKeys { it >= today }.toList().sortedBy { it.first }
                } else {
                    eventsByDate.filterKeys { it < today }.toList().sortedByDescending { it.first }
                }
                if (groups.isEmpty()) {
                    item(key = "empty-${tab.name}") {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.library_calendar_empty_title),
                            message = stringResource(
                                if (upcoming) Res.string.library_calendar_empty_upcoming
                                else Res.string.library_calendar_empty_recent,
                            ),
                        )
                    }
                } else {
                    groups.forEach { (date, events) ->
                        item(key = "date-$date") {
                            CalendarDateHeader(date = date, today = today, localeTag = localeTag)
                        }
                        items(events, key = { event -> "event-$date-${eventKey(event)}" }) { event ->
                            CalendarEventRow(event = event, onClick = {
                                onOpenTitle(event.contentType, event.contentId, event.title)
                            })
                        }
                    }
                }
            }
        }
    }
}

private fun eventKey(event: LibraryCalendarEvent): String =
    "${event.contentType}:${event.contentId}:${event.season}:${event.episode}"

@Composable
private fun CalendarTabs(
    selected: CalendarTab,
    onSelected: (CalendarTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalendarChip(
            label = stringResource(Res.string.library_calendar_upcoming),
            selected = selected == CalendarTab.Upcoming,
            onClick = { onSelected(CalendarTab.Upcoming) },
            modifier = Modifier.tvFocusOnAppear(),
        )
        CalendarChip(
            label = stringResource(Res.string.library_calendar_recent),
            selected = selected == CalendarTab.Recent,
            onClick = { onSelected(CalendarTab.Recent) },
        )
        CalendarChip(
            label = stringResource(Res.string.library_calendar_show_month),
            selected = selected == CalendarTab.Month,
            onClick = { onSelected(CalendarTab.Month) },
        )
    }
}

@Composable
private fun CalendarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)) else null,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun CalendarStatusLine(uiState: LibraryCalendarUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        val text = when {
            uiState.isRefreshing && uiState.progressTotal > 0 ->
                stringResource(Res.string.library_calendar_progress, uiState.progressDone, uiState.progressTotal)
            uiState.lastUpdatedEpochMs != null ->
                stringResource(Res.string.library_calendar_updated_at, formatLocalDateTime(uiState.lastUpdatedEpochMs))
            else -> stringResource(Res.string.library_calendar_never_updated)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!uiState.isRefreshing && uiState.failedCount > 0) {
            Text(
                text = stringResource(Res.string.library_calendar_failed_items, uiState.failedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (uiState.isRefreshing && uiState.progressTotal > 0) {
            LinearProgressIndicator(
                progress = { uiState.progressDone.toFloat() / uiState.progressTotal },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CalendarDateHeader(
    date: String,
    today: String,
    localeTag: String,
) {
    val relative = when (date) {
        today -> stringResource(Res.string.library_calendar_today)
        plusDaysIso(today, 1) -> stringResource(Res.string.library_calendar_tomorrow)
        plusDaysIso(today, -1) -> stringResource(Res.string.library_calendar_yesterday)
        else -> null
    }
    val formatted = remember(date, localeTag) {
        val weekday = runCatching { formatCalendarDateWithPattern(date, localeTag, "EEEE") }.getOrNull()
        listOfNotNull(weekday, formatReleaseDateForDisplay(date)).joinToString(" · ")
    }
    NuvioSectionLabel(
        text = listOfNotNull(relative, formatted).joinToString(" · "),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun CalendarEventRow(
    event: LibraryCalendarEvent,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val subtitle = if (event.isEpisode) {
        val code = formatEpisodeCodeWithSelectedFormat(event.season, event.episode) ?: when {
            event.season != null && event.episode != null ->
                stringResource(Res.string.compose_player_episode_code_full, event.season, event.episode)
            event.episode != null -> stringResource(Res.string.compose_player_episode_code_episode_only, event.episode)
            else -> ""
        }
        listOfNotNull(code.takeIf { it.isNotBlank() }, event.episodeTitle).joinToString(" · ")
    } else {
        stringResource(Res.string.library_calendar_movie)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(colorScheme.surfaceContainerLow)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorScheme.surfaceContainerHigh),
        ) {
            if (event.poster != null) {
                AsyncImage(
                    model = event.poster,
                    contentDescription = event.title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun LazyListScope.monthContent(
    month: CalendarYearMonth,
    today: String,
    localeTag: String,
    selectedDay: String?,
    eventsByDate: Map<String, List<LibraryCalendarEvent>>,
    onMonthChange: (Int) -> Unit,
    onGoToToday: () -> Unit,
    onDaySelected: (String) -> Unit,
    onOpenTitle: (type: String, id: String, title: String) -> Unit,
) {
    item(key = "month-nav") {
        MonthNavigation(
            month = month,
            today = today,
            localeTag = localeTag,
            onMonthChange = onMonthChange,
            onGoToToday = onGoToToday,
        )
    }
    item(key = "month-weekdays") {
        WeekdayHeader(localeTag = localeTag)
    }
    val leadingBlanks = isoDayOfWeek(month.firstEpochDay) - 1
    val cellCount = leadingBlanks + month.lengthInDays
    val weeks = (cellCount + 6) / 7
    items((0 until weeks).toList(), key = { week -> "month-${month.firstDayIso}-week-$week" }) { week ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (column in 0 until 7) {
                val dayIndex = week * 7 + column - leadingBlanks
                if (dayIndex < 0 || dayIndex >= month.lengthInDays) {
                    Spacer(modifier = Modifier.weight(1f).height(52.dp))
                } else {
                    val date = epochDayToIsoDate(month.firstEpochDay + dayIndex)
                    DayCell(
                        dayNumber = dayIndex + 1,
                        eventCount = eventsByDate[date]?.size ?: 0,
                        isToday = date == today,
                        isSelected = date == selectedDay,
                        onClick = { onDaySelected(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
    val dayEvents = selectedDay?.takeIf { it.startsWith(month.firstDayIso.take(7)) }
        ?.let { eventsByDate[it] }
        .orEmpty()
    if (selectedDay != null && selectedDay.startsWith(month.firstDayIso.take(7))) {
        item(key = "month-day-$selectedDay") {
            CalendarDateHeader(date = selectedDay, today = today, localeTag = localeTag)
        }
        if (dayEvents.isEmpty()) {
            item(key = "month-day-empty") {
                Text(
                    text = stringResource(Res.string.library_calendar_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(dayEvents, key = { event -> "month-event-${eventKey(event)}" }) { event ->
                CalendarEventRow(event = event, onClick = {
                    onOpenTitle(event.contentType, event.contentId, event.title)
                })
            }
        }
    }
}

@Composable
private fun MonthNavigation(
    month: CalendarYearMonth,
    today: String,
    localeTag: String,
    onMonthChange: (Int) -> Unit,
    onGoToToday: () -> Unit,
) {
    val title = remember(month, localeTag) {
        runCatching { formatCalendarDateWithPattern(month.firstDayIso, localeTag, "LLLL yyyy") }
            .getOrDefault(month.firstDayIso.take(7))
            .replaceFirstChar { it.uppercaseChar() }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(-1) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.library_calendar_previous_month),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (!today.startsWith(month.firstDayIso.take(7))) {
            IconButton(onClick = onGoToToday) {
                Icon(
                    imageVector = Icons.Rounded.Today,
                    contentDescription = stringResource(Res.string.library_calendar_go_to_today),
                )
            }
        }
        IconButton(onClick = { onMonthChange(1) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.library_calendar_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(localeTag: String) {
    val names = remember(localeTag) {
        (0 until 7).map { offset ->
            val date = epochDayToIsoDate(ReferenceMondayEpochDay + offset)
            runCatching { formatCalendarDateWithPattern(date, localeTag, "EEE") }.getOrDefault("")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        names.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    eventCount: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val countLabel = if (eventCount > 0) stringResource(Res.string.library_calendar_releases_count, eventCount) else null
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(
                when {
                    isSelected -> colorScheme.primaryContainer
                    eventCount > 0 -> colorScheme.surfaceContainerHigh
                    else -> colorScheme.surfaceContainerLow
                },
            )
            .then(if (isToday) Modifier.border(1.5.dp, colorScheme.primary, shape) else Modifier)
            .clickable(onClickLabel = countLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || eventCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
            )
            if (eventCount > 0) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    repeat(eventCount.coerceAtMost(3)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}
