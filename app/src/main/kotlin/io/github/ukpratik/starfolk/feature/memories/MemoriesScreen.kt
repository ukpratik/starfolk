// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.ui.components.MoodFaceIcon
import io.github.ukpratik.starfolk.ui.components.MoodLabels
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import io.github.ukpratik.starfolk.ui.theme.colors
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One remembered quest: content from the pack, feelings and names from private.db (US-008). */
data class Memory(val completionId: Long, val day: AppDay, val realm: Realm, val title: String, val mood: Int?, val note: String?, val name: String?)

class MemoriesViewModel(c: AppContainer) : ViewModel() {
    val memories = combine(c.quests.completions(), c.quests.checkins(), c.quests.names(), flow { emit(c.content.bundle()) }) { done, checkins, names, b ->
        val titles = b.quests.associate { it.id to it.title }
        val moods = checkins.associateBy { it.completionId }
        val nameById = names.associate { it.completionId to it.name }
        done.sortedByDescending { it.id }.map {
            Memory(it.id, AppDay(it.appDay), Realm.valueOf(it.realm), titles[it.questId] ?: "A quest", moods[it.id]?.mood, moods[it.id]?.note, nameById[it.id])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Memories (S09): newest first, grouped by month; tap for the memory card. Nothing here ever leaves the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(container: AppContainer, onSos: () -> Unit, onConnectAgain: () -> Unit) {
    val vm: MemoriesViewModel = viewModel { MemoriesViewModel(container) }
    val memories by vm.memories.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Memory?>(null) }
    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        val list = memories
        if (list != null && list.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(horizontal = 32.dp)) {
                Text(stringResource(R.string.memories_empty_title), style = StarfolkType.H1.copy(color = Tokens.Ink900))
                Text(stringResource(R.string.memories_empty_body), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 12.dp))
            }
        } else if (list != null) {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 84.dp, bottom = 140.dp)) {
                item { Text(stringResource(R.string.memories_title), style = StarfolkType.Display.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() }) }
                list.groupBy { it.day.date.withDayOfMonth(1) }.forEach { (month, items) ->
                    item { Text(month.format(MONTH).uppercase(), style = StarfolkType.Label.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
                    items(items, key = { it.completionId }) { m -> MemoryRow(m) { open = m } }
                }
            }
        }
        SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
    open?.let { m ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Paper100) {
            Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${m.realm.name} · ${m.day.date.format(DAY).uppercase()}", style = StarfolkType.Label.copy(color = m.realm.colors().ink))
                Text(m.title, style = StarfolkType.H2.copy(color = Tokens.Ink900))
                if (m.name != null) Text("with ${m.name}", style = StarfolkType.HandL.copy(color = Tokens.HandwritingInk))
                if (m.mood != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    MoodFaceIcon(m.mood); Text(" felt ${MoodLabels[m.mood - 1]}", style = StarfolkType.Hand.copy(color = Tokens.Ink700))
                }
                if (m.note != null) Text("“${m.note}”", style = StarfolkType.BodyL.copy(color = Tokens.Ink700))
                Spacer(Modifier.height(6.dp))
                if (m.realm == Realm.CONNECT) PaperButton(stringResource(R.string.memory_again), onClick = { open = null; onConnectAgain() }, modifier = Modifier.fillMaxWidth(), filled = true)
                PaperButton(stringResource(R.string.memory_close), onClick = { open = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MemoryRow(m: Memory, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 56.dp).background(Tokens.Paper50, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(m.realm.colors().fill, RoundedCornerShape(5.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(m.title, style = StarfolkType.Body.copy(color = Tokens.Ink900), maxLines = 2)
            Text(m.day.date.format(DAY) + (m.name?.let { " · with $it" } ?: ""), style = StarfolkType.Hand.copy(color = Tokens.Ink700))
        }
        if (m.mood != null) MoodFaceIcon(m.mood)
    }
}
