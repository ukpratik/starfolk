// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.shelter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.components.StatusBarScrim
import io.github.ukpratik.starfolk.core.content.Track
import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.core.shelter.ShelterProgress
import io.github.ukpratik.starfolk.core.shelter.SlipChoice
import io.github.ukpratik.starfolk.core.shelter.TrackState
import io.github.ukpratik.starfolk.core.shelter.TrackStatus
import io.github.ukpratik.starfolk.feature.sos.ShelterSosPill
import io.github.ukpratik.starfolk.ui.theme.LocalRegister
import io.github.ukpratik.starfolk.ui.theme.Register
import io.github.ukpratik.starfolk.ui.theme.StarfolkType

/**
 * The Shelter (S11–S14, US-011…017), in the quiet register: no stamps, no confetti, no streaks, no red. First entry
 * shows the wellbeing disclaimer (US-021); then the track picker, or today's step of the active track.
 */
@Composable
fun ShelterScreen(container: AppContainer, onSos: () -> Unit, onLetters: (TrackId) -> Unit, onBackToQuests: () -> Unit) {
    val vm: ShelterViewModel = viewModel { ShelterViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val graduated by vm.graduated.collectAsStateWithLifecycle()
    val l = linen()
    var picking by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { vm.refreshDay(); onPauseOrDispose { } }

    CompositionLocalProvider(LocalRegister provides Register.SHELTER) {
        Box(Modifier.fillMaxSize().background(l.bg)) {
            val s = state
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 26.dp, end = 26.dp, top = 64.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    s == null -> Unit
                    graduated != null -> Graduation(graduated!!.first, graduated!!.second, l) { vm.dismissGraduation(); onBackToQuests() }
                    !s.introSeen -> Intro(l, onContinue = vm::introSeen, onSos = onSos)
                    s.active == null || picking -> Picker(s, l, onPick = { vm.start(it); picking = false })
                    else -> Day(s, vm, l, onLetters = onLetters, onSwitch = { picking = true })
                }
            }
            StatusBarScrim(l.bg)
            ShelterSosPill(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
        }
    }
}

@Composable
private fun ColumnScope.Intro(l: Linen, onContinue: () -> Unit, onSos: () -> Unit) {
    Lantern(l, Modifier.align(Alignment.CenterHorizontally))
    Text(stringResource(R.string.shelter_title), style = StarfolkType.ShelterTitle.copy(color = l.pencil), modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.shelter_intro_body), style = StarfolkType.BodyL.copy(color = l.pencil))
    Text(stringResource(R.string.disclaimer), style = StarfolkType.Body.copy(color = l.soft), modifier = Modifier.border(1.dp, l.soft).padding(14.dp))
    QuietButton(stringResource(R.string.shelter_continue), l, Modifier.fillMaxWidth(), filled = true, onClick = onContinue)
    QuietButton(stringResource(R.string.sos_note_a11y), l, Modifier.fillMaxWidth(), onClick = onSos)
}

