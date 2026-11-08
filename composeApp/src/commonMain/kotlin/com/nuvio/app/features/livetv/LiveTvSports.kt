package com.nuvio.app.features.livetv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_sports_broadcast
import nuvio.composeapp.generated.resources.live_tv_sports_finished
import nuvio.composeapp.generated.resources.live_tv_sports_starts_in
import nuvio.composeapp.generated.resources.live_tv_sports_today
import nuvio.composeapp.generated.resources.live_tv_sports_no_channels
import nuvio.composeapp.generated.resources.live_tv_sports_reminder_add
import nuvio.composeapp.generated.resources.live_tv_sports_reminder_remove
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

enum class LiveTvSportsState { Upcoming, Live, Finished }

data class LiveTvSportsCompetitor(
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val score: String?,
    val isHome: Boolean,
    val isWinner: Boolean,
)

data class LiveTvSportsEvent(
    val id: String,
    val league: LiveTvSportsLeague,
    val name: String,
    val startMillis: Long,
    val state: LiveTvSportsState,
    val statusDetail: String,
    val competitors: List<LiveTvSportsCompetitor>,
    val broadcasts: List<String>,
    /** False for events that come with a date only (sporting-events.org): no start time to show. */
    val hasTime: Boolean = true,
)

data class LiveTvSportsUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val events: List<LiveTvSportsEvent> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

// ---------------------------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------------------------

object LiveTvSportsRepository {
    private val log = Logger.withTag("LiveTvSports")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvSportsUiState())
    val uiState: StateFlow<LiveTvSportsUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    private val _openSportsRequested = MutableStateFlow(false)

    /** True when something outside asks for Live TV → Sports (a reminder notification), until consumed. */
    val openSportsRequested: StateFlow<Boolean> = _openSportsRequested.asStateFlow()

    fun requestOpenSports() {
        _openSportsRequested.value = true
    }

    fun consumeOpenSportsRequest() {
        _openSportsRequested.value = false
    }

    /** At most this many leagues are asked at once, so a long list of leagues doesn't flood the network. */
    private const val PARALLEL_LEAGUES = 8

    /** Leagues of the last load, so a change in the settings reloads at once. */
    private var loadedSignature: String? = null

    /** The leagues to load: the ones picked in Settings → Sports (or the defaults). */
    internal fun enabledLeagues(): List<LiveTvSportsLeague> {
        LiveTvSportsSettingsRepository.ensureLoaded()
        return LiveTvSportsSettingsRepository.settings.value.enabledLeagues()
    }

    private fun currentSignature(): String {
        LiveTvSportsSettingsRepository.ensureLoaded()
        return LiveTvSportsSettingsRepository.settings.value.signature()
    }

    /** Refreshes when stale: every minute while something is live, otherwise every five minutes. */
    fun refreshIfStale() {
        val state = _uiState.value
        if (state.isLoading) return
        val now = Clock.System.now().toEpochMilliseconds()
        val maxAge = if (state.events.any { it.state == LiveTvSportsState.Live }) MINUTE_MILLIS else 5 * MINUTE_MILLIS
        val leaguesChanged = loadedSignature != null && loadedSignature != currentSignature()
        if (state.hasLoaded && !leaguesChanged && now - state.updatedAtMillis < maxAge) return
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        job = scope.launch {
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            val window = LiveTvSportsWindow.around(nowMillis)
            loadedSignature = currentSignature()
            val leagues = enabledLeagues()
            val gate = Semaphore(PARALLEL_LEAGUES)
            val results = withContext(Dispatchers.Default) {
                leagues.map { league ->
                    async {
                        gate.withPermit {
                            try {
                                Result.success(LiveTvSportsFeeds.fetch(league, window))
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                log.w { "${league.id} failed: ${error.message}" }
                                Result.failure(error)
                            }
                        }
                    }
                }.awaitAll()
            }
            val events = results
                .flatMap { it.getOrNull().orEmpty() }
                .filter { event ->
                    when (event.state) {
                        LiveTvSportsState.Live -> true
                        // Up to two days ahead; a start more than a day gone means the feed never
                        // marked it finished (date-only fixtures), so it's dropped.
                        LiveTvSportsState.Upcoming -> event.startMillis - nowMillis <= 48 * HOUR_MILLIS &&
                            nowMillis - event.startMillis <= DAY_MILLIS
                        LiveTvSportsState.Finished -> nowMillis - event.startMillis <= 18 * HOUR_MILLIS
                    }
                }
                .distinctBy { "${it.league.id}:${it.id}" }
                .sortedWith(liveTvSportsEventOrder)
            val allFailed = results.isNotEmpty() && results.all { it.isFailure }
            if (!allFailed) LiveTvSportsRemindersRepository.updateFromEvents(events)
            if (allFailed) {
                log.e(results.first().exceptionOrNull()) { "All sports feeds failed" }
            } else {
                log.i {
                    "Loaded ${events.size} events (${events.count { it.state == LiveTvSportsState.Live }} live) " +
                        "from ${leagues.size} leagues, ${results.count { it.isFailure }} failed"
                }
            }
            _uiState.value = LiveTvSportsUiState(
                isLoading = false,
                hasLoaded = true,
                errorMessage = if (allFailed) results.first().exceptionOrNull()?.message ?: "Network error" else null,
                events = if (allFailed) _uiState.value.events else events,
                updatedAtMillis = nowMillis,
            )
        }
    }
}

