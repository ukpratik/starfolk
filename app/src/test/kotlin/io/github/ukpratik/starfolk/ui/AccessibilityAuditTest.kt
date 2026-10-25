// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.starfolk.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * NFR-008 audit across the main screens, at 100% and 200% font scale: every clickable node has a spoken label and a
 * ≥ 48 dp touch target, and the key actions remain reachable when the text is huge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityAuditTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun labelOf(n: SemanticsNode): String {
        val cfg = n.config
        val own = (cfg.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + cfg.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        if (own.isNotBlank()) return own
        return n.children.joinToString(" ") { labelOf(it) }
    }

    /** Clickables with no label or a tiny target fail with the screen name, so the report says where. */
    private fun audit(screen: String) {
        rule.waitForIdle()
        val density = rule.density
        val failures = mutableListOf<String>()
        var clickables = 0
        fun walk(n: SemanticsNode) {
            if (n.config.contains(SemanticsActions.OnClick)) {
                clickables++
                val label = labelOf(n)
                if (label.isBlank()) failures += "unlabelled clickable at ${n.boundsInRoot}"
                with(density) {
                    val minSide = minOf(n.size.width, n.size.height).toDp()
                    // Compose enforces 48 dp touch targets for small clickables; check the reported touch bounds.
                    val touch = n.touchBoundsInRoot
                    val touchMin = minOf(touch.width, touch.height).toDp()
                    if (maxOf(minSide, touchMin) < 47.5.dp) failures += "small target (${minSide}) for '$label'"
                }
            }
            n.children.forEach(::walk)
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertWithMessage("$screen at fontScale ${RuntimeEnvironment.getQualifiers()}").that(failures).isEmpty()
        assertWithMessage("$screen: the audit found nothing to check").that(clickables).isAtLeast(2)
    }

    private fun tour() {
        waitFor("Begin")
        audit("welcome")
        rule.onNodeWithText("Begin").performClick()
        audit("onboarding: sky")
        rule.onNodeWithText("Next").performScrollTo().performClick()
        rule.onNodeWithText("People", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("OK · a small push", substring = true).performScrollTo().performClick()
        waitFor("Hold to tear")
        audit("today")
        rule.onNodeWithText("Hold to tear").performScrollTo()
        rule.onNodeWithText("Stamp it ✓").performScrollTo()
        rule.onNodeWithText("SKY").performClick(); audit("sky")
        rule.onNodeWithText("MEMORIES").performClick(); audit("memories")
        rule.onNodeWithText("SHELTER").performClick(); waitFor("Come in"); audit("shelter intro")
        rule.onNodeWithText("Come in").performScrollTo().performClick(); waitFor("BREAKUP"); audit("shelter picker")
        rule.onNodeWithText("BREAKUP", substring = true).performScrollTo().performClick(); waitFor("I did this"); audit("shelter day")
        rule.onNodeWithText("I did this").performScrollTo()
        // In the Shelter the help control is the plain pill (its text is its label).
        rule.onAllNodesWithText("Need help now?")[0].performClick(); waitFor("Talk to a crisis line"); audit("sos")
        rule.onNodeWithText("Talk to a crisis line").performScrollTo().performClick(); waitFor("in your country"); audit("sos lines")
    }

    @Test fun `normal text`() = tour()

    @Test @Config(fontScale = 2.0f) fun `200 percent text`() = tour()
}
