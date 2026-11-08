package com.nuvio.app.features.livetv

/** Game reminders are Android only for now. */
internal actual object LiveTvSportsNotificationPlatform {
    actual val isSupported: Boolean = false

    actual fun schedule(reminders: List<LiveTvSportsReminder>, options: LiveTvSportsReminderOptions) = Unit

    actual fun showTestNotification(title: String, body: String) = Unit
}