private val liveTvSportsEventOrder = Comparator<LiveTvSportsEvent> { a, b ->
    val rank = { event: LiveTvSportsEvent ->
        when (event.state) {
            LiveTvSportsState.Live -> 0
            LiveTvSportsState.Upcoming -> 1
            LiveTvSportsState.Finished -> 2
        }
    }
    val byRank = rank(a).compareTo(rank(b))
    when {
        byRank != 0 -> byRank
        a.state == LiveTvSportsState.Finished -> b.startMillis.compareTo(a.startMillis)
        else -> a.startMillis.compareTo(b.startMillis)
    }
}

// ---------------------------------------------------------------------------------------------
// Channel matching
// ---------------------------------------------------------------------------------------------

private val channelNoiseTokens = setOf(
    "hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "1080p", "720p", "50fps", "60fps",
    "raw", "vip", "backup", "live", "tv",
)

internal fun normalizeSportsName(value: String): String =
    value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .filter { it.isNotBlank() && it !in channelNoiseTokens }
        .joinToString(" ")

/**
 * Finds playlist channels that probably carry [event]: channels named after both teams (event
 * channels many providers create per match) rank first, then channels whose name matches one of
 * ESPN's broadcaster names as a whole-word sequence.
 */
internal fun matchSportsChannels(
    event: LiveTvSportsEvent,
    channels: List<Pair<LiveTvChannel, String>>,
    limit: Int = 8,
): List<LiveTvChannel> {
    val teamKeys = event.competitors
        .map { normalizeSportsName(it.shortName).ifBlank { normalizeSportsName(it.name) } }
        .filter { it.length >= 3 }
    val broadcastKeys = event.broadcasts.map(::normalizeSportsName).filter { it.length >= 3 }
    if (teamKeys.size < 2 && broadcastKeys.isEmpty()) return emptyList()
    return channels.asSequence()
        .mapNotNull { (channel, normalized) ->
            val padded = " $normalized "
            val score = when {
                teamKeys.size >= 2 && teamKeys.all { padded.contains(" $it ") } -> 3
                broadcastKeys.any { it == normalized } -> 2
                broadcastKeys.any { padded.contains(" $it ") } -> 1
                else -> 0
            }
            if (score > 0) channel to score else null
        }
        .sortedWith(compareByDescending<Pair<LiveTvChannel, Int>> { it.second }.thenBy { it.first.name.length })
        .take(limit)
        .map { it.first }
        .toList()
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

@Composable
internal fun LiveTvSportsControls(
    allLabel: String,
    liveLabel: String,
    leagues: List<LiveTvSportsLeague>,
    hasLive: Boolean,
    selectedFilter: String,
    eventCount: Int,
    onFilterSelected: (String) -> Unit,
    remindersLabel: String? = null,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            LiveTvPanelFilterChip(
                label = allLabel,
                selected = selectedFilter == LIVE_TV_SPORTS_FILTER_ALL,
                onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_ALL) },
            )
            if (hasLive) {
                LiveTvPanelFilterChip(
                    label = liveLabel,
                    selected = selectedFilter == LIVE_TV_SPORTS_FILTER_LIVE,
                    onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_LIVE) },
                )
            }
            if (remindersLabel != null) {
                LiveTvPanelFilterChip(
                    label = remindersLabel,
                    selected = selectedFilter == LIVE_TV_SPORTS_FILTER_REMINDERS,
                    onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_REMINDERS) },
                )
            }
            leagues.forEach { league ->
                LiveTvPanelFilterChip(
                    label = league.label,
                    selected = selectedFilter == league.id,
                    onClick = { onFilterSelected(league.id) },
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = eventCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
        )
    }
}

internal const val LIVE_TV_SPORTS_FILTER_ALL = "all"
internal const val LIVE_TV_SPORTS_FILTER_LIVE = "live"
internal const val LIVE_TV_SPORTS_FILTER_REMINDERS = "reminders"

internal fun LazyListScope.liveTvSportsList(
    events: List<LiveTvSportsEvent>,
    matchedChannels: Map<String, List<LiveTvChannel>>,
    nowMillis: Long,
    onChannelClick: (LiveTvChannel) -> Unit,
    /** Keys of the games with a reminder; null when reminders are off (no bell). */
    reminderKeys: Set<String>? = null,
    onReminderToggle: (LiveTvSportsEvent) -> Unit = {},
) {
    items(events, key = { "sports:${it.league.id}:${it.id}" }) { event ->
        LiveTvSportsEventCard(
            event = event,
            channels = matchedChannels["${event.league.id}:${event.id}"].orEmpty(),
            nowMillis = nowMillis,
            onChannelClick = onChannelClick,
            reminderSet = reminderKeys
                ?.takeIf { event.state != LiveTvSportsState.Finished }
                ?.let { liveTvSportsEventKey(event) in it },
            onReminderToggle = { onReminderToggle(event) },
        )
    }
}