@Composable
private fun ColumnScope.Picker(s: ShelterUiState, l: Linen, onPick: (TrackId) -> Unit) {
    var confirm by remember { mutableStateOf<TrackId?>(null) }
    Text(stringResource(R.string.shelter_title), style = StarfolkType.ShelterTitle.copy(color = l.pencil), modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.shelter_body), style = StarfolkType.BodyL.copy(color = l.pencil))
    for (track in s.tracks) {
        val st = s.stateOf(track.track)
        Column(
            Modifier.fillMaxWidth().background(l.card, RoundedCornerShape(4.dp)).border(1.dp, l.soft.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable(role = Role.Button) {
                    val active = s.active
                    if (active != null && active.track != track.track) confirm = track.track else onPick(track.track)
                }.padding(18.dp),
        ) {
            Text("${track.title.uppercase()} · ${track.length} DAYS", style = StarfolkType.Label.copy(color = l.sage))
            Text(track.summary, style = StarfolkType.Body.copy(color = l.pencil), modifier = Modifier.padding(top = 6.dp))
            if (st != null && st.daysWalked > 0) Text(
                pluralStringResource(if (st.status == TrackStatus.DONE) R.plurals.shelter_walked_done else R.plurals.shelter_walked, st.daysWalked, st.daysWalked),
                style = StarfolkType.Hand.copy(color = l.soft), modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    confirm?.let { next ->
        val current = s.activeTrack!!
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.shelter_switch_title, current.title, s.tracks.first { it.track == next }.title), style = StarfolkType.H2.copy(color = l.pencil)) },
            text = { Text(pluralStringResource(R.plurals.shelter_switch_body, s.active!!.daysWalked, s.active!!.daysWalked), style = StarfolkType.Body.copy(color = l.pencil)) },
            confirmButton = { TextButton({ onPick(next); confirm = null }) { Text(stringResource(R.string.shelter_switch_yes)) } },
            dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = l.card,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.Day(s: ShelterUiState, vm: ShelterViewModel, l: Linen, onLetters: (TrackId) -> Unit, onSwitch: () -> Unit) {
    val track = s.activeTrack ?: return
    val active = s.active ?: return
    val day = s.day
    var slipping by remember { mutableStateOf(false) }
    var journaling by remember { mutableStateOf(false) }

    Lantern(l, Modifier.align(Alignment.CenterHorizontally))
    Text("${track.title.uppercase()} · DAY ${s.dayNumber}", style = StarfolkType.Label.copy(color = l.soft), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text(
        if (s.doneToday) stringResource(R.string.shelter_done_today, active.currentDay) else day?.title ?: stringResource(R.string.shelter_missing_day),
        style = StarfolkType.ShelterTitle.copy(color = l.pencil, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Tally(active.daysWalked, l, Modifier.semantics { contentDescription = "${active.daysWalked} days walked" })
        Text("  " + pluralStringResource(R.plurals.shelter_days_walked, active.daysWalked, active.daysWalked), style = StarfolkType.Hand.copy(color = l.soft))
    }
    if (day != null && !s.doneToday) Text(day.insight, style = StarfolkType.BodyL.copy(color = l.pencil))
    if (day != null) ClipCard(l) {
        Text(stringResource(R.string.shelter_step), style = StarfolkType.Label.copy(color = l.sage))
        Text(day.step.title, style = StarfolkType.ShelterTitle.copy(color = l.pencil, fontSize = StarfolkType.H2.fontSize), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        Text(day.step.detail, style = StarfolkType.Body.copy(color = l.pencil))
        Spacer(Modifier.height(14.dp))
        if (s.doneToday) Text("✓ " + stringResource(R.string.shelter_done_mark), style = StarfolkType.HandL.copy(color = l.sage))
        else QuietButton(stringResource(R.string.shelter_did_this), l, Modifier.fillMaxWidth()) { vm.markDone(track) }
    }
    if (day?.journalPrompt != null) Column(
        Modifier.fillMaxWidth().background(l.card).border(1.dp, l.rule).clickable(role = Role.Button) { journaling = true }.padding(18.dp),
    ) {
        Text(stringResource(R.string.shelter_write), style = StarfolkType.Label.copy(color = l.soft))
        Text(day.journalPrompt!!, style = StarfolkType.HandL.copy(color = l.pencil), modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.shelter_private), style = StarfolkType.Body.copy(color = l.soft), modifier = Modifier.padding(top = 6.dp))
    }
    if (track.track == TrackId.BREAKUP) BreakupTools(active, s, vm, l, onLetters)
    Text(
        stringResource(R.string.shelter_slipped), style = StarfolkType.HandL.copy(color = l.pencil, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { slipping = true }.padding(top = 8.dp),
    )
    Text(
        stringResource(R.string.shelter_switch), style = StarfolkType.Body.copy(color = l.soft, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSwitch).padding(top = 12.dp),
    )
    Text(stringResource(R.string.disclaimer_short), style = StarfolkType.Body.copy(color = l.soft, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())

    if (slipping) AlertDialog(
        onDismissRequest = { slipping = false },
        title = { Text(stringResource(R.string.slip_title), style = StarfolkType.ShelterTitle.copy(color = l.pencil, fontSize = StarfolkType.H2.fontSize)) },
        text = { Text(pluralStringResource(R.plurals.slip_body, active.daysWalked, active.daysWalked), style = StarfolkType.Body.copy(color = l.pencil)) },
        confirmButton = { TextButton({ vm.slip(track.track, SlipChoice.KEEP); slipping = false }) { Text(stringResource(R.string.slip_keep)) } },
        dismissButton = { TextButton({ vm.slip(track.track, SlipChoice.RESTART); slipping = false }) { Text(stringResource(R.string.slip_restart)) } },
        containerColor = l.card,
    )
    if (journaling && day != null) JournalSheet(track, day.day, day.journalPrompt ?: "", vm, l) { journaling = false }
}

@Composable
private fun BreakupTools(active: TrackState, s: ShelterUiState, vm: ShelterViewModel, l: Linen, onLetters: (TrackId) -> Unit) {
    var reached by remember { mutableStateOf(false) }
    val days = ShelterProgress.noContactDays(active, s.today)
    Column(Modifier.fillMaxWidth().border(1.dp, l.rule).padding(16.dp)) {
        if (days == null) {
            Text(stringResource(R.string.nc_off), style = StarfolkType.Body.copy(color = l.pencil))
            QuietButton(stringResource(R.string.nc_start), l, Modifier.fillMaxWidth().padding(top = 10.dp)) { vm.setNoContact(TrackId.BREAKUP, true) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$days", style = StarfolkType.Display.copy(color = l.sage))
                Text("  " + stringResource(R.string.nc_days), style = StarfolkType.Body.copy(color = l.pencil))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                QuietButton(stringResource(R.string.nc_reached), l, Modifier.weight(1f)) { reached = true }
                QuietButton(stringResource(R.string.nc_stop), l, Modifier.weight(1f)) { vm.setNoContact(TrackId.BREAKUP, false) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(l.card).border(1.dp, l.rule).clickable(role = Role.Button) { onLetters(TrackId.BREAKUP) }.padding(16.dp)) {
        Text(stringResource(R.string.letters_title), style = StarfolkType.Label.copy(color = l.soft))
        Text(stringResource(R.string.letters_entry), style = StarfolkType.HandL.copy(color = l.pencil), modifier = Modifier.padding(top = 4.dp))
    }
    if (reached) AlertDialog(
        onDismissRequest = { reached = false },
        title = { Text(stringResource(R.string.nc_reached_title), style = StarfolkType.H2.copy(color = l.pencil)) },
        text = { Text(stringResource(R.string.nc_reached_body), style = StarfolkType.Body.copy(color = l.pencil)) },
        confirmButton = { TextButton({ vm.slip(TrackId.BREAKUP, SlipChoice.KEEP, reachedOut = true); reached = false }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton({ reached = false }) { Text(stringResource(R.string.cancel)) } },
        containerColor = l.card,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalSheet(track: Track, day: Int, prompt: String, vm: ShelterViewModel, l: Linen, onClose: () -> Unit) {
    val saved by vm.journal(track.track, day).collectAsStateWithLifecycle(initialValue = null)
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(saved) { if (text == null && saved != null) text = saved!!.body }
    ModalBottomSheet(onDismissRequest = { text?.let { vm.saveJournal(track.track, day, it) }; onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = l.card) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(prompt, style = StarfolkType.HandL.copy(color = l.pencil))
            Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(top = 10.dp)) {
                BasicTextField(
                    text ?: "", { text = it }, textStyle = StarfolkType.BodyL.copy(color = l.pencil), cursorBrush = SolidColor(l.pencil),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).semantics { contentDescription = prompt },
                )
            }
            Text(stringResource(R.string.shelter_private), style = StarfolkType.Body.copy(color = l.soft))
            QuietButton(stringResource(R.string.save), l, Modifier.fillMaxWidth().padding(top = 12.dp), filled = true) { text?.let { vm.saveJournal(track.track, day, it) }; onClose() }
        }
    }
}

/** Graduation (S14, US-017): the one gentle celebration in the Shelter: days walked and a North Star. */
@Composable
private fun ColumnScope.Graduation(track: Track, state: TrackState, l: Linen, onBack: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text("★", style = StarfolkType.Display.copy(color = io.github.ukpratik.starfolk.ui.theme.Tokens.Foil, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.grad_title, track.title), style = StarfolkType.ShelterTitle.copy(color = l.pencil, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().semantics { heading() })
    Text(pluralStringResource(R.plurals.grad_body, state.daysWalked, state.daysWalked), style = StarfolkType.BodyL.copy(color = l.pencil, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
    QuietButton(stringResource(R.string.grad_back), l, Modifier.fillMaxWidth(), filled = true, onClick = onBack)
}
