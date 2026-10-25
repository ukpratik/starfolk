// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.sky

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.core.sky.Box as SkyBox
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.sky.SkyCanvas

/** The Sky tab (S05b): the full collage. Tapping it opens Memories (D-026; per-star taps are B-005). */
@Composable
fun SkyScreen(container: AppContainer, skyDemo: Int, onSos: () -> Unit) {
    val today = remember { container.appDayClock.today() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value; val h = maxHeight.value
        val model = remember(w, h, skyDemo) {
            skyModel(DEMO_SEED, demoCompletions(skyDemo, today), today, w, h - 90f, keepClear = listOf(SkyBox(0f, 0f, w, 48f), SkyBox(w - 160f, 0f, 160f, 130f)))
        }
        Box(Modifier.fillMaxSize()) {
            SkyCanvas(model, SkyLayout.describe(model, null), DEMO_SEED, Modifier.fillMaxSize().testTag("sky"), tornBottom = false)
            SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
        }
    }
}
