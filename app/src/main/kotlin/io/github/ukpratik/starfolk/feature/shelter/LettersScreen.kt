// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.shelter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.components.StatusBarScrim
import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.data.personal.LetterEntity
import io.github.ukpratik.starfolk.ui.theme.Tokens
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import java.text.DateFormat
import java.util.Date

/** Unsent letters (S13, US-013): written, kept on this phone, never sendable, saved as you write. "Release" deletes one for good. */
@Composable
fun LettersScreen(container: AppContainer, track: TrackId, onBack: () -> Unit, onSos: () -> Unit) {
    val vm: LettersViewModel = viewModel { LettersViewModel(container, track, createSavedStateHandle()) }
    val letters by vm.letters.collectAsStateWithLifecycle(initialValue = emptyList())
    val writing by vm.writing.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var releasing by remember { mutableStateOf<LetterEntity?>(null) }
    val l = linen()
    BackHandler(enabled = writing) { vm.close(draft) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (writing) vm.flush(draft) }

    Box(Modifier.fillMaxSize().background(l.bg)) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("← back", style = StarfolkType.Body.copy(color = l.pencil), modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) {
            if (writing) vm.close(draft) else onBack()
        }.padding(vertical = 12.dp))
        if (writing) {
            Text(stringResource(R.string.letters_write_title), style = StarfolkType.ShelterTitle.copy(color = l.pencil), modifier = Modifier.semantics { heading() })
            Box(Modifier.fillMaxWidth().heightIn(min = 280.dp).background(l.card).border(1.dp, l.rule).padding(16.dp)) {
                BasicTextField(draft, { draft = it; vm.edited(it) }, textStyle = StarfolkType.BodyL.copy(color = l.pencil), cursorBrush = SolidColor(l.pencil),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).semantics { contentDescription = "Letter" })
            }
            Text(stringResource(R.string.letters_private), style = StarfolkType.Body.copy(color = l.soft))
            QuietButton(stringResource(R.string.letters_keep), l, Modifier.fillMaxWidth(), filled = true) { vm.close(draft) }
        } else {
            Text(stringResource(R.string.letters_title_full), style = StarfolkType.ShelterTitle.copy(color = l.pencil), modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.letters_explain), style = StarfolkType.Body.copy(color = l.pencil))
            QuietButton(stringResource(R.string.letters_new), l, Modifier.fillMaxWidth(), filled = true) { draft = ""; vm.open(null) }
            if (letters.isEmpty()) Text(stringResource(R.string.letters_empty), style = StarfolkType.Hand.copy(color = l.soft))
            for (letter in letters) {
                Column(Modifier.fillMaxWidth().background(l.card).border(1.dp, l.rule).clickable(role = Role.Button) {
                    draft = letter.body; vm.open(letter.id)
                }.padding(16.dp)) {
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(letter.createdAt)), style = StarfolkType.Label.copy(color = l.soft))
                    Text(letter.body.take(140) + if (letter.body.length > 140) "…" else "", style = StarfolkType.Body.copy(color = l.pencil), modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.letters_release), style = StarfolkType.Hand.copy(color = l.soft), modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { releasing = letter }.padding(top = 10.dp))
                    }
                }
            }
        }
    }
    StatusBarScrim(l.bg)
    io.github.ukpratik.starfolk.feature.sos.ShelterSosPill(onSos, Modifier.align(androidx.compose.ui.Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
    releasing?.let { letter ->
        AlertDialog(
            onDismissRequest = { releasing = null },
            title = { Text(stringResource(R.string.letters_release_title), style = StarfolkType.H2.copy(color = l.pencil)) },
            text = { Text(stringResource(R.string.letters_release_body), style = StarfolkType.Body.copy(color = l.pencil)) },
            confirmButton = { TextButton({ vm.release(letter.id); releasing = null }) { Text(stringResource(R.string.letters_release_yes), color = Tokens.Danger) } },
            dismissButton = { TextButton({ releasing = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = l.card,
        )
    }
}
