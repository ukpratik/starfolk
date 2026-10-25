// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk

import android.net.TrafficStats
import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NFR-006 / US-020 / D-028 on a real Android runtime: from a clean install, without ever answering "Yes" to stats, a
 * walk through onboarding, a quest stamp, the Shelter and SOS must not send or receive a single byte from this app's UID.
 * Run: `./gradlew :app:connectedPlayDebugAndroidTest` and `connectedFdroidDebugAndroidTest` (emulator or device).
 */
@RunWith(AndroidJUnit4::class)
class NoNetworkWithoutConsentTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** Without a live network the test would pass vacuously, so it refuses to run offline. */
    @Before fun requireANetwork() {
        // Asked through the test harness's shell: the fdroid app itself may not even query network state (D-028).
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(cmd: String) = ui.executeShellCommand(cmd).let { pfd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() } }
        assertWithMessage("this test needs airplane mode off").that(shell("settings get global airplane_mode_on").trim()).isEqualTo("0")
        assertWithMessage("this test needs a connected network").that(shell("dumpsys connectivity")).contains("CONNECTED")
    }

    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun bytes(): Long = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0) + TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)

    @Test fun walksTheAppWithoutTouchingTheNetwork() {
        val before = bytes()
        if (rule.onAllNodesWithText("Begin").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Begin").performClick()
            rule.onNodeWithText("Next").performScrollTo().performClick()
            rule.onNodeWithText("People", substring = true).performScrollTo().performClick()
            rule.onNodeWithText("OK · a small push", substring = true).performScrollTo().performClick()
        }
        waitFor("TODAY")
        if (rule.onAllNodesWithText("Stamp it ✓").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Stamp it ✓").performScrollTo().performClick()
            waitFor("how'd it feel?")
            rule.onNodeWithText("good").performClick()
            rule.onAllNodesWithText("Stamp it", substring = true)[0].performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("SKY").performClick(); rule.waitForIdle()
        rule.onNodeWithText("MEMORIES").performClick(); rule.waitForIdle()
        rule.onNodeWithText("SHELTER").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Need help now?")[0].performClick()
        waitFor("Talk to a crisis line")
        rule.onNodeWithText("Talk to a crisis line").performClick()
        rule.waitForIdle()
        Thread.sleep(3_000) // give any stray background work a chance to show up
        val after = bytes()
        assertWithMessage("network bytes moved by the app without consent").that(after - before).isEqualTo(0L)
    }
}
