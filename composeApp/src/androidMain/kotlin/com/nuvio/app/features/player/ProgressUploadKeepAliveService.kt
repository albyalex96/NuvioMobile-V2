package com.nuvio.app.features.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import co.touchlab.kermit.Logger
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Longest wait for the progress upload before the process is left to the system. */
private const val ProgressUploadMaxWaitMs = 5_000L

/**
 * Keeps the process running for a few seconds after the TV goes to standby, so the watch
 * progress upload started by the player can reach the server. A started service keeps the
 * process out of the cached (freezable) state and the partial wake lock keeps the CPU on;
 * the screen is not involved. It stops as soon as the upload ends or after
 * [ProgressUploadMaxWaitMs].
 */
class ProgressUploadKeepAliveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireWakeLock()
        scope.launch {
            val finished = WatchProgressRepository.awaitProgressUploads(ProgressUploadMaxWaitMs)
            if (finished) {
                log.i { "Progress upload finished, releasing the process" }
            } else {
                log.w { "Progress upload still running after ${ProgressUploadMaxWaitMs}ms, giving up" }
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nuvio:ProgressUpload")
            .apply {
                setReferenceCounted(false)
                // Safety timeout: released earlier in onDestroy.
                acquire(ProgressUploadMaxWaitMs + 1_000L)
            }
    }

    companion object {
        private val log = Logger.withTag("PlayerLifecycle")

        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, ProgressUploadKeepAliveService::class.java))
            }.onFailure { error ->
                // Background start limits: the upload still runs, only without the keep-alive.
                log.w(error) { "Could not start the progress upload keep-alive" }
            }
        }
    }
}
