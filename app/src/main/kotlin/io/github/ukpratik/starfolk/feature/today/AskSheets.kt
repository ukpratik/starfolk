// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.today

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.metrics.Event
import io.github.ukpratik.starfolk.core.sky.CompletionLite
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.share.ShareCard
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** "Want a nudge tomorrow?" after the first check-in (US-010). Only a time choice leads to the permission request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NudgeSheet(onAnswer: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onAnswer(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Paper100) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.nudge_title), style = StarfolkType.HandL.copy(color = Tokens.Ink900))
            Text(stringResource(R.string.nudge_body), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton(stringResource(R.string.nudge_morning), { onAnswer("08:00") }, Modifier.weight(1f), filled = true)
                PaperButton(stringResource(R.string.nudge_evening), { onAnswer("19:00") }, Modifier.weight(1f), filled = true)
            }
            PaperButton(stringResource(R.string.nudge_no), { onAnswer(null) }, Modifier.fillMaxWidth())
        }
    }
}

/** Anonymous usage stats, off until the user says yes (US-020). Shows exactly what would be sent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentSheet(onAnswer: (Boolean) -> Unit) {
    var details by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { onAnswer(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Paper100) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.consent_title), style = StarfolkType.H2.copy(color = Tokens.Ink900))
            Text(stringResource(R.string.consent_body), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            Text(
                stringResource(if (details) R.string.consent_hide else R.string.consent_see),
                style = StarfolkType.Hand.copy(color = Tokens.HandwritingInk),
                modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { details = !details }.padding(top = 10.dp),
            )
            if (details) WhatsSent()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton(stringResource(R.string.consent_yes), { onAnswer(true) }, Modifier.weight(1f), filled = true)
                PaperButton(stringResource(R.string.consent_no), { onAnswer(false) }, Modifier.weight(1f))
            }
        }
    }
}

/** The full event list, from :core, so the UI can't drift from what's actually sent. */
@Composable
fun WhatsSent() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((name, what) in Event.DESCRIPTIONS) Text("• $name: $what", style = StarfolkType.Body.copy(color = Tokens.Ink700))
        Text(stringResource(R.string.consent_never), style = StarfolkType.Body.copy(color = Tokens.Ink900))
    }
}

/** Share a quest (S10, US-018): a preview of the card, a WhatsApp button when installed, and the system sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(quest: Quest, seed: Long, palette: Int, sky: List<CompletionLite>, today: AppDay, onShared: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri by produceState<android.net.Uri?>(null, quest.id) { value = ShareCard.render(context, quest, seed, palette, sky, today) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Paper100) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.share_title), style = StarfolkType.HandL.copy(color = Tokens.Ink900))
            uri?.let { u -> preview(context, u)?.let { Image(it, contentDescription = quest.title, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) } }
            Text(stringResource(R.string.share_note), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            val text = remember(quest.id) { ShareCard.text(context, quest) }
            if (ShareCard.whatsAppInstalled(context)) PaperButton(stringResource(R.string.share_whatsapp), {
                uri?.let { context.startActivity(ShareCard.intent(it, text, toWhatsApp = true)); onShared() }
            }, Modifier.fillMaxWidth(), filled = true)
            PaperButton(stringResource(R.string.share_more), {
                uri?.let { context.startActivity(ShareCard.intent(it, text, toWhatsApp = false)); onShared() }
            }, Modifier.fillMaxWidth())
        }
    }
}

private fun preview(context: Context, uri: android.net.Uri) =
    runCatching { context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
