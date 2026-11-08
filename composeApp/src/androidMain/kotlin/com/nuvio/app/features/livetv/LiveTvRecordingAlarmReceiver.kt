package com.nuvio.app.features.livetv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.nuvio.app.features.settings.ThemeSettingsStorage

/** Starts a scheduled recording at its time, and arms them again after a reboot. */
class LiveTvRecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val log = Logger.withTag("LiveTvRecording")
        LiveTvRecordingPlatform.initialize(context)
        ThemeSettingsStorage.initialize(context.applicationContext)
        when (intent.action) {
            ACTION_ALARM -> {
                val id = intent.getStringExtra(LiveTvRecordingService.EXTRA_ID) ?: return
                log.i { "Alarm for recording $id" }
                LiveTvRecordingPlatform.startService(context, id)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> LiveTvRecordingsRepository.rearmAll()
        }
    }

    companion object {
        const val ACTION_ALARM = "com.nuvio.app.livetv.RECORDING_ALARM"
    }
}
