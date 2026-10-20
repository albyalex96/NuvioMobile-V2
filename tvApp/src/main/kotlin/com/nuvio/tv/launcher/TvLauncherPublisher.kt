package com.nuvio.tv.launcher

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.BasePreviewProgram
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.ContinueWatchingLaunchAction
import com.nuvio.app.core.deeplink.buildContinueWatchingDeepLinkUrl
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.tv.R
import com.nuvio.tv.TvActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val log = Logger.withTag("TvWatchNext")

/**
 * Publishes the "Continue watching" row to the Android TV launcher in two places (as NuvioTV):
 * - an app channel ("Nuvio · Continue watching" row of the home screen). The launcher lists
 *   Play Next sources only for apps that own a channel, so the channel is also what makes the
 *   second place work;
 * - the system "Play next" / "Watch next" row.
 *
 * Entries are keyed by [internalKey] so later syncs update, add and remove the same rows.
 * The launcher long-press menu belongs to the system (apps cannot add entries to it), so the
 * per-item options are shown by the app itself when the entry is opened with
 * [ContinueWatchingLaunchAction.Menu].
 */
object TvLauncherPublisher {
    /** The launcher shows a handful of entries per row; more only adds provider work. */
    private const val MAX_PROGRAMS = 20
    private const val KEY_PREFIX = "nuvio-cw:"
    private const val CHANNEL_KEY = "nuvio-cw-channel"
    private const val PREFERENCES_NAME = "nuvio_tv_launcher"
    private const val PREF_CHANNEL_ID = "channel_id"

