// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.ukpratik.starfolk.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The M-B daily loop end to end on a fresh install (US-001, 003, 004, 005, 006, 014). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DailyLoopFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun onboard() {
        waitFor("Begin")
        rule.onNodeWithText("Begin").performClick()
        rule.onNodeWithContentDescription("Plum").performClick()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("People", substring = true).performClick()
        rule.onNodeWithText("OK · a small push", substring = true).performClick()
        waitFor("Hold to tear")
    }

    @Test fun `SOS is reachable before onboarding`() {
        waitFor("Begin")
        rule.onNodeWithContentDescription("Need help now?").performClick()
        waitFor("Talk to a crisis line")
        rule.onNodeWithText("Calm my body", substring = true).assertIsDisplayed()
    }

    @Test fun `the Shelter fast path, a step done, and no racing ahead`() {
        waitFor("Going through something")
        rule.onNodeWithText("Going through something", substring = true).performClick()
        waitFor("Come in")
        rule.onNodeWithText("Come in").performClick()
        waitFor("BREAKUP")
        rule.onNodeWithText("BREAKUP", substring = true).performClick()
        waitFor("BREAKUP · DAY 1")
        rule.onNodeWithText("I did this").performScrollTo().performClick()
        waitFor("done today")
        waitFor("Day 2 opens tomorrow")
        rule.onNodeWithText("I slipped today").performScrollTo().performClick()
        waitFor("Day 1 again is still progress.")
    }

    @Test fun `three taps reach the first ticket, which can be swapped twice`() {
        onboard()
        rule.onNodeWithText("Swap · 2 left", substring = true).performClick()
        waitFor("Swap · 1 left")
        rule.onNodeWithText("Swap · 1 left", substring = true).performClick()
        waitFor("This one's yours today")
        rule.onNodeWithText("This one's yours today").assertIsNotEnabled()
    }

    @Test fun `commit works without the gesture, through the TalkBack action`() {
        onboard()
        val node = rule.onNode(SemanticsMatcher("has Commit action") { n ->
            n.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == "Commit" } == true
        }).fetchSemanticsNode()
        val commit = node.config[SemanticsActions.CustomActions].first { it.label == "Commit" }
        rule.runOnIdle { commit.action() }
        waitFor("When will you do it?")
        rule.onNodeWithText("Right now").performClick()
        waitFor("doing it now")
    }

    @Test fun `stamping a quest asks how it felt, then shows why it works`() {
        onboard()
        rule.onNodeWithText("Stamp it ✓").performClick()
        waitFor("how'd it feel?")
        rule.onNodeWithText("good").performClick()
        rule.onNode(hasText("Stamp it", substring = true) and hasText("★", substring = true)).performClick()
        waitFor("WHY THIS WORKS")
        waitFor("Done for today")
    }
}
