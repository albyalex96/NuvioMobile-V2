package com.nuvio.app.features.livetv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.nuvio.app.features.settings.AppIconPlatform
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_sports_notification_channel_description
import nuvio.composeapp.generated.resources.live_tv_sports_notification_channel_name
import org.jetbrains.compose.resources.getString
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Game reminders with WorkManager: one job per reminder and chosen moment (kickoff, 5 minutes
 * before…), plus, with live alerts on, a chain of score checks that starts at kickoff.
 */
internal actual object LiveTvSportsNotificationPlatform {
    private val log = Logger.withTag("LiveTvSportsReminders")
    internal const val CHANNEL_ID = "live_tv_sports_reminders"
    internal const val WORK_TAG = "live_tv_sports_reminders"
    internal const val DEEP_LINK = "nuvio://sports"

    private var appContext: Context? = null

    actual val isSupported: Boolean = true

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun schedule(reminders: List<LiveTvSportsReminder>, options: LiveTvSportsReminderOptions) {
        val context = appContext ?: return
        ensureChannel(context)
        val workManager = WorkManager.getInstance(context)
        // Everything is replaced: a handful of jobs, and no stale one survives a change.
        workManager.cancelAllWorkByTag(WORK_TAG)
        val now = System.currentTimeMillis()
        var scheduled = 0
        reminders.forEach { reminder ->
            options.leadMinutes.forEach { lead ->
                val triggerAt = reminder.startMillis - lead * 60_000L
                // Date-only events ring once, in the morning of their day (09:00).
                val effectiveTrigger = if (reminder.hasTime) triggerAt else reminder.startMillis + 9 * 3_600_000L
                if (!reminder.hasTime && lead != options.leadMinutes.min()) return@forEach
                val delay = effectiveTrigger - now
                if (delay <= 0L) return@forEach
                val data = reminderData(reminder)
                    .putString(LiveTvSportsReminderWorker.KEY_KIND, LiveTvSportsReminderWorker.KIND_REMINDER)
                    .putInt(LiveTvSportsReminderWorker.KEY_LEAD, if (reminder.hasTime) lead else -1)
                    .build()
                val request = OneTimeWorkRequestBuilder<LiveTvSportsReminderWorker>()
                    .setInputData(data)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .addTag(WORK_TAG)
                    .build()
                workManager.enqueueUniqueWork("$WORK_TAG:${reminder.key}:$lead", ExistingWorkPolicy.REPLACE, request)
                scheduled++
            }
            if (options.liveAlertsEnabled && reminder.hasTime) {
                val interval = options.liveAlertIntervalMinutes.coerceAtLeast(1) * 60_000L
                val firstCheck = maxOf(reminder.startMillis + interval, now + interval)
                // Started more than 8 hours ago: the game is surely over.
                if (now - reminder.startMillis < 8 * 3_600_000L) {
                    enqueueLiveCheck(context, reminder, options.liveAlertIntervalMinutes, firstCheck - now)
                    scheduled++
                }
            }
        }
        log.i { "Scheduled $scheduled jobs for ${reminders.size} reminders" }
    }

    internal fun reminderData(reminder: LiveTvSportsReminder): Data.Builder = Data.Builder()
        .putString(LiveTvSportsReminderWorker.KEY_EVENT_KEY, reminder.key)
        .putString(LiveTvSportsReminderWorker.KEY_LEAGUE_ID, reminder.leagueId)
        .putString(LiveTvSportsReminderWorker.KEY_EVENT_ID, reminder.eventId)
        .putString(LiveTvSportsReminderWorker.KEY_TITLE, reminder.title)
        .putString(LiveTvSportsReminderWorker.KEY_LEAGUE_LABEL, reminder.leagueLabel)
        .putLong(LiveTvSportsReminderWorker.KEY_START, reminder.startMillis)

    /**
     * [fromWorker]: the next check planned by the running one, queued after it (replacing the
     * running work would cancel it).
     */
    internal fun enqueueLiveCheck(
        context: Context,
        reminder: LiveTvSportsReminder,
        intervalMinutes: Int,
        delayMillis: Long,
        fromWorker: Boolean = false,
    ) {
        val data = reminderData(reminder)
            .putString(LiveTvSportsReminderWorker.KEY_KIND, LiveTvSportsReminderWorker.KIND_LIVE_CHECK)
            .putInt(LiveTvSportsReminderWorker.KEY_INTERVAL, intervalMinutes)
            .build()
        val request = OneTimeWorkRequestBuilder<LiveTvSportsReminderWorker>()
            .setInputData(data)
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "$WORK_TAG:${reminder.key}:live",
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                request,
            )
    }

    actual fun showTestNotification(title: String, body: String) {
        val context = appContext ?: return
        notify(context, "test", title, body)
    }

    internal fun notify(context: Context, id: String, title: String, body: String) {
        ensureChannel(context)
        val launch = Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(context)
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(DEEP_LINK)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            abs(id.hashCode()),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(abs(id.hashCode()), notification) }
            .onFailure { log.w(it) { "Notification not shown (permission?)" } }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            runBlocking { getString(Res.string.live_tv_sports_notification_channel_name) },
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = runBlocking { getString(Res.string.live_tv_sports_notification_channel_description) }
        }
        manager.createNotificationChannel(channel)
    }
}