    private val mutex = Mutex()

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private fun Context.isLeanback(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Mirrors [items] (already in display order) on the launcher; an empty list clears the rows. */
    suspend fun sync(
        context: Context,
        items: List<ContinueWatchingItem>,
        action: ContinueWatchingLaunchAction,
    ) {
        if (!supported) return
        val appContext = context.applicationContext
        if (!appContext.isLeanback()) {
            log.d { "No leanback launcher on this device, nothing to publish" }
            return
        }
        val desired = items
            .filter { !it.isReleaseAlert && it.parentMetaId.isNotBlank() }
            .distinctBy(::internalKey)
            .take(MAX_PROGRAMS)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching { syncChannel(appContext, desired, action) }
                    .onFailure { error -> log.e(error) { "Launcher channel sync failed" } }
                runCatching { syncWatchNext(appContext, desired, action) }
                    .onFailure { error -> log.e(error) { "Watch Next sync failed" } }
            }
        }
    }

    /** Removes the channel and every Play Next entry published by this app (feature turned off). */
    suspend fun clear(context: Context) {
        if (!supported) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val channelId = storedChannelId(appContext)
                    if (channelId != null) {
                        // Deleting the channel also deletes its programs.
                        appContext.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null)
                        preferences(appContext).edit().remove(PREF_CHANNEL_ID).apply()
                        log.i { "Launcher channel $channelId removed" }
                    }
                    val existing = queryWatchNext(appContext)
                    existing.forEach { program -> deleteWatchNext(appContext, program.id) }
                    if (existing.isNotEmpty()) log.i { "Watch Next cleared (${existing.size} entries)" }
                }.onFailure { error -> log.e(error) { "Launcher clear failed" } }
            }
        }
    }

    // --- App channel ---

    @SuppressLint("RestrictedApi")
    private fun syncChannel(
        context: Context,
        desired: List<ContinueWatchingItem>,
        action: ContinueWatchingLaunchAction,
    ) {
        val channelId = ensureChannel(context) ?: return
        val existing = queryChannelPrograms(context, channelId)
        var inserted = 0
        var updated = 0
        var skippedRemoved = 0
        desired.forEachIndexed { index, item ->
            val key = internalKey(item)
            val current = existing[key]?.firstOrNull()
            if (current != null && !current.isBrowsable) {
                skippedRemoved++
                return@forEachIndexed
            }
            val values = PreviewProgram.Builder()
                .setChannelId(channelId)
                .fill(context, item, action, key)
                // Higher weight = earlier in the row.
                .setWeight(MAX_PROGRAMS - index)
                .build()
                .toContentValues()
            if (current == null) {
                context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)
                    ?.let { inserted++ }
                    ?: log.w { "Channel program insert rejected for $key" }
            } else if (values.differsFrom(current.toContentValues())) {
                context.contentResolver.update(TvContractCompat.buildPreviewProgramUri(current.id), values, null, null)
                updated++
            }
            // Duplicates of the same key (older versions, crashes): keep only the first row.
            existing[key]?.drop(1)?.forEach { duplicate ->
                context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(duplicate.id), null, null)
            }
        }
        val desiredKeys = desired.mapTo(HashSet(), ::internalKey)
        val stale = existing.filterKeys { it !in desiredKeys }.values.flatten()
        stale.forEach { program ->
            context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(program.id), null, null)
        }
        log.i {
            "Launcher channel $channelId synced: ${desired.size} entries ($inserted new, $updated updated, " +
                "${stale.size} removed, $skippedRemoved hidden by the user)"
        }
    }

    /** Id of the app channel, created (and made browsable) the first time. */
    @SuppressLint("RestrictedApi")
    private fun ensureChannel(context: Context): Long? {
        val prefs = preferences(context)
        storedChannelId(context)?.let { stored ->
            val exists = context.contentResolver.query(
                TvContractCompat.buildChannelUri(stored),
                arrayOf(TvContractCompat.Channels._ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } == true
            if (exists) return stored
            log.w { "Launcher channel $stored no longer exists, creating it again" }
            prefs.edit().remove(PREF_CHANNEL_ID).apply()
        }

        // App data cleared but the channel survived: reuse it instead of adding a second row.
        val orphan = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            var found: Long? = null
            while (found == null && cursor.moveToNext()) {
                if (cursor.getString(1) == CHANNEL_KEY) found = cursor.getLong(0)
            }
            found
        }
        if (orphan != null) {
            prefs.edit().putLong(PREF_CHANNEL_ID, orphan).apply()
            log.i { "Reusing launcher channel $orphan" }
            return orphan
        }

        val appLink = Intent(Intent.ACTION_MAIN)
            .setClassName(context.packageName, TvActivity::class.java.name)
            .toUri(Intent.URI_INTENT_SCHEME)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(context.getString(R.string.tv_launcher_channel_name))
            .setAppLinkIntentUri(Uri.parse(appLink))
            .setInternalProviderId(CHANNEL_KEY)
            .build()
        val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
        if (uri == null) {
            log.w { "Launcher channel insert rejected" }
            return null
        }
        val id = ContentUris.parseId(uri)
        prefs.edit().putLong(PREF_CHANNEL_ID, id).apply()
        writeChannelLogo(context, id)
        // The first channel of an app is shown without asking the user.
        TvContractCompat.requestChannelBrowsable(context, id)
        log.i { "Launcher channel $id created" }
        return id
    }

    @SuppressLint("RestrictedApi")
    private fun queryChannelPrograms(context: Context, channelId: Long): Map<String, List<PreviewProgram>> =
        context.contentResolver.query(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            PreviewProgram.PROJECTION,
            // No selection: some providers (Fire OS) reject it; the rows are filtered here.
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val program = PreviewProgram.fromCursor(cursor)
                    if (program.channelId == channelId && program.internalProviderId?.startsWith(KEY_PREFIX) == true) {
                        add(program)
                    }
                }
            }
        }.orEmpty().groupBy { it.internalProviderId.orEmpty() }

    private fun writeChannelLogo(context: Context, channelId: Long) {
        runCatching {
            val icon = context.packageManager.getApplicationIcon(context.packageName)
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            context.contentResolver.openOutputStream(TvContractCompat.buildChannelLogoUri(channelId))?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.onFailure { error -> log.w(error) { "Launcher channel logo not written" } }
    }

    private fun storedChannelId(context: Context): Long? =
        preferences(context).getLong(PREF_CHANNEL_ID, -1L).takeIf { it >= 0L }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // --- Play next ---

    @SuppressLint("RestrictedApi")
    private fun syncWatchNext(
        context: Context,
        desired: List<ContinueWatchingItem>,
        action: ContinueWatchingLaunchAction,
    ) {
        val existing = queryWatchNext(context).associateBy { it.internalProviderId.orEmpty() }
        var inserted = 0
        var updated = 0
        var skippedRemoved = 0

        // Newest first: the launcher orders entries by last engagement time.
        val now = System.currentTimeMillis()
        desired.forEachIndexed { index, item ->
            val key = internalKey(item)
            val current = existing[key]
            if (current != null && !current.isBrowsable) {
                // Removed by the user from the launcher: do not bring it back for the same episode.
                skippedRemoved++
                return@forEachIndexed
            }
            val values = WatchNextProgram.Builder()
                .setWatchNextType(
                    if (item.isNextUp) {
                        TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                    } else {
                        TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                    },
                )
                .setLastEngagementTimeUtcMillis(
                    current
                        ?.takeIf { it.lastPlaybackPositionMillis.toLong() == item.resumePositionMs.coerceAtMost(Int.MAX_VALUE.toLong()) }
                        ?.lastEngagementTimeUtcMillis
                        ?: (now - index * 1_000L),
                )
                .fill(context, item, action, key)
                .build()
                .toContentValues()
            if (current == null) {
                context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                    ?.let { inserted++ }
                    ?: log.w { "Watch Next insert rejected for $key" }
            } else if (values.differsFrom(current.toContentValues())) {
                context.contentResolver.update(TvContractCompat.buildWatchNextProgramUri(current.id), values, null, null)
                updated++
            }
        }

        val desiredKeys = desired.mapTo(HashSet(), ::internalKey)
        val stale = existing.filterKeys { it !in desiredKeys }.values
        stale.forEach { program -> deleteWatchNext(context, program.id) }

        log.i {
            "Watch Next synced: ${desired.size} entries ($inserted new, $updated updated, " +
                "${stale.size} removed, $skippedRemoved hidden by the user), action $action"
        }
    }

    @SuppressLint("RestrictedApi")
    private fun queryWatchNext(context: Context): List<WatchNextProgram> =
        context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val program = WatchNextProgram.fromCursor(cursor)
                    // The provider only returns this app's rows; the prefix skips foreign ones anyway.
                    if (program.internalProviderId?.startsWith(KEY_PREFIX) == true) add(program)
                }
            }
        }.orEmpty()

    private fun deleteWatchNext(context: Context, programId: Long) {
        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(programId), null, null)
    }

    // --- Shared ---

    /** Fields common to channel and Play Next entries. */
    private fun <B : BasePreviewProgram.Builder<*>> B.fill(
        context: Context,
        item: ContinueWatchingItem,
        action: ContinueWatchingLaunchAction,
        key: String,
    ): B {
        val isEpisode = item.seasonNumber != null && item.episodeNumber != null
        // Explicit intent: the phone app (same nuvio:// scheme) must not intercept the entry.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(buildContinueWatchingDeepLinkUrl(item, action)))
            .setClassName(context.packageName, TvActivity::class.java.name)
        val landscapeArtwork = item.episodeThumbnail ?: item.background ?: item.imageUrl
        setType(if (isEpisode) TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
        setTitle(item.title)
        if (isEpisode) {
            setSeasonNumber(item.seasonNumber ?: 0)
            setEpisodeNumber(item.episodeNumber ?: 0)
            item.episodeTitle?.takeIf(String::isNotBlank)?.let(::setEpisodeTitle)
        }
        item.pauseDescription?.takeIf(String::isNotBlank)?.let(::setDescription)
        when {
            landscapeArtwork != null -> {
                setPosterArtUri(Uri.parse(landscapeArtwork))
                setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            }
            item.poster != null -> {
                setPosterArtUri(Uri.parse(item.poster))
                setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            }
        }
        item.logo?.let { setLogoUri(Uri.parse(it)) }
        val durationMs = item.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val resumeFraction = item.resumeProgressFraction ?: item.progressFraction
        when {
            item.isNextUp -> Unit
            durationMs > 0 -> {
                setDurationMillis(durationMs)
                setLastPlaybackPositionMillis(item.resumePositionMs.coerceIn(0L, durationMs.toLong()).toInt())
            }
            resumeFraction > 0f -> {
                // Progress known only as a percentage (Trakt/Simkl): synthetic values for the bar.
                setDurationMillis(SYNTHETIC_DURATION_MS)
                setLastPlaybackPositionMillis((resumeFraction * SYNTHETIC_DURATION_MS).toInt())
            }
        }
        setIntent(intent)
        setInternalProviderId(key)
        return this
    }

    private const val SYNTHETIC_DURATION_MS = 100_000

    private fun internalKey(item: ContinueWatchingItem): String =
        "$KEY_PREFIX${item.parentMetaId}:${item.seasonNumber ?: -1}:${item.episodeNumber ?: -1}"

    /** True when a column we write differs (the stored row also has provider-managed columns). */
    private fun ContentValues.differsFrom(stored: ContentValues): Boolean =
        keySet().any { column -> get(column)?.toString() != stored.get(column)?.toString() }
}
