// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** While a track is active, today's Shelter step comes first, in linen (S05 "Shelter active", D-031). */
@Composable
fun ShelterTodayCard(card: ShelterCard, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(Tokens.Linen, RoundedCornerShape(4.dp)).border(1.dp, Tokens.PencilSoft.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onOpen).padding(18.dp),
    ) {
        Text("${card.title.uppercase()} · DAY ${card.day}", style = StarfolkType.Label.copy(color = Tokens.Sage))
        Text(
            stringResource(if (card.doneToday) R.string.today_shelter_done else R.string.today_shelter_waiting),
            style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil, fontSize = StarfolkType.H2.fontSize), modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** US-016: offered after a heavy stretch. Never switches mode; "Not now" snoozes it for 7 app-days. */
@Composable
fun LowMoodCard(onShelter: () -> Unit, onSos: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Tokens.Linen, RoundedCornerShape(4.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.lowmood_title), style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil, fontSize = StarfolkType.H2.fontSize))
        PaperButton(stringResource(R.string.lowmood_shelter), onShelter, Modifier.fillMaxWidth(), filled = true)
        PaperButton(stringResource(R.string.sos_note_a11y), onSos, Modifier.fillMaxWidth())
        Text(stringResource(R.string.not_now), style = StarfolkType.Hand.copy(color = Tokens.Ink700), modifier = Modifier.clickable(role = Role.Button, onClick = onNotNow).padding(vertical = 10.dp))
    }
}

/** US-019: the optional 1-minute check, at baseline and again after 14 days. */
@Composable
fun Who5Card(prompt: Who5Prompt, onTake: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Tokens.Paper50, RoundedCornerShape(6.dp)).border(1.5.dp, Tokens.Paper300, RoundedCornerShape(6.dp)).padding(18.dp)) {
        Text(stringResource(if (prompt == Who5Prompt.BASELINE) R.string.who5_offer else R.string.who5_recheck_offer), style = StarfolkType.H2.copy(color = Tokens.Ink900))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton(stringResource(R.string.who5_take), onTake, Modifier.weight(1f), filled = true)
            PaperButton(stringResource(R.string.not_now), onDismiss, Modifier.weight(1f))
        }
    }
}
