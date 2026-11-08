package com.nuvio.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code run by a cold start, the home and a few scrolls of its rows.
 *
 * The benchmark library reinstalls the app before recording (it wipes its data), so every run
 * starts from the sign-in screen. Like the original TV app, the generator then continues
 * without an account, creates a local profile when asked and installs Cinemeta, so the home has
 * catalogs. The package is chosen with the `targetPackage` instrumentation argument.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val targetPackage: String =
        InstrumentationRegistry.getArguments().getString("targetPackage")
            ?: PHONE_PACKAGE

    private val isTv: Boolean get() = targetPackage.contains(".tv")

    @Test
    fun startupAndHome() = rule.collect(
        packageName = targetPackage,
        includeInStartupProfile = true,
        // The home content changes between runs, so the profile rarely gets stable: cap the runs
        // (default 15) to keep the generation short.
        maxIterations = MAX_ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
        prepareHome()
        // The home loads catalogs and posters after the first frame.
        device.waitForIdle(HOME_SETTLE_MS)
        Thread.sleep(HOME_SETTLE_MS)
        browseHome()
    }

    /** First run after the reinstall: no account, local profile, Cinemeta. */
    private fun MacrobenchmarkScope.prepareHome() {
        val deadline = System.currentTimeMillis() + SETUP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            when {
                device.find(Labels.CONTINUE_WITHOUT_ACCOUNT) != null ->
                    device.find(Labels.CONTINUE_WITHOUT_ACCOUNT)?.click()

                device.find(Labels.WHO_IS_WATCHING) != null -> {
                    // The local profile is sometimes created by itself after a few seconds.
                    if (device.wait(Until.gone(Labels.WHO_IS_WATCHING), PROFILE_AUTO_WAIT_MS)) continue
                    createLocalProfile()
                }

                device.find(Labels.NO_CONTENT_SOURCES) != null -> installCinemeta()

                device.find(Labels.HOME_TAB) != null -> return
            }
            Thread.sleep(STEP_SETTLE_MS)
        }
    }

    private fun MacrobenchmarkScope.createLocalProfile() {
        device.find(Labels.MANAGE_PROFILES)?.click() ?: return
        device.wait(Until.hasObject(Labels.ADD_PROFILE), UI_TIMEOUT_MS)
        device.find(Labels.ADD_PROFILE)?.click() ?: return
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)
        device.findObject(By.clazz("android.widget.EditText"))?.let { field ->
            field.click()
            field.text = PROFILE_NAME
        } ?: return
        // New profile: the save button reads "Create Profile" (icon description or text).
        (device.find(Labels.SAVE_DESC) ?: device.find(Labels.SAVE))?.click()
        Thread.sleep(STEP_SETTLE_MS)
        // Back on the profile list: open the new profile.
        device.wait(Until.hasObject(By.text(PROFILE_NAME)), UI_TIMEOUT_MS)
        device.findObject(By.text(PROFILE_NAME))?.click()
    }

    private fun MacrobenchmarkScope.installCinemeta() {
        // A plain start: the app is already open, so startActivityAndWait would not see a launch.
        device.executeShellCommand(
            "am start -a android.intent.action.VIEW -d $CINEMETA_URL $targetPackage",
        )
        device.wait(Until.hasObject(By.textContains("Cinemeta")), INSTALL_TIMEOUT_MS)
        // The deep link opens the add-on settings: restart the app to land on the home.
        killProcess()
        startActivityAndWait()
    }

    private fun MacrobenchmarkScope.browseHome() {
        repeat(SCROLL_STEPS) {
            if (isTv) {
                device.pressDPadDown()
            } else {
                // A plain swipe: the home recomposes while loading, so a looked-up scrollable
                // node can go stale (StaleObjectException).
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    20,
                )
            }
            device.waitForIdle(STEP_SETTLE_MS)
        }
        if (isTv) {
            repeat(SCROLL_STEPS) {
                device.pressDPadRight()
                device.waitForIdle(STEP_SETTLE_MS)
            }
        }
    }

    private fun androidx.test.uiautomator.UiDevice.find(selector: BySelector): UiObject2? =
        runCatching { findObject(selector) }.getOrNull()

    /** Labels in English and Italian (the device language). */
    private object Labels {
        private fun any(vararg labels: String): BySelector =
            By.text(Pattern.compile(labels.joinToString("|") { Pattern.quote(it) }, Pattern.CASE_INSENSITIVE))

        private fun anyDesc(vararg labels: String): BySelector =
            By.desc(Pattern.compile(labels.joinToString("|") { Pattern.quote(it) }, Pattern.CASE_INSENSITIVE))

        val CONTINUE_WITHOUT_ACCOUNT = any("Continue Without Account", "Continua senza account")
        val WHO_IS_WATCHING = any("Who's watching?", "Chi sta guardando?")
        val MANAGE_PROFILES = any("Manage Profiles", "Gestisci profili")
        // The "+" tile has no description: its caption below the icon is a text.
        val ADD_PROFILE = any("Add Profile", "Aggiungi profilo")
        val SAVE = any("Create Profile", "Crea profilo", "Save", "Salva")
        val SAVE_DESC = anyDesc("Create Profile", "Crea profilo", "Save", "Salva")
        val NO_CONTENT_SOURCES = any("No content sources", "Nessuna sorgente di contenuti")
        val HOME_TAB = anyDesc("Home")
    }

    private companion object {
        const val PHONE_PACKAGE = "com.nuvioenhanced.alby.benchmark"
        const val CINEMETA_URL = "stremio://v3-cinemeta.strem.io/manifest.json"
        const val PROFILE_NAME = "Benchmark"
        const val SETUP_TIMEOUT_MS = 60_000L
        const val PROFILE_AUTO_WAIT_MS = 8_000L
        const val UI_TIMEOUT_MS = 5_000L
        const val INSTALL_TIMEOUT_MS = 15_000L
        const val HOME_SETTLE_MS = 6_000L
        const val STEP_SETTLE_MS = 800L
        const val SCROLL_STEPS = 6
        const val MAX_ITERATIONS = 6
    }
}
