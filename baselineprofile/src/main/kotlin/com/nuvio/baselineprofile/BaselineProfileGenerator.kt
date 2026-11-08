package com.nuvio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code run by a cold start, the home and a few scrolls of its rows.
 *
 * Before running: install the benchmark app, sign in and enable "remember last profile", so
 * the app opens straight on the home. The package is chosen with the `targetPackage`
 * instrumentation argument (default: the phone app).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val targetPackage: String =
        InstrumentationRegistry.getArguments().getString("targetPackage")
            ?: PHONE_PACKAGE

    @Test
    fun startupAndHome() = rule.collect(
        packageName = targetPackage,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // The home loads catalogs and posters after the first frame.
        device.waitForIdle(HOME_SETTLE_MS)
        Thread.sleep(HOME_SETTLE_MS)

        val isTv = targetPackage.contains(".tv")
        repeat(SCROLL_STEPS) {
            if (isTv) {
                device.pressDPadDown()
            } else {
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
                    ?: device.swipe(
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

    private companion object {
        const val PHONE_PACKAGE = "com.nuvioenhanced.alby.benchmark"
        const val HOME_SETTLE_MS = 6_000L
        const val STEP_SETTLE_MS = 800L
        const val SCROLL_STEPS = 6
    }
}
