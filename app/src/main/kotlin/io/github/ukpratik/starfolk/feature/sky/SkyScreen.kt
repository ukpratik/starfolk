// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.sky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.sky.Box as SkyBox
import io.github.ukpratik.starfolk.core.sky.CompletionLite
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.sky.SkyCanvas
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SkyUiState(val seed: Long, val palette: Int, val completions: List<CompletionLite>, val names: Map<Long, String>)

class SkyViewModel(c: AppContainer, skyDemo: Int) : ViewModel() {
    private val today = c.appDayClock.today()
    val state = combine(c.quests.completions(), c.quests.names(), c.settings.settings) { done, names, s ->
        val real = done.map { CompletionLite(it.id, Realm.valueOf(it.realm), AppDay(it.appDay), it.stamp) }
        SkyUiState(s.skySeed, s.palette, if (skyDemo > 0) demoCompletions(skyDemo, today) else real, names.associate { it.completionId to it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The Sky tab (S05b): the full collage. Tapping it, or the TalkBack action, opens Memories (D-026; star taps are B-005). */
@Composable
fun SkyScreen(container: AppContainer, skyDemo: Int, onSos: () -> Unit, onOpenMemories: () -> Unit) {
    val vm: SkyViewModel = viewModel { SkyViewModel(container, skyDemo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { container.appDayClock.today() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value; val h = maxHeight.value
        val s = state ?: return@BoxWithConstraints
        val model = remember(w, h, s) {
            skyModel(s.seed, s.completions, today, w, h - 90f, keepClear = listOf(SkyBox(0f, 0f, w, 48f), SkyBox(w - 160f, 0f, 160f, 130f)))
        }
        val description = SkyLayout.describe(model, s.completions.lastOrNull()?.id?.let { s.names[it] })
        Box(Modifier.fillMaxSize()) {
            SkyCanvas(
                model, description, s.seed,
                Modifier.fillMaxSize().testTag("sky").clickable(onClick = onOpenMemories)
                    .semantics { customActions = listOf(CustomAccessibilityAction("Open memories") { onOpenMemories(); true }) },
                tornBottom = false, palette = s.palette, names = s.names,
            )
            SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
        }
    }
}
