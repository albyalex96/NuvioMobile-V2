package com.nuvio.app.features.livetv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_sports_notification_final
import nuvio.composeapp.generated.resources.live_tv_sports_notification_score
import nuvio.composeapp.generated.resources.live_tv_sports_notification_starts_in
import nuvio.composeapp.generated.resources.live_tv_sports_notification_starts_now
import nuvio.composeapp.generated.resources.live_tv_sports_notification_today
import org.jetbrains.compose.resources.getString

/**
 * Runs a game reminder: either the notification itself, or one score check of a followed game
 * (which schedules the next one until the game is over).
 */
class LiveTvSportsReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val log = Logger.withTag("LiveTvSportsReminders")

    override suspend fun doWork(): Result {
        // WorkManager can start with the app closed, before MainActivity has run.
        EpisodeReleaseNotificationPlatform.initialize(applicationContext)
        LiveTvSportsNotificationPlatform.initialize(applicationContext)
        if (!EpisodeReleaseNotificationPlatform.notificationsAuthorized()) {
            log.w { "Sports reminder skipped: notifications not allowed" }
            return Result.success()
        }
        val reminder = LiveTvSportsReminder(
            key = inputData.getString(KEY_EVENT_KEY) ?: return Result.failure(),
            leagueId = inputData.getString(KEY_LEAGUE_ID) ?: return Result.failure(),
            eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure(),
            title = inputData.getString(KEY_TITLE) ?: return Result.failure(),
            leagueLabel = inputData.getString(KEY_LEAGUE_LABEL).orEmpty(),
            startMillis = inputData.getLong(KEY_START, 0L),
        )
        return when (inputData.getString(KEY_KIND)) {
            KIND_LIVE_CHECK -> liveCheck(reminder, inputData.getInt(KEY_INTERVAL, 10))
            else -> {
                showReminder(reminder, inputData.getInt(KEY_LEAD, 0))
                Result.success()
            }
        }
    }

    private suspend fun showReminder(reminder: LiveTvSportsReminder, lead: Int) {
        val body = when {
            lead < 0 -> getString(Res.string.live_tv_sports_notification_today, reminder.leagueLabel)
            lead == 0 -> getString(Res.string.live_tv_sports_notification_starts_now, reminder.leagueLabel)
            else -> getString(Res.string.live_tv_sports_notification_starts_in, reminder.leagueLabel, lead)
        }
        LiveTvSportsNotificationPlatform.notify(applicationContext, "${reminder.key}:$lead", reminder.title, body)
        log.i { "Reminder shown: ${reminder.title} (lead $lead)" }
    }

    /** One score check: notifies a changed score or the final result, then plans the next check. */
    private suspend fun liveCheck(reminder: LiveTvSportsReminder, intervalMinutes: Int): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scoreKey = "score:${reminder.key}"
        val finalKey = "final:${reminder.key}"
        val now = System.currentTimeMillis()
        val tooLate = now - reminder.startMillis > 8 * HOUR_MILLIS
        val league = liveTvSportsLeaguesById[reminder.leagueId]
        if (league == null || prefs.getBoolean(finalKey, false)) return Result.success()

        val window = LiveTvSportsWindow(listOf(sportsDayKey(reminder.startMillis - DAY_MILLIS), sportsDayKey(reminder.startMillis)))
        val event = runCatching { LiveTvSportsFeeds.fetch(league, window) }
            .onFailure { log.w { "Score check of ${reminder.title} failed: ${it.message}" } }
            .getOrNull()
            ?.firstOrNull { it.id == reminder.eventId }

        if (event != null && event.competitors.size >= 2) {
            val score = event.competitors.joinToString(" – ") { "${it.shortName} ${it.score.orEmpty()}".trim() }
            val previous = prefs.getString(scoreKey, null)
            when (event.state) {
                LiveTvSportsState.Finished -> {
                    LiveTvSportsNotificationPlatform.notify(
                        applicationContext,
                        "${reminder.key}:final",
                        reminder.title,
                        getString(Res.string.live_tv_sports_notification_final, score),
                    )
                    prefs.edit().putBoolean(finalKey, true).remove(scoreKey).apply()
                    log.i { "Final result notified: ${reminder.title} $score" }
                    return Result.success()
                }
                LiveTvSportsState.Live -> {
                    // The first check only records the score; later ones notify a change.
                    if (previous != null && previous != score) {
                        val detail = event.statusDetail.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
                        LiveTvSportsNotificationPlatform.notify(
                            applicationContext,
                            "${reminder.key}:score",
                            reminder.title,
                            getString(Res.string.live_tv_sports_notification_score, score + detail),
                        )
                        log.i { "Score change notified: ${reminder.title} $score" }
                    }
                    prefs.edit().putString(scoreKey, score).apply()
                }
                LiveTvSportsState.Upcoming -> log.d { "Score check: ${reminder.title} not started yet" }
            }
        }
        if (tooLate) {
            log.i { "Score checks of ${reminder.title} stopped (game over 8 hours ago)" }
            prefs.edit().remove(scoreKey).apply()
            return Result.success()
        }
        LiveTvSportsNotificationPlatform.enqueueLiveCheck(applicationContext, reminder, intervalMinutes, intervalMinutes * 60_000L, fromWorker = true)
        return Result.success()
    }

    companion object {
        private const val PREFS = "nuvio_live_tv_sports_reminders"
        const val KEY_KIND = "kind"
        const val KIND_REMINDER = "reminder"
        const val KIND_LIVE_CHECK = "live_check"
        const val KEY_EVENT_KEY = "event_key"
        const val KEY_LEAGUE_ID = "league_id"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_TITLE = "title"
        const val KEY_LEAGUE_LABEL = "league_label"
        const val KEY_START = "start"
        const val KEY_LEAD = "lead"
        const val KEY_INTERVAL = "interval"
    }
}
