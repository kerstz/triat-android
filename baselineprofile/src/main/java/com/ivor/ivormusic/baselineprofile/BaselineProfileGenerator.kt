package com.ivor.ivormusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records the classes and methods Triat touches on the paths a user actually
 * walks, so ART can compile them ahead of time instead of interpreting them
 * and then JIT-ing them while the screen is in front of the user.
 *
 * Run it against a rooted or userdebug device - the Triat_Bench AVD, not
 * Pixel_10, which is a Google Play image and refuses `adb root`. The generated
 * profile is architecture-independent: recording on an x86_64 emulator
 * produces a profile that is valid on an arm64 phone, because the rules name
 * method signatures rather than machine code.
 *
 * The journey below deliberately stops at what works signed out and offline-ish:
 * anything that needs a YouTube session would record a different, and on a
 * fresh device unreachable, path.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = InstrumentationRegistry.getArguments().getString(
            "targetAppId"
        ) ?: error("targetAppId not passed as instrumentation runner arg"),
        // The default is one iteration, which records a cold start and little
        // else. Several passes let the scrolls land on warmed-up frames too.
        maxIterations = 6,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Home: the tab that opens first, and the shelves that carry its cost.
        device.waitForIdle()
        scrollVertically()

        // Each tab is a separate Compose tree and a separate set of classes.
        openTab("Rechercher", "Search")
        device.waitForIdle()

        openTab("Bibliothèque", "Library")
        device.waitForIdle()
        scrollVertically()

        openTab("Paramètres", "Settings")
        device.waitForIdle()
        scrollVertically()
        device.pressBack()

        openTab("Accueil", "Home")
        device.waitForIdle()
    }

    /**
     * The bottom bar is labelled in the device's language, so each tab is
     * tried under both its French and English label. A tab that cannot be
     * found is skipped rather than failing the run: a missing screen costs
     * some coverage, a thrown exception costs the whole profile.
     */
    private fun androidx.test.uiautomator.UiDevice.findTab(vararg labels: String) =
        labels.firstNotNullOfOrNull { label ->
            wait(Until.findObject(By.text(label)), 2_000)
        }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openTab(vararg labels: String) {
        device.findTab(*labels)?.click()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollVertically() {
        val scrollable = device.wait(
            Until.findObject(By.scrollable(true)),
            2_000
        ) ?: return
        scrollable.setGestureMargin(device.displayWidth / 5)
        repeat(2) { scrollable.scroll(Direction.DOWN, 0.8f) }
        scrollable.scroll(Direction.UP, 0.8f)
        device.waitForIdle()
    }
}