@Composable
private fun LiveTvSportsEventCard(
    event: LiveTvSportsEvent,
    channels: List<LiveTvChannel>,
    nowMillis: Long,
    onChannelClick: (LiveTvChannel) -> Unit,
    reminderSet: Boolean?,
    onReminderToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val isLive = event.state == LiveTvSportsState.Live
    val cardShape = tokens.shapes.compactCard
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLive) {
                    // Pulsing red outline while the match is on (draw phase only).
                    Modifier.liveTvGlowBorder(
                        alpha = rememberLiveTvBlinkAlpha(minAlpha = 0.25f, periodMillis = 1_200),
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            ),
        onClick = { channels.firstOrNull()?.let(onChannelClick) },
        enabled = channels.isNotEmpty(),
        color = tokens.colors.surface,
        shape = cardShape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = event.league.icon,
                    contentDescription = null,
                    tint = if (isLive) LiveTvLiveRed else tokens.colors.textMuted,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(15.dp),
                )
                Text(
                    text = event.league.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvSportsStatus(event = event, nowMillis = nowMillis)
                if (reminderSet != null) {
                    Icon(
                        imageVector = if (reminderSet) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        contentDescription = stringResource(
                            if (reminderSet) Res.string.live_tv_sports_reminder_remove else Res.string.live_tv_sports_reminder_add,
                        ),
                        tint = if (reminderSet) tokens.colors.accent else tokens.colors.textMuted,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onReminderToggle)
                            .padding(4.dp)
                            .size(20.dp),
                    )
                }
            }
            if (event.competitors.size >= 2) {
                event.competitors.forEach { competitor ->
                    LiveTvSportsCompetitorRow(
                        competitor = competitor,
                        showScore = event.state != LiveTvSportsState.Upcoming,
                        dimmed = event.state == LiveTvSportsState.Finished &&
                            event.competitors.any { it.isWinner } && !competitor.isWinner,
                    )
                }
            } else {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (event.broadcasts.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.live_tv_sports_broadcast, event.broadcasts.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (channels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    channels.forEach { channel ->
                        LiveTvSportsChannelChip(channel = channel, onClick = { onChannelClick(channel) })
                    }
                }
            } else if (event.state != LiveTvSportsState.Finished) {
                Text(
                    text = stringResource(Res.string.live_tv_sports_no_channels),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun LiveTvSportsStatus(event: LiveTvSportsEvent, nowMillis: Long) {
    val tokens = MaterialTheme.nuvio
    when (event.state) {
        LiveTvSportsState.Live -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (event.statusDetail.isNotBlank()) {
                Text(
                    text = event.statusDetail,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                )
            }
            LiveTvLiveBadge()
        }

        LiveTvSportsState.Upcoming -> {
            val minutesToStart = (event.startMillis - nowMillis) / 60_000L
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (!event.hasTime) {
                        // A date only: "Today" or the day, never a made-up time.
                        formatSportsDay(event.startMillis, nowMillis)
                            ?: stringResource(Res.string.live_tv_sports_today)
                    } else if (minutesToStart in 0L until 60L) {
                        // Countdown in the last hour before the start.
                        stringResource(Res.string.live_tv_sports_starts_in, "${minutesToStart.coerceAtLeast(1L)} min")
                    } else {
                        formatSportsStart(event.startMillis, nowMillis)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LiveTvSportsState.Finished -> Text(
            text = event.statusDetail.ifBlank { stringResource(Res.string.live_tv_sports_finished) },
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
        )
    }
}

/** "dd/MM" of a date-only event, or null when it's today. */
private fun formatSportsDay(startMillis: Long, nowMillis: Long): String? {
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    if (start <= today) return null
    return "${start.dayOfMonth.toString().padStart(2, '0')}/${start.monthNumber.toString().padStart(2, '0')}"
}

private fun formatSportsStart(startMillis: Long, nowMillis: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(zone)
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val time = formatLiveTvEpgTime(startMillis)
    return if (start.date == today) {
        time
    } else {
        "${start.dayOfMonth.toString().padStart(2, '0')}/${start.monthNumber.toString().padStart(2, '0')} $time"
    }
}

@Composable
private fun LiveTvSportsCompetitorRow(
    competitor: LiveTvSportsCompetitor,
    showScore: Boolean,
    dimmed: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val textColor = if (dimmed) tokens.colors.textMuted else tokens.colors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!competitor.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = competitor.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.SportsSoccer,
                    contentDescription = null,
                    tint = tokens.colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = competitor.name,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = if (competitor.isWinner) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showScore && !competitor.score.isNullOrBlank()) {
            // A new goal slides the score up.
            AnimatedContent(
                targetState = competitor.score,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "liveTvSportsScore",
            ) { score ->
                Text(
                    text = score,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LiveTvSportsChannelChip(
    channel: LiveTvChannel,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(tokens.colors.overlaySelected)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tokens.colors.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
