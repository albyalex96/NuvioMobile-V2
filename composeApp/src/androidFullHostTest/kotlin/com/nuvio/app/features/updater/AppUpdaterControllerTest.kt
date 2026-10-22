package com.nuvio.app.features.updater

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import java.io.File
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppUpdaterControllerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: UpdatePreferences

    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_updater", Context.MODE_PRIVATE).edit().clear().commit()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        AndroidAppUpdaterPlatform.initialize(context)
        preferences = UpdatePreferences("1.1.0")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun forcedChecksDoNotOfferEqualOrOlderVersions() {
        listOf(AppVersionConfig.VERSION_NAME, "0.0.0").forEach { tag ->
            val controller = controller { Result.success(update(tag)) }
            controller.checkForUpdates(force = true, showNoUpdateFeedback = false)

            assertFalse(controller.uiState.value.isUpdateAvailable)
            assertFalse(controller.uiState.value.showDialog)
            assertNull(controller.uiState.value.update)
        }
    }

    @Test
    fun manualChecksCanShowAnIgnoredNewerRelease() {
        AppUpdaterPlatform.setIgnoredTag("999.0.0")
        val controller = controller { Result.success(update("999.0.0")) }

        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)
        assertFalse(controller.uiState.value.showDialog)
        controller.checkForUpdates(force = true, showNoUpdateFeedback = false)
        assertTrue(controller.uiState.value.showDialog)
        assertTrue(controller.uiState.value.isUpdateAvailable)
    }

    @Test
    fun channelChangeDiscardsPendingUpdate() {
        val controller = controller { Result.success(update("999.0.0")) }
        controller.checkForUpdates(force = true, showNoUpdateFeedback = false)
        assertTrue(controller.uiState.value.showDialog)

        preferences.setChannel(UpdateChannel.BETA)

        assertEquals(AppUpdaterUiState(updateChannel = UpdateChannel.BETA), controller.uiState.value)
    }

    @Test
    fun channelChangeChecksNewChannelAndIgnoresLateResults() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val oldResult = CompletableDeferred<Result<AppUpdate>>()
        val requestedChannels = mutableListOf<UpdateChannel>()
        val controller = controller { channel ->
            requestedChannels += channel
            if (channel == UpdateChannel.STABLE) {
                withContext(NonCancellable) { oldResult.await() }
            } else {
                Result.success(update("999.0.0-beta.1"))
            }
        }
        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)
        preferences.setChannel(UpdateChannel.BETA)
        oldResult.complete(Result.success(update("999.0.0")))

        assertEquals(listOf(UpdateChannel.STABLE, UpdateChannel.BETA), requestedChannels)
        assertEquals(UpdateChannel.BETA, controller.uiState.value.updateChannel)
        assertEquals("999.0.0-beta.1", controller.uiState.value.update?.tag)
        assertTrue(controller.uiState.value.showDialog)
        assertFalse(controller.uiState.value.isChecking)
    }

    @Test
    fun noEligibleReleaseDoesNotOpenErrorDialog() {
        val controller = controller { Result.failure(NoChannelReleaseException()) }
        controller.checkForUpdates(force = true, showNoUpdateFeedback = false)

        assertFalse(controller.uiState.value.showDialog)
        assertFalse(controller.uiState.value.isChecking)
        assertNull(controller.uiState.value.errorMessage)
    }

    @Test
    fun channelChangeCancelsDebugDownloadAndClearsItsState() {
        val controller = controller { error("Debug channel changes should not check for updates") }
        controller.showDebugTestUpdate()
        controller.downloadUpdate()
        assertTrue(controller.uiState.value.isDownloading)

        preferences.setChannel(UpdateChannel.BETA)

        assertEquals(AppUpdaterUiState(updateChannel = UpdateChannel.BETA), controller.uiState.value)
    }

    @Test
    fun backgroundDownloadStaysSilentAndShowsOnlyTheInstallBanner() {
        preferences.setAutoDownload(true)
        val apk = fakeApk()
        val download = CompletableDeferred<Result<String>>()
        val controller = controller(
            fetch = { Result.success(update("999.0.0")) },
            download = { _, _ -> download.await() },
        )

        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)
        assertTrue(controller.uiState.value.isDownloading)
        assertFalse(controller.uiState.value.showDialog)

        download.complete(Result.success(apk.absolutePath))

        assertFalse(controller.uiState.value.isDownloading)
        assertEquals(apk.absolutePath, controller.uiState.value.downloadedApkPath)
        assertTrue(controller.uiState.value.showDialog)
        assertEquals("999.0.0", preferences.pendingDownload()?.update?.tag)
    }

    @Test
    fun downloadedUpdateSurvivesRestartWithoutNetwork() {
        preferences.setAutoDownload(true)
        val apk = fakeApk()
        controller(
            fetch = { Result.success(update("999.0.0")) },
            download = { _, _ -> Result.success(apk.absolutePath) },
        ).checkForUpdates(force = false, showNoUpdateFeedback = false)

        val restarted = controller(
            fetch = { Result.failure(IllegalStateException("offline")) },
            download = { _, _ -> error("The downloaded APK must be reused") },
        )
        assertEquals(apk.absolutePath, restarted.uiState.value.downloadedApkPath)
        assertTrue(restarted.uiState.value.showDialog)

        restarted.checkForUpdates(force = false, showNoUpdateFeedback = false)
        assertEquals(apk.absolutePath, restarted.uiState.value.downloadedApkPath)
        assertEquals("999.0.0", restarted.uiState.value.update?.tag)
        assertTrue(restarted.uiState.value.showDialog)
    }

    @Test
    fun staleDownloadedUpdateIsDiscardedOnStartup() {
        val apk = fakeApk()
        preferences.setPendingDownload(
            PendingUpdateDownload(
                channel = preferences.channel.value.storedValue,
                apkPath = apk.absolutePath,
                update = update(AppVersionConfig.VERSION_NAME),
            ),
        )

        val controller = controller { Result.failure(NoChannelReleaseException()) }

        assertNull(controller.uiState.value.downloadedApkPath)
        assertNull(preferences.pendingDownload())
        assertFalse(apk.exists())
    }

    @Test
    fun newerReleaseReplacesTheDownloadedOne() {
        preferences.setAutoDownload(true)
        val oldApk = fakeApk("old.apk")
        val newApk = fakeApk("new.apk")
        var tag = "999.0.0"
        val controller = controller(
            fetch = { Result.success(update(tag)) },
            download = { update, _ -> Result.success(if (update.tag == "999.0.0") oldApk.absolutePath else newApk.absolutePath) },
        )
        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)
        assertEquals(oldApk.absolutePath, controller.uiState.value.downloadedApkPath)

        tag = "999.1.0"
        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)

        assertFalse(oldApk.exists())
        assertEquals(newApk.absolutePath, controller.uiState.value.downloadedApkPath)
        assertEquals("999.1.0", preferences.pendingDownload()?.update?.tag)
    }

    @Test
    fun disabledAutoDownloadOnlyShowsTheUpdateBanner() {
        val controller = controller(
            fetch = { Result.success(update("999.0.0")) },
            download = { _, _ -> error("Auto-download is off") },
        )
        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)

        assertTrue(controller.uiState.value.showDialog)
        assertFalse(controller.uiState.value.isDownloading)
        assertNull(controller.uiState.value.downloadedApkPath)
    }

    private fun fakeApk(name: String = "update.apk"): File =
        File(File(RuntimeEnvironment.getApplication().filesDir, "updates").apply { mkdirs() }, name)
            .apply { writeText("apk") }

    private fun controller(fetch: suspend (UpdateChannel) -> Result<AppUpdate>) =
        controller(fetch = fetch, download = { _, _ -> error("Unexpected download") })

    private fun controller(
        fetch: suspend (UpdateChannel) -> Result<AppUpdate>,
        download: suspend (AppUpdate, (Long, Long?) -> Unit) -> Result<String>,
    ) = AppUpdaterController(scope, preferences, fetch, download)

    private fun update(tag: String) = AppUpdate(
        tag = tag,
        title = tag,
        notes = "",
        releaseUrl = null,
        assetName = "app.apk",
        assetUrl = "https://example.com/app.apk",
        assetSizeBytes = null,
    )
}
