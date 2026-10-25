// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.who5

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.safety.Who5
import io.github.ukpratik.starfolk.data.Who5Repository
import io.github.ukpratik.starfolk.data.personal.Who5Entity
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.components.StatusBarScrim
import androidx.compose.foundation.layout.Box
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * "How far you've come" (S16, US-019): the five WHO-5 items, one at a time, then a neutral, non-clinical result. A low
 * score (≤ 28%) gently points to people who can help (US-016). Answers stay in private.db.
 */
@Composable
fun Who5Screen(container: AppContainer, onClose: () -> Unit, onSos: () -> Unit, onShelter: () -> Unit) {
    val history by container.who5.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val answers = remember { mutableStateListOf<Int>() }
    var step by remember { mutableIntStateOf(-1) }
    var result by remember { mutableStateOf<Who5Entity?>(null) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("× " + stringResource(R.string.close), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClose).padding(vertical = 12.dp))
        val r = result
        when {
            r != null -> Result(r, history.firstOrNull { it.kind == Who5Repository.BASELINE && it.takenAt != r.takenAt }, onClose, onSos, onShelter)
            step < 0 -> {
                Text(stringResource(R.string.who5_title), style = StarfolkType.H1.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.who5_intro), style = StarfolkType.BodyL.copy(color = Tokens.Ink700))
                PaperButton(stringResource(R.string.who5_start), { step = 0 }, Modifier.fillMaxWidth(), filled = true)
                PaperButton(stringResource(R.string.skip), onClose, Modifier.fillMaxWidth())
                Text(Who5.ATTRIBUTION, style = StarfolkType.Body.copy(color = Tokens.Ink500))
            }
            else -> {
                Text("${step + 1} / ${Who5.ITEMS.size} · ${Who5.PROMPT}", style = StarfolkType.Label.copy(color = Tokens.Ink700))
                Text(Who5.ITEMS[step], style = StarfolkType.H1.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (points in 5 downTo 0) {
                        Text(
                            Who5.SCALE[points], style = StarfolkType.Body.copy(color = Tokens.Ink900),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Tokens.Paper50, RoundedCornerShape(8.dp))
                                .border(1.5.dp, Tokens.Paper300, RoundedCornerShape(8.dp))
                                .selectable(false, role = Role.RadioButton) {
                                    answers.add(points)
                                    if (step < Who5.ITEMS.lastIndex) step++ else scope.launch { result = container.who5.save(answers.toList()) }
                                }.padding(16.dp),
                        )
                    }
                }
                Text(Who5.ATTRIBUTION, style = StarfolkType.Body.copy(color = Tokens.Ink500))
            }
        }
    }
    StatusBarScrim(Tokens.Paper100)
    SosNote(onSos, Modifier.align(androidx.compose.ui.Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
}

@Composable
private fun Result(r: Who5Entity, baseline: Who5Entity?, onClose: () -> Unit, onSos: () -> Unit, onShelter: () -> Unit) {
    Text("${r.percent} / 100", style = StarfolkType.Display.copy(color = Tokens.Ink900))
    if (baseline != null) {
        val delta = r.percent - baseline.percent
        Text(stringResource(R.string.who5_delta, if (delta >= 0) "+$delta" else "$delta"), style = StarfolkType.HandL.copy(color = Tokens.HandwritingInk))
    } else {
        Text(stringResource(R.string.who5_baseline_done), style = StarfolkType.BodyL.copy(color = Tokens.Ink700))
    }
    Text(stringResource(R.string.who5_neutral), style = StarfolkType.Body.copy(color = Tokens.Ink700))
    if (Who5.Score(r.percent / 4, r.percent).supportCopy) {
        Column(Modifier.fillMaxWidth().background(Tokens.Linen, RoundedCornerShape(4.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.who5_low), style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil, fontSize = StarfolkType.H2.fontSize))
            PaperButton(stringResource(R.string.sos_lines), onSos, Modifier.fillMaxWidth(), filled = true)
            PaperButton(stringResource(R.string.lowmood_shelter), onShelter, Modifier.fillMaxWidth())
        }
    }
    PaperButton(stringResource(R.string.back_to_today), onClose, Modifier.fillMaxWidth())
}
