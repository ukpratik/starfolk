// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The D-012 sky spike (NFR-002): a dense sky (150 demo completions → 40 stars, clusters, 12 stamps, flame) on Today and
 * the Sky tab, scrolled and switched for about 10 s. Targets: p50 ≤ 16 ms / p95 ≤ 25 ms (mid-range), p50 ≤ 22 ms /
 * p95 ≤ 33 ms (low-end), < 5% of frames over budget. Run on the D-022 phone:
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.ukpratik.starfolk.baselineprofile.SkyFrameBenchmark`.
 */
@RunWith(AndroidJUnit4::class)
class SkyFrameBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun denseSky() = run(150)

    /** Baseline: the same scenario with a day-0 sky, so the sky's own cost is denseSky minus this. */
    @Test fun emptySky() = run(0)

    private fun run(demo: Int) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        // No startupMode: the app must stay alive from setup into the measured block (frames, not startup).
        iterations = 5,
        setupBlock = {
            pressHome()
            killProcess()
            startActivityAndWait(Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity").putExtra("sky_demo", demo))
        },
    ) {
        device.wait(Until.hasObject(By.text("TODAY")), TIMEOUT_MS)
        repeat(3) {
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 1f)
            device.findObject(By.scrollable(true))?.scroll(Direction.UP, 1f)
            device.findObject(By.text("SKY"))?.click()
            device.waitForIdle()
            device.findObject(By.text("TODAY"))?.click()
            device.waitForIdle()
        }
    }
}
