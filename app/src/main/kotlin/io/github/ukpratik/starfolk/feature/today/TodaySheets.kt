// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Insight
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.data.QuestRepository
import io.github.ukpratik.starfolk.ui.components.MoodPicker
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import io.github.ukpratik.starfolk.ui.theme.colors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A commit slot and when to remind (null = no reminder). Reminders themselves are scheduled from M-D (US-010). */
data class CommitChoice(val slot: String, val remindAt: Long?)

/** "When?" after hold-to-tear (S06, US-005). Times in the past fall back to "now". */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WhenSheet(onPick: (CommitChoice) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Paper100) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.when_title), style = StarfolkType.HandL.copy(color = Tokens.Ink900))
            Text(stringResource(R.string.when_body), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.when_now)) { onPick(CommitChoice("now", null)) }
                Chip(stringResource(R.string.when_lunch)) { onPick(at("lunch", LocalTime.of(12, 30))) }
                Chip(stringResource(R.string.when_evening)) { onPick(at("evening", LocalTime.of(19, 0))) }
                Chip(stringResource(R.string.when_pick)) { custom = true }
            }
        }
    }
    if (custom) {
        val state = rememberTimePickerState(initialHour = 18, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { custom = false },
            confirmButton = { TextButton({ custom = false; onPick(at("custom", LocalTime.of(state.hour, state.minute))) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton({ custom = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
            containerColor = Tokens.Paper100,
        )
    }
}

private fun at(slot: String, time: LocalTime): CommitChoice {
    val zone = ZoneId.systemDefault()
    val instant = LocalDate.now(zone).atTime(time).atZone(zone).toInstant()
    return if (instant.toEpochMilli() <= System.currentTimeMillis()) CommitChoice("now", null) else CommitChoice(slot, instant.toEpochMilli())
}

/** How a commit reads in handwriting on the ticket ("tonight 7 pm"). */
fun commitLabel(slot: String, remindAt: Long?): String {
    if (remindAt == null) return "doing it now"
    val t = java.time.Instant.ofEpochMilli(remindAt).atZone(ZoneId.systemDefault()).toLocalTime()
    val clock = t.format(DateTimeFormatter.ofPattern(if (t.minute == 0) "h a" else "h:mm a", Locale.ENGLISH)).lowercase()
    return when (slot) { "lunch" -> "at lunch"; "evening" -> "tonight $clock"; else -> "at $clock" }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = StarfolkType.Button.copy(color = Tokens.Ink900),
        modifier = Modifier.heightIn(min = 48.dp).border(2.dp, Tokens.Ink900, RoundedCornerShape(24.dp)).background(Tokens.Paper50, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

/** Reflection after Done (S07, US-006/008): mood, who it was with (names a star), one line. Private by design. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionSheet(quest: Quest, onSave: (mood: Int?, note: String?, name: String?) -> Unit, onSkip: () -> Unit) {
    var mood by rememberSaveable { mutableStateOf<Int?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onSkip, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Paper100) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("${quest.realm.name} · DONE", style = StarfolkType.Label.copy(color = quest.realm.colors().ink))
            Text(stringResource(R.string.reflect_title), style = StarfolkType.HandL.copy(color = Tokens.Ink900), modifier = Modifier.rotate(-1.5f).padding(top = 4.dp, bottom = 8.dp))
            MoodPicker(mood, onSelect = { mood = it })
            if (quest.namesStar) HandLine(stringResource(R.string.reflect_who), name, { if (it.length <= QuestRepository.MAX_NAME) name = it }, stringResource(R.string.reflect_who_hint), KeyboardCapitalization.Words)
            HandLine(stringResource(R.string.reflect_line), note, { if (it.length <= QuestRepository.MAX_NOTE) note = it }, stringResource(R.string.reflect_line_hint), KeyboardCapitalization.Sentences)
            Text(stringResource(R.string.reflect_private), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton(stringResource(R.string.skip), onClick = { onSave(null, null, null) }, modifier = Modifier.width(96.dp))
                PaperButton(
                    stringResource(if (quest.realm == Realm.CONNECT) R.string.reflect_save_star else R.string.reflect_save),
                    onClick = { onSave(mood, note, name) }, modifier = Modifier.weight(1f), filled = true,
                )
            }
        }
    }
}

@Composable
private fun HandLine(label: String, value: String, onChange: (String) -> Unit, hint: String, caps: KeyboardCapitalization) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).border(width = 0.dp, color = Tokens.Paper100), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        Text(label, style = StarfolkType.Label.copy(color = Tokens.Ink700), modifier = Modifier.padding(bottom = 8.dp, end = 10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = StarfolkType.Hand.copy(color = Tokens.Ink500))
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = StarfolkType.Hand.copy(color = Tokens.HandwritingInk),
                cursorBrush = SolidColor(Tokens.HandwritingInk),
                keyboardOptions = KeyboardOptions(capitalization = caps),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.5.dp).background(Tokens.Paper300))
}

/** The taped index card after the stamp (S08, US-009): the psychology behind the quest, with its source. */
@Composable
fun WhyCard(insight: Insight, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().rotate(1f).background(Tokens.Paper50).border(1.dp, Tokens.Paper300).padding(18.dp),
    ) {
        Text(stringResource(R.string.why_label), style = StarfolkType.Tab.copy(color = Tokens.StampRed))
        Text(insight.title, style = StarfolkType.H2.copy(color = Tokens.Ink900), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        Text(insight.body, style = StarfolkType.Body.copy(color = Tokens.Ink700))
        Text("— ${insight.cite}", style = StarfolkType.Hand.copy(color = Tokens.HandwritingInk), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        PaperButton(stringResource(R.string.back_to_today), onClick = onClose, modifier = Modifier.fillMaxWidth(), filled = true)
    }
}
