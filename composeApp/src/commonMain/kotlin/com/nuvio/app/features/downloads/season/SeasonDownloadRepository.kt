package com.nuvio.app.features.downloads.season

import co.touchlab.kermit.Logger
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.downloads.DownloadEnqueueResult
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.DownloadsClock
import com.nuvio.app.features.downloads.DownloadsPreferences
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.DownloadsStorage
import com.nuvio.app.features.downloads.hls.HlsChoicesLoad
import com.nuvio.app.features.downloads.hls.HlsDownloadPreferences
import com.nuvio.app.features.downloads.hls.HlsDownloadSpec
import com.nuvio.app.features.downloads.hls.loadHlsDownloadChoices
import com.nuvio.app.features.player.PlayerStreamsRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Downloads a whole season with the stream picked for its first episode.
 *
 * The episodes are not looked up all at once: the links of debrid services and of many sources
 * expire, and the sources limit how often they can be asked. Each episode is looked up only when
 * a download slot is free ([DownloadsPreferences.seasonConcurrency]), one lookup at a time and a
 * few seconds apart, and then handed to [DownloadsRepository] like a single download.
 *
 * Fallbacks: when the chosen source has no stream for an episode, the closest stream of another
 * source is used; a stream that fails (lookup, debrid, HLS or download) is skipped at the next
 * attempt; a rate limit (HTTP 429/403/503/509) pauses the season and lowers the number of
 * downloads at the same time, which grows again with every completed episode.
 *
 * All the state is changed on the main thread, like [DownloadsRepository].
 */
object SeasonDownloadRepository {
    private val log = Logger.withTag("SeasonDownload")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _batches = MutableStateFlow<List<SeasonDownloadBatch>>(emptyList())
    val batches: StateFlow<List<SeasonDownloadBatch>> = _batches.asStateFlow()

    private val _pendingRequest = MutableStateFlow<SeasonDownloadRequest?>(null)
    /** Season download waiting for the user to pick the stream of its first episode. */
    val pendingRequest: StateFlow<SeasonDownloadRequest?> = _pendingRequest.asStateFlow()

