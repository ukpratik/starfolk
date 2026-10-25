// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records launch → Today → Sky → Shelter → SOS into the shipped Baseline Profile (NFR-001).
 * Run: `./gradlew :app:generatePlayReleaseBaselineProfile` with a device attached.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // A fresh install starts at onboarding (US-001): walk the 3 taps once, so Today is profiled too.
        if (device.wait(Until.hasObject(By.text("Begin")), 3_000)) {
            device.findObject(By.text("Begin"))?.click()
            device.wait(Until.hasObject(By.text("Next")), TIMEOUT_MS); device.findObject(By.text("Next"))?.click()
            device.wait(Until.hasObject(By.textContains("People")), TIMEOUT_MS); device.findObject(By.textContains("People"))?.click()
            device.wait(Until.hasObject(By.textContains("small push")), TIMEOUT_MS); device.findObject(By.textContains("small push"))?.click()
        }
        device.wait(Until.hasObject(By.text("TODAY")), TIMEOUT_MS)
        for (tab in listOf("SKY", "MEMORIES", "SHELTER", "TODAY")) {
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
        device.findObject(By.desc("Need help now?"))?.click()
        device.waitForIdle()
        device.pressBack()
    }
}

internal const val PACKAGE = "io.github.ukpratik.starfolk"
internal const val TIMEOUT_MS = 10_000L