    private var hasLoaded = false
    private var observerJob: Job? = null
    private var lookupJob: Job? = null
    private var lookupEpisodeKey: String? = null
    private var delayedPumpJob: Job? = null
    private var lastLookupAtMs = 0L
    private var nextLookupSpacingMs = 0L
    private val _cooldowns = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Season id -> time until which it waits after a rate limit. */
    val cooldowns: StateFlow<Map<String, Long>> = _cooldowns.asStateFlow()
    /** Lowered by rate limits, raised again by completed episodes; null = the setting. */
    private var adaptiveLimit: Int? = null

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        DownloadsPreferences.ensureLoaded()
        DownloadsRepository.ensureLoaded()
        val stored = decode(DownloadsStorage.loadSeasonBatchesPayload())
        // A lookup cut short by the end of the process starts over.
        _batches.value = stored.map { batch ->
            batch.copy(episodes = batch.episodes.map {
                if (it.state == SeasonEpisodeState.Resolving) it.copy(state = SeasonEpisodeState.Pending) else it
            })
        }
        log.i { "Loaded ${_batches.value.size} season download(s)" }
        observerJob?.cancel()
        observerJob = scope.launch {
            DownloadsRepository.uiState.collect { state ->
                syncWithDownloads(state.items)
                pump()
            }
        }
    }

    fun onProfileChanged() {
        observerJob?.cancel()
        lookupJob?.cancel()
        delayedPumpJob?.cancel()
        lookupEpisodeKey = null
        _cooldowns.value = emptyMap()
        adaptiveLimit = null
        _pendingRequest.value = null
        hasLoaded = false
        _batches.value = emptyList()
        ensureLoaded()
    }

    // region Request from the details screen

    /**
     * Prepares the download of [episodes] (the released episodes of [seasonNumber]) and returns the
     * request whose first episode the user must pick a stream for; null when every episode is
     * already downloaded or downloading.
     */
    fun requestSeason(meta: MetaDetails, seasonNumber: Int, episodes: List<MetaVideo>): SeasonDownloadRequest? {
        ensureLoaded()
        val seasonEpisodes = episodes
            .filter { it.season == seasonNumber && it.episode != null }
            .distinctBy { it.episode }
            .sortedBy { it.episode }
            .map { video ->
                SeasonDownloadEpisode(
                    videoId = video.id.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(
                        parentMetaId = meta.id,
                        seasonNumber = video.season,
                        episodeNumber = video.episode,
                        fallbackVideoId = video.id,
                    ),
                    seasonNumber = seasonNumber,
                    episodeNumber = video.episode!!,
                    title = video.title.takeIf { it.isNotBlank() },
                    thumbnail = video.thumbnail,
                )
            }
        val missing = seasonEpisodes.filter { episode ->
            val existing = DownloadsRepository.findEpisodeDownload(meta.id, seasonNumber, episode.episodeNumber)
            existing == null || existing.status == DownloadStatus.Failed
        }
        if (missing.isEmpty()) {
            log.i { "Season $seasonNumber of ${meta.id}: every episode is already downloaded" }
            return null
        }
        val request = SeasonDownloadRequest(
            contentType = meta.type,
            parentMetaId = meta.id,
            parentMetaType = meta.type,
            title = meta.name,
            logo = meta.logo,
            poster = meta.poster,
            background = meta.background,
            seasonNumber = seasonNumber,
            // Episodes already downloading are followed too, the first one to pick is the first missing.
            episodes = missing + (seasonEpisodes - missing.toSet()),
        )
        log.i { "Season download requested: ${meta.id} season $seasonNumber, ${missing.size} episode(s) to download" }
        _pendingRequest.value = request
        return request
    }

    fun clearRequest(request: SeasonDownloadRequest? = null) {
        if (request == null || _pendingRequest.value == request) _pendingRequest.value = null
    }

    /**
     * Starts the season download of [request] with the stream picked for its first episode:
     * [templateStream] as listed by its source (what the other episodes are matched against),
     * [playableStream] with its playable link, [hlsSpec]/[hlsPreferences] when it is HLS.
     */
    fun start(
        request: SeasonDownloadRequest,
        templateStream: StreamItem,
        playableStream: StreamItem,
        hlsSpec: HlsDownloadSpec?,
        hlsPreferences: HlsDownloadPreferences?,
    ): DownloadEnqueueResult {
        ensureLoaded()
        clearRequest(request)
        val first = request.firstEpisode
        val now = DownloadsClock.nowEpochMs()
        val batchId = "season_${now.toString(36)}_${Random.nextInt(0, 1_000_000).toString(36)}"
        val template = SeasonStreamMatcher.templateOf(templateStream, request.seasonNumber, first.episodeNumber)
        val episodes = request.episodes.map { episode ->
            val existing = DownloadsRepository.findEpisodeDownload(request.parentMetaId, episode.seasonNumber, episode.episodeNumber)
            when {
                episode == first -> episode.copy(state = SeasonEpisodeState.Resolving)
                existing == null || existing.status == DownloadStatus.Failed -> episode
                existing.status == DownloadStatus.Completed -> episode.copy(state = SeasonEpisodeState.Completed, downloadId = existing.id)
                else -> episode.copy(state = SeasonEpisodeState.Downloading, downloadId = existing.id)
            }
        }
        // Another season download of the same season is replaced.
        _batches.value.filter { it.parentMetaId == request.parentMetaId && it.seasonNumber == request.seasonNumber }
            .forEach { removeBatch(it.id, deleteUnfinished = false) }
        val batch = SeasonDownloadBatch(
            id = batchId,
            contentType = request.contentType,
            parentMetaId = request.parentMetaId,
            parentMetaType = request.parentMetaType,
            title = request.title,
            logo = request.logo,
            poster = request.poster,
            background = request.background,
            seasonNumber = request.seasonNumber,
            template = template,
            hls = hlsPreferences,
            episodes = episodes,
            createdAtEpochMs = now,
        )
        _batches.value = _batches.value + batch
        persist()
        log.i {
            "Season download $batchId started: ${request.parentMetaId} season ${request.seasonNumber}, " +
                "${episodes.count { it.state == SeasonEpisodeState.Pending } + 1} episode(s) to download " +
                "from ${template.addonName} (binge group: ${template.bingeGroup != null}, file name: ${template.filename != null}, " +
                "quality: ${template.resolution ?: "unknown"})"
        }
        val result = enqueueEpisode(batch, first, playableStream, hlsSpec, fallbackSource = null)
        if (result != DownloadEnqueueResult.Started && result != DownloadEnqueueResult.Replaced) {
            // The picked stream cannot be downloaded: the first episode is looked up like the others.
            registerFailure(batchId, first.episodeNumber, SeasonStreamMatcher.keyOf(templateStream), result.toastMessage())
        }
        pump()
        return result
    }

    // endregion

    // region Actions of the downloads screen

    fun pauseBatch(batchId: String) {
        val batch = batch(batchId) ?: return
        log.i { "Season download $batchId paused" }
        if (lookupEpisodeKey?.startsWith("$batchId|") == true) cancelLookup()
        updateBatch(batchId) { current ->
            current.copy(paused = true, episodes = current.episodes.map {
                if (it.state == SeasonEpisodeState.Resolving) it.copy(state = SeasonEpisodeState.Pending) else it
            })
        }
        batch.episodes.filter { it.state == SeasonEpisodeState.Downloading }
            .mapNotNull { it.downloadId }
            .forEach(DownloadsRepository::pauseDownload)
    }

    fun resumeBatch(batchId: String) {
        val batch = batch(batchId) ?: return
        log.i { "Season download $batchId resumed" }
        _cooldowns.value = _cooldowns.value - batchId
        updateBatch(batchId) { it.copy(paused = false) }
        batch.episodes.filter { it.state == SeasonEpisodeState.Downloading }
            .mapNotNull { it.downloadId }
            .forEach(DownloadsRepository::resumeDownload)
        pump()
    }

    /** Pauses every season download of [parentMetaId], or every season download when it is null. */
    fun pauseAll(parentMetaId: String? = null) {
        _batches.value
            .filter { !it.paused && !it.isFinished && (parentMetaId == null || it.parentMetaId == parentMetaId) }
            .forEach { pauseBatch(it.id) }
    }

    /** Resumes every paused season download of [parentMetaId], or all of them when it is null. */
    fun resumeAll(parentMetaId: String? = null) {
        _batches.value
            .filter { it.paused && (parentMetaId == null || it.parentMetaId == parentMetaId) }
            .forEach { resumeBatch(it.id) }
    }

    /**
     * Cancels every season download of [parentMetaId] (all of them when null): no more episodes
     * are looked up and their unfinished downloads are deleted; finished episodes stay.
     */
    fun cancelAll(parentMetaId: String? = null) {
        val targets = _batches.value.filter { parentMetaId == null || it.parentMetaId == parentMetaId }
        if (targets.isEmpty()) return
        log.i { "Cancel ${targets.size} season download(s)${parentMetaId?.let { " of $it" }.orEmpty()}" }
        targets.forEach { removeBatch(it.id, deleteUnfinished = true) }
        pump()
    }

    /** Looks up again the episodes that failed or had no matching stream. */
    fun retryBatch(batchId: String) {
        log.i { "Season download $batchId: retrying the failed episodes" }
        _cooldowns.value = _cooldowns.value - batchId
        updateBatch(batchId) { current ->
            current.copy(paused = false, episodes = current.episodes.map { episode ->
                if (episode.state == SeasonEpisodeState.Failed || episode.state == SeasonEpisodeState.NeedsManual) {
                    episode.copy(state = SeasonEpisodeState.Pending, attempts = 0, message = null, failedSources = emptyList())
                } else {
                    episode
                }
            })
        }
        pump()
    }

    /** Stops the season download; the unfinished episodes are deleted, the finished ones are kept. */
    fun cancelBatch(batchId: String) {
        log.i { "Season download $batchId cancelled" }
        removeBatch(batchId, deleteUnfinished = true)
        pump()
    }

    /** Removes a finished season download from the list (its files stay). */
    fun dismissBatch(batchId: String) {
        removeBatch(batchId, deleteUnfinished = false)
    }

    // endregion

    // region Background

    /**
     * Called by the background download job when one of its transfers ends, before the job
     * finishes: waits (at most [timeoutMs]) until the next episodes of the season downloads have
     * been looked up and handed to the downloads, so the process stays alive in the meantime.
     *
     * In a process started by the system without the app (no profile, sources or downloads
     * loaded) there is nothing to do here: the season downloads resume when the app opens.
     */
    suspend fun awaitNextEpisodes(timeoutMs: Long) {
        if (!hasLoaded || _batches.value.none { !it.paused && !it.isFinished }) return
        withContext(Dispatchers.Main) { awaitNextEpisodesOnMain(timeoutMs) }
    }

    private suspend fun awaitNextEpisodesOnMain(timeoutMs: Long) {
        log.d { "Background job ended: waiting for the next season episodes" }
        val finished = withTimeoutOrNull(timeoutMs) {
            // The end of the transfer reaches DownloadsRepository through its observer first.
            delay(BACKGROUND_SETTLE_MS)
            while (true) {
                pump()
                val lookup = lookupJob
                if (lookup?.isActive == true) {
                    lookup.join()
                    continue
                }
                if (!hasStartableWork(ignoreWaits = true)) break
                delay(BACKGROUND_POLL_MS)
            }
        }
        if (finished == null) log.w { "Background job: next season episodes not started within ${timeoutMs / 1000}s" }
    }

    // endregion

    // region Coordinator

    private fun pump() {
        if (!hasLoaded) return
        if (lookupJob?.isActive == true) return
        if (activeDownloads() >= effectiveLimit()) return
        val now = DownloadsClock.nowEpochMs()
        val next = nextPendingEpisode(now) ?: run {
            // Only seasons waiting for a rate limit pause: try again when it ends.
            _cooldowns.value.filterKeys { id -> batch(id)?.let { !it.paused && it.pendingCount > 0 } == true }
                .values.minOrNull()?.let { schedulePump(it - now) }
            return
        }
        val wait = lastLookupAtMs + nextLookupSpacingMs - now
        if (wait > 0L) {
            schedulePump(wait)
            return
        }
        val (batch, episode) = next
        lastLookupAtMs = now
        nextLookupSpacingMs = LOOKUP_SPACING_MS + Random.nextLong(0L, LOOKUP_JITTER_MS)
        updateEpisode(batch.id, episode.episodeNumber) { it.copy(state = SeasonEpisodeState.Resolving, message = null) }
        val key = "${batch.id}|${episode.episodeNumber}"
        lookupEpisodeKey = key
        lookupJob = scope.launch {
            try {
                lookUpAndEnqueue(batch.id, episode.episodeNumber)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.e(error) { "Lookup of ${batch.parentMetaId} S${batch.seasonNumber}E${episode.episodeNumber} failed" }
                registerFailure(batch.id, episode.episodeNumber, sourceKey = null, message = error.message)
            } finally {
                if (lookupEpisodeKey == key) lookupEpisodeKey = null
            }
            pump()
        }
    }

    private suspend fun lookUpAndEnqueue(batchId: String, episodeNumber: Int) {
        val batch = batch(batchId) ?: return
        val episode = batch.episodes.firstOrNull { it.episodeNumber == episodeNumber } ?: return
        log.i { "Looking up ${batch.parentMetaId} S${batch.seasonNumber}E$episodeNumber (attempt ${episode.attempts + 1})" }
        val state = PlayerStreamsRepository.resolveStreams(
            type = batch.contentType,
            videoId = episode.videoId,
            season = batch.seasonNumber,
            episode = episodeNumber,
            timeoutMs = LOOKUP_TIMEOUT_MS,
        )
        val streams = state.groups.flatMap { it.streams }
        val candidates = SeasonStreamMatcher.rank(
            template = batch.template,
            seasonNumber = batch.seasonNumber,
            episodeNumber = episodeNumber,
            streams = streams,
            excludedKeys = episode.failedSources.toSet(),
            isDownloadable = { it.playableDirectUrl != null || DirectDebridPlaybackResolver.shouldResolveToPlayableStream(it) },
        )
        if (candidates.isEmpty()) {
            log.w { "No stream for S${batch.seasonNumber}E$episodeNumber among ${streams.size} stream(s): the user has to pick it" }
            updateEpisode(batchId, episodeNumber) { it.copy(state = SeasonEpisodeState.NeedsManual, message = null) }
            return
        }
        val skipped = mutableListOf<String>()
        for (candidate in candidates.take(MAX_CANDIDATES_PER_LOOKUP)) {
            if (batch(batchId)?.paused != false) return
            val key = SeasonStreamMatcher.keyOf(candidate)
            val fallbackSource = candidate.addonName.takeUnless { SeasonStreamMatcher.isFromSource(candidate, batch.template) }
            if (fallbackSource != null) log.i { "S${batch.seasonNumber}E$episodeNumber: no usable stream from ${batch.template.addonName}, falling back to $fallbackSource" }
            val playable = playableStreamOf(candidate, batch.seasonNumber, episodeNumber)
            if (playable == null) {
                skipped += key
                continue
            }
            val isHls = DownloadsRepository.isHlsStreamDownload(playable)
            val hlsSpec = if (isHls) hlsSpecFor(batch, playable) else null
            if (isHls && hlsSpec == null) {
                skipped += key
                continue
            }
            val current = batch(batchId) ?: return
            val currentEpisode = current.episodes.firstOrNull { it.episodeNumber == episodeNumber } ?: return
            updateEpisode(batchId, episodeNumber) { it.copy(failedSources = (it.failedSources + skipped).distinct()) }
            val result = enqueueEpisode(current, currentEpisode, playable, hlsSpec, fallbackSource)
            if (result == DownloadEnqueueResult.Started || result == DownloadEnqueueResult.Replaced) return
            skipped += key
        }
        log.w { "S${batch.seasonNumber}E$episodeNumber: none of the ${candidates.size} stream(s) could be used" }
        updateEpisode(batchId, episodeNumber) { it.copy(failedSources = (it.failedSources + skipped).distinct()) }
        registerFailure(batchId, episodeNumber, sourceKey = null, message = null)
    }

    /** The stream with a playable link (resolved through the debrid service when needed); null when it cannot be used. */
    private suspend fun playableStreamOf(stream: StreamItem, seasonNumber: Int, episodeNumber: Int): StreamItem? {
        if (!DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) {
            return stream.takeIf { it.playableDirectUrl != null }
        }
        return when (val result = DirectDebridPlaybackResolver.resolveToPlayableStream(stream, seasonNumber, episodeNumber)) {
            is DirectDebridPlayableResult.Success -> result.stream.takeIf { it.playableDirectUrl != null }
            else -> {
                log.w { "S${seasonNumber}E$episodeNumber: debrid link not available ($result), trying the next stream" }
                null
            }
        }
    }

    private suspend fun hlsSpecFor(batch: SeasonDownloadBatch, stream: StreamItem): HlsDownloadSpec? {
        val url = stream.playableDirectUrl ?: return null
        return when (val load = loadHlsDownloadChoices(url, DownloadsRepository.downloadRequestHeaders(stream))) {
            is HlsChoicesLoad.Problem -> {
                log.w { "HLS stream of ${batch.parentMetaId} can't be downloaded: ${load.problem}" }
                null
            }
            is HlsChoicesLoad.Ready -> load.choices.specFor(batch.hls ?: HlsDownloadPreferences(), load.durationSeconds)
        }
    }

    private fun enqueueEpisode(
        batch: SeasonDownloadBatch,
        episode: SeasonDownloadEpisode,
        stream: StreamItem,
        hlsSpec: HlsDownloadSpec?,
        fallbackSource: String?,
    ): DownloadEnqueueResult {
        val result = DownloadsRepository.enqueueFromStream(
            contentType = batch.contentType,
            videoId = episode.videoId,
            parentMetaId = batch.parentMetaId,
            parentMetaType = batch.parentMetaType,
            title = batch.title,
            logo = batch.logo,
            poster = batch.poster,
            background = batch.background,
            seasonNumber = batch.seasonNumber,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title,
            episodeThumbnail = episode.thumbnail,
            stream = stream,
            hls = hlsSpec,
        )
        val download = DownloadsRepository.findEpisodeDownload(batch.parentMetaId, batch.seasonNumber, episode.episodeNumber)
        if ((result == DownloadEnqueueResult.Started || result == DownloadEnqueueResult.Replaced) && download != null) {
            log.i {
                "S${batch.seasonNumber}E${episode.episodeNumber} handed to the downloads as ${download.id}" +
                    (fallbackSource?.let { " (fallback source: $it)" } ?: "")
            }
            updateEpisode(batch.id, episode.episodeNumber) {
                it.copy(
                    state = SeasonEpisodeState.Downloading,
                    downloadId = download.id,
                    sourceKey = SeasonStreamMatcher.keyOf(stream),
                    fallbackSource = fallbackSource,
                    message = null,
                )
            }
        } else {
            log.w { "S${batch.seasonNumber}E${episode.episodeNumber} could not be downloaded: $result" }
        }
        return result
    }

    /** Follows the downloads handed over by the season downloads. */
    private fun syncWithDownloads(items: List<DownloadItem>) {
        val byId = items.associateBy { it.id }
        _batches.value.forEach { batch ->
            batch.episodes.filter { it.state == SeasonEpisodeState.Downloading && it.downloadId != null }.forEach { episode ->
                val item = byId[episode.downloadId]
                when {
                    item == null -> {
                        log.i { "S${batch.seasonNumber}E${episode.episodeNumber} was removed from the downloads" }
                        updateEpisode(batch.id, episode.episodeNumber) { it.copy(state = SeasonEpisodeState.Skipped) }
                    }
                    item.status == DownloadStatus.Completed -> {
                        log.i { "S${batch.seasonNumber}E${episode.episodeNumber} downloaded" }
                        updateEpisode(batch.id, episode.episodeNumber) { it.copy(state = SeasonEpisodeState.Completed, message = null) }
                        adaptiveLimit = adaptiveLimit?.let { limit ->
                            (limit + 1).takeIf { it < DownloadsPreferences.seasonConcurrency.value }
                        }
                    }
                    item.status == DownloadStatus.Failed -> handleFailedDownload(batch, episode, item)
                }
            }
        }
    }

    private fun handleFailedDownload(batch: SeasonDownloadBatch, episode: SeasonDownloadEpisode, item: DownloadItem) {
        val message = item.errorMessage
        if (message != null && RATE_LIMIT_ERROR.containsMatchIn(message)) {
            val attempts = episode.attempts + 1
            if (attempts > MAX_RATE_LIMIT_ATTEMPTS) {
                log.w { "S${batch.seasonNumber}E${episode.episodeNumber}: still rate limited after $attempts attempts" }
                updateEpisode(batch.id, episode.episodeNumber) { it.copy(state = SeasonEpisodeState.Failed, attempts = attempts, message = message) }
                return
            }
            val pauseMs = (RATE_LIMIT_PAUSE_MS shl (attempts - 1)).coerceAtMost(MAX_RATE_LIMIT_PAUSE_MS)
            val limit = (adaptiveLimit ?: DownloadsPreferences.seasonConcurrency.value)
            adaptiveLimit = (limit / 2).coerceAtLeast(1)
            _cooldowns.value = _cooldowns.value + (batch.id to DownloadsClock.nowEpochMs() + pauseMs)
            log.w {
                "Rate limited on S${batch.seasonNumber}E${episode.episodeNumber} ($message): season paused for ${pauseMs / 1000}s, " +
                    "downloads at the same time lowered to $adaptiveLimit"
            }
            // The link may have expired meanwhile: the episode is looked up again after the pause.
            DownloadsRepository.cancelDownload(item.id)
            updateEpisode(batch.id, episode.episodeNumber) {
                it.copy(state = SeasonEpisodeState.Pending, downloadId = null, attempts = attempts, message = message)
            }
            return
        }
        log.w { "S${batch.seasonNumber}E${episode.episodeNumber} download failed: $message" }
        DownloadsRepository.cancelDownload(item.id)
        updateEpisode(batch.id, episode.episodeNumber) { it.copy(downloadId = null) }
        registerFailure(batch.id, episode.episodeNumber, episode.sourceKey, message)
    }

    /** Another attempt with another stream, until [MAX_ATTEMPTS]; then the episode is failed. */
    private fun registerFailure(batchId: String, episodeNumber: Int, sourceKey: String?, message: String?) {
        updateEpisode(batchId, episodeNumber) { episode ->
            val attempts = episode.attempts + 1
            val failedSources = (episode.failedSources + listOfNotNull(sourceKey)).distinct()
            if (attempts >= MAX_ATTEMPTS) {
                log.w { "Episode $episodeNumber of $batchId failed after $attempts attempt(s)" }
                episode.copy(state = SeasonEpisodeState.Failed, attempts = attempts, failedSources = failedSources, message = message)
            } else {
                log.i { "Episode $episodeNumber of $batchId: attempt $attempts failed, trying another stream" }
                episode.copy(state = SeasonEpisodeState.Pending, attempts = attempts, failedSources = failedSources, message = message)
            }
        }
    }

    private fun nextPendingEpisode(now: Long): Pair<SeasonDownloadBatch, SeasonDownloadEpisode>? =
        _batches.value
            .filter { !it.paused && (_cooldowns.value[it.id] ?: 0L) <= now }
            .sortedBy { it.createdAtEpochMs }
            .firstNotNullOfOrNull { batch ->
                batch.episodes.firstOrNull { it.state == SeasonEpisodeState.Pending }?.let { batch to it }
            }

    /** Episodes still to start that could start now (or, with [ignoreWaits], after a pause or the spacing). */
    private fun hasStartableWork(ignoreWaits: Boolean): Boolean {
        if (activeDownloads() >= effectiveLimit()) return false
        val now = DownloadsClock.nowEpochMs()
        return _batches.value.any { batch ->
            !batch.paused && batch.pendingCount > 0 && (ignoreWaits || (_cooldowns.value[batch.id] ?: 0L) <= now)
        }
    }

    /** Season episodes whose download is running (a paused one leaves its slot free). */
    private fun activeDownloads(): Int {
        val items = DownloadsRepository.uiState.value.items.associateBy { it.id }
        return _batches.value.sumOf { batch ->
            batch.episodes.count { episode ->
                episode.state == SeasonEpisodeState.Downloading &&
                    items[episode.downloadId]?.status == DownloadStatus.Downloading
            }
        }
    }

    private fun effectiveLimit(): Int {
        val setting = DownloadsPreferences.seasonConcurrency.value
        return adaptiveLimit?.coerceAtMost(setting) ?: setting
    }

    private fun schedulePump(delayMs: Long) {
        if (delayedPumpJob?.isActive == true) return
        delayedPumpJob = scope.launch {
            delay(delayMs.coerceAtLeast(MIN_PUMP_DELAY_MS))
            pump()
        }
    }

    private fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
        lookupEpisodeKey = null
    }

    private fun removeBatch(batchId: String, deleteUnfinished: Boolean) {
        val batch = batch(batchId) ?: return
        if (lookupEpisodeKey?.startsWith("$batchId|") == true) cancelLookup()
        _cooldowns.value = _cooldowns.value - batchId
        if (deleteUnfinished) {
            batch.episodes.filter { it.state == SeasonEpisodeState.Downloading }
                .mapNotNull { it.downloadId }
                .forEach(DownloadsRepository::cancelDownload)
        }
        _batches.value = _batches.value.filterNot { it.id == batchId }
        persist()
    }

    private fun batch(batchId: String): SeasonDownloadBatch? = _batches.value.firstOrNull { it.id == batchId }

    private fun updateBatch(batchId: String, transform: (SeasonDownloadBatch) -> SeasonDownloadBatch) {
        var changed = false
        _batches.value = _batches.value.map { batch ->
            if (batch.id == batchId) transform(batch).also { changed = it != batch } else batch
        }
        if (changed) persist()
    }

    private fun updateEpisode(batchId: String, episodeNumber: Int, transform: (SeasonDownloadEpisode) -> SeasonDownloadEpisode) {
        updateBatch(batchId) { batch ->
            batch.copy(episodes = batch.episodes.map { if (it.episodeNumber == episodeNumber) transform(it) else it })
        }
    }

    private fun persist() {
        DownloadsStorage.saveSeasonBatchesPayload(json.encodeToString(StoredSeasonDownloads.serializer(), StoredSeasonDownloads(_batches.value)))
    }

    private fun decode(payload: String?): List<SeasonDownloadBatch> {
        if (payload.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(StoredSeasonDownloads.serializer(), payload).batches }
            .onFailure { error -> log.e(error) { "Could not read the season downloads" } }
            .getOrDefault(emptyList())
    }

    // endregion

    private const val LOOKUP_TIMEOUT_MS = 45_000L
    private const val LOOKUP_SPACING_MS = 4_000L
    private const val LOOKUP_JITTER_MS = 3_000L
    private const val MIN_PUMP_DELAY_MS = 250L
    private const val MAX_CANDIDATES_PER_LOOKUP = 4
    private const val MAX_ATTEMPTS = 3
    private const val MAX_RATE_LIMIT_ATTEMPTS = 4
    private const val RATE_LIMIT_PAUSE_MS = 60_000L
    private const val MAX_RATE_LIMIT_PAUSE_MS = 10 * 60_000L
    private const val BACKGROUND_SETTLE_MS = 1_000L
    private const val BACKGROUND_POLL_MS = 1_000L
    private val RATE_LIMIT_ERROR = Regex("""HTTP (403|429|503|509)\b""")
}

@Serializable
private data class StoredSeasonDownloads(
    val batches: List<SeasonDownloadBatch> = emptyList(),
)
